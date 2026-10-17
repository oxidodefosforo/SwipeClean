package com.swipeclean.app.ui

import android.app.Application
import android.content.IntentSender
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swipeclean.app.Access
import com.swipeclean.app.data.Album
import com.swipeclean.app.data.Decision
import com.swipeclean.app.data.DecisionRecord
import com.swipeclean.app.data.DecisionStore
import com.swipeclean.app.data.Filter
import com.swipeclean.app.data.MediaItem
import com.swipeclean.app.data.MediaRepository
import com.swipeclean.app.data.SettingsStore
import com.swipeclean.app.data.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.random.Random

enum class Screen { PERMISSION, SETUP, SWIPE, REVIEW, TRASH }

data class Stats(val total: Int = 0, val reviewed: Int = 0, val kept: Int = 0, val toDelete: Int = 0)
data class Pending(val count: Int = 0, val bytes: Long = 0L)

class UiMessage(
    val text: String,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
)

enum class RequestKind { TRASH, RESTORE, DELETE_FOREVER }

/** Solicitud al sistema (diálogo oficial de Android) que la Activity debe lanzar. */
class SystemRequest(val kind: RequestKind, val ids: List<Long>, val intentSender: IntentSender)

private class HistoryEntry(
    val item: MediaItem,
    val decision: Decision,
    val previous: DecisionRecord?,
    val requeued: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MediaRepository(app)
    private val store = DecisionStore(app)
    private val settings = SettingsStore(app)

    /** Escrituras en BD en serie, fuera del hilo principal, para mantener el orden. */
    private val dbDispatcher = Dispatchers.IO.limitedParallelism(1)

    // ---------- Estado observable ----------
    var screen by mutableStateOf(Screen.PERMISSION); private set
    var access by mutableStateOf(Access.NONE); private set
    var loading by mutableStateOf(false); private set
    var albums by mutableStateOf<List<Album>>(emptyList()); private set
    var years by mutableStateOf<List<Int>>(emptyList()); private set
    var filter by mutableStateOf(settings.loadFilter()); private set
    var stats by mutableStateOf(Stats()); private set
    var pending by mutableStateOf(Pending()); private set
    var canUndo by mutableStateOf(false); private set

    /** Cambia con cada decisión/deshacer: la tarjeta superior se recrea aunque sea la misma foto. */
    var cardVersion by mutableIntStateOf(0); private set
    var busyMessage by mutableStateOf<String?>(null); private set
    var request by mutableStateOf<SystemRequest?>(null); private set
    var message by mutableStateOf<UiMessage?>(null); private set

    /** Cola de la sesión actual: el primer elemento es la foto visible. */
    val queue = mutableStateListOf<MediaItem>()

    /** Pantalla de revisión: fotos en cola de eliminación y las que el usuario deseleccionó. */
    var pendingItems by mutableStateOf<List<MediaItem>>(emptyList()); private set
    val deselected = mutableStateMapOf<Long, Boolean>()

    /** Pantalla de papelera. */
    var trashItems by mutableStateOf<List<MediaItem>>(emptyList()); private set
    val trashSelected = mutableStateMapOf<Long, Boolean>()

    /** Dirección desde la que vuelve la tarjeta al deshacer (para animarla). */
    var undoFrom: Decision? = null
        private set

    // ---------- Estado interno ----------
    private var allItems: List<MediaItem> = emptyList()
    private var byId: Map<Long, MediaItem> = emptyMap()
    private var filtered: List<MediaItem> = emptyList()
    private val decisions = HashMap<Long, DecisionRecord>()
    private val history = ArrayDeque<HistoryEntry>()
    private var decisionsLoaded = false
    private var firstLoadDone = false
    private var loadJob: Job? = null

    // Operación por lotes en curso (papelera / restaurar / eliminar definitivamente)
    private var opKind = RequestKind.TRASH
    private val opChunks = ArrayDeque<List<Long>>()
    private val opDone = mutableListOf<Long>()
    private var opFailed = 0
    private var inFlight: SystemRequest? = null

    // =====================================================================
    // Permisos y carga
    // =====================================================================

    fun onAccessChecked(newAccess: Access) {
        val changed = newAccess != access
        access = newAccess
        if (newAccess == Access.NONE) {
            screen = Screen.PERMISSION
            return
        }
        if (screen == Screen.PERMISSION) screen = Screen.SETUP
        // En acceso limitado el usuario puede cambiar su selección fuera de la app: recargar.
        if (changed || newAccess == Access.PARTIAL || !firstLoadDone) reload()
    }

    fun reload() {
        loadJob?.cancel()
        loading = true
        loadJob = viewModelScope.launch {
            try {
                val items = withContext(Dispatchers.IO) { repo.loadAll() }
                if (!decisionsLoaded) {
                    val recs = withContext(dbDispatcher) { store.loadAll() }
                    decisions.clear()
                    decisions.putAll(recs)
                    decisionsLoaded = true
                }
                reconcile(items)
                allItems = items
                byId = items.associateBy { it.id }
                albums = items.groupBy { it.bucketId }
                    .map { (id, list) -> Album(id, list.first().bucketName, list.size) }
                    .sortedBy { it.name.lowercase() }
                years = items.map { it.year }.distinct().sortedDescending()
                refreshFiltered()
                refreshPending()
                if (!firstLoadDone) {
                    firstLoadDone = true
                    // Continuar automáticamente donde se quedó.
                    if (settings.sessionActive && screen == Screen.SETUP) startSession()
                }
            } catch (e: SecurityException) {
                screen = Screen.PERMISSION
                access = Access.NONE
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                message = UiMessage("No se pudo leer la galería: ${e.message}")
            } finally {
                // Si esta carga fue reemplazada por otra, no tocar el indicador.
                if (isActive) loading = false
            }
        }
    }

    /** Sincroniza las decisiones guardadas con el estado real de la galería. */
    private fun reconcile(items: List<MediaItem>) {
        // Fotos marcadas como "en papelera" que vuelven a estar visibles: se restauraron fuera de la app.
        val restored = HashMap<Long, DecisionRecord>()
        for (it in items) {
            val r = decisions[it.id]
            if (r?.decision == Decision.TRASHED) restored[it.id] = r.copy(decision = Decision.KEEP)
        }
        if (restored.isNotEmpty()) {
            decisions.putAll(restored)
            viewModelScope.launch(dbDispatcher) { runCatching { store.putAll(restored) } }
        }
        // Con acceso completo: limpiar decisiones de archivos que ya no existen.
        if (access == Access.FULL) {
            val present = items.mapTo(HashSet(items.size)) { it.id }
            val gone = decisions.filter { (id, r) -> r.decision != Decision.TRASHED && id !in present }.keys
            if (gone.isNotEmpty()) removeDecisions(gone.toList())
        }
    }

    // =====================================================================
    // Filtros y estadísticas
    // =====================================================================

    fun updateFilter(f: Filter) {
        val normalized = if (f.year == null) f.copy(month = null) else f
        filter = normalized
        settings.saveFilter(normalized)
        refreshFiltered()
    }

    private fun refreshFiltered() {
        val f = filter
        val list = allItems.filter { item ->
            (f.albumId == null || item.bucketId == f.albumId) &&
                (f.year == null || item.year == f.year) &&
                (f.month == null || item.month == f.month)
        }
        filtered = when (f.order) {
            SortOrder.NEWEST -> list.sortedByDescending { it.dateMillis }
            SortOrder.OLDEST -> list.sortedBy { it.dateMillis }
            SortOrder.RANDOM -> list.sortedBy { it.id }.shuffled(Random(settings.randomSeed))
        }
        recomputeStats()
    }

    private fun recomputeStats() {
        var reviewed = 0
        var kept = 0
        var toDelete = 0
        for (item in filtered) {
            when (decisions[item.id]?.decision) {
                Decision.KEEP -> { reviewed++; kept++ }
                Decision.DELETE -> { reviewed++; toDelete++ }
                Decision.TRASHED -> reviewed++
                Decision.SKIP, null -> Unit
            }
        }
        stats = Stats(filtered.size, reviewed, kept, toDelete)
    }

    private fun refreshPending() {
        val items = decisions.entries
            .filter { it.value.decision == Decision.DELETE }
            .map { (id, rec) -> byId[id] ?: itemFromRecord(id, rec) }
            .sortedByDescending { it.dateMillis }
        pendingItems = items
        pending = Pending(items.size, items.sumOf { it.sizeBytes })
    }

    private fun itemFromRecord(id: Long, rec: DecisionRecord): MediaItem {
        val cal = Calendar.getInstance().apply { timeInMillis = rec.dateMillis }
        return MediaItem(
            id = id,
            uri = repo.uriOf(id),
            dateMillis = rec.dateMillis,
            sizeBytes = rec.sizeBytes,
            bucketId = "",
            bucketName = "",
            year = cal.get(Calendar.YEAR),
            month = cal.get(Calendar.MONTH) + 1,
        )
    }

    // =====================================================================
    // Sesión de revisión
    // =====================================================================

    fun startSession() {
        val unreviewed = filtered.filter { decisions[it.id] == null }
        val skipped = filtered.filter { decisions[it.id]?.decision == Decision.SKIP }
        queue.clear()
        queue.addAll(unreviewed)
        queue.addAll(skipped)
        cardVersion++
        history.clear()
        canUndo = false
        if (queue.isEmpty()) {
            settings.sessionActive = false
            message = UiMessage("No quedan fotos por revisar con este filtro.")
            screen = Screen.SETUP
        } else {
            settings.sessionActive = true
            screen = Screen.SWIPE
        }
    }

    fun decide(decision: Decision) {
        val item = queue.firstOrNull() ?: return
        queue.removeAt(0)
        val previous = decisions[item.id]
        // SKIP: la foto vuelve al final de la cola una vez; si se salta de nuevo, queda para otra sesión.
        val requeue = decision == Decision.SKIP && previous == null
        if (requeue) queue.add(item)

        val record = DecisionRecord(decision, item.sizeBytes, item.dateMillis)
        decisions[item.id] = record
        viewModelScope.launch(dbDispatcher) { runCatching { store.put(item.id, record) } }

        cardVersion++
        history.addLast(HistoryEntry(item, decision, previous, requeue))
        if (history.size > 500) history.removeFirst()
        canUndo = true

        recomputeStats()
        if (decision == Decision.DELETE || previous?.decision == Decision.DELETE) refreshPending()
        if (queue.isEmpty()) finishSession()
    }

    fun undo() {
        val entry = history.removeLastOrNull() ?: return
        val id = entry.item.id
        if (entry.requeued) {
            val idx = queue.indexOfLast { it.id == id }
            if (idx >= 0) queue.removeAt(idx)
        }
        val prev = entry.previous
        if (prev == null) {
            decisions.remove(id)
            viewModelScope.launch(dbDispatcher) { runCatching { store.remove(listOf(id)) } }
        } else {
            decisions[id] = prev
            viewModelScope.launch(dbDispatcher) { runCatching { store.put(id, prev) } }
        }
        undoFrom = entry.decision
        queue.add(0, entry.item)
        cardVersion++
        canUndo = history.isNotEmpty()
        settings.sessionActive = true
        screen = Screen.SWIPE
        recomputeStats()
        refreshPending()
    }

    fun takeUndoFrom(): Decision? = undoFrom.also { undoFrom = null }

    private fun finishSession() {
        settings.sessionActive = false
        openReview()
    }

    fun backToSetup() {
        screen = Screen.SETUP
    }

    fun newSession() {
        settings.sessionActive = false
        queue.clear()
        history.clear()
        canUndo = false
        screen = Screen.SETUP
    }

    fun resetProgress() {
        val keep = decisions.filterValues { it.decision == Decision.TRASHED }
        decisions.clear()
        decisions.putAll(keep)
        viewModelScope.launch(dbDispatcher) { runCatching { store.clearExceptTrashed() } }
        settings.newSeed()
        settings.sessionActive = false
        queue.clear()
        history.clear()
        canUndo = false
        refreshFiltered()
        refreshPending()
        message = UiMessage("Progreso reiniciado.")
    }

    // =====================================================================
    // Revisión y envío a la papelera
    // =====================================================================

    fun openReview() {
        deselected.clear()
        refreshPending()
        screen = Screen.REVIEW
    }

    fun closeReview() {
        screen = if (queue.isNotEmpty()) Screen.SWIPE else Screen.SETUP
    }

    fun toggleDeselect(id: Long) {
        if (deselected.containsKey(id)) deselected.remove(id) else deselected[id] = true
    }

    /** Cancelar: las fotos vuelven a quedar sin revisar. No se elimina nada. */
    fun cancelDeletion() {
        val ids = decisions.filterValues { it.decision == Decision.DELETE }.keys.toList()
        removeDecisions(ids)
        deselected.clear()
        refreshFiltered()
        refreshPending()
        message = UiMessage("Eliminación cancelada. ${plural(ids.size, "foto volverá", "fotos volverán")} a la cola de revisión.")
    }

    fun confirmTrash() {
        val selected = pendingItems.map { it.id }.filter { !deselected.containsKey(it) }
        // Las deseleccionadas pasan a "conservar".
        val keepNow = deselected.keys.toList()
        if (keepNow.isNotEmpty()) {
            val updates = HashMap<Long, DecisionRecord>()
            keepNow.forEach { id -> decisions[id]?.let { updates[id] = it.copy(decision = Decision.KEEP) } }
            decisions.putAll(updates)
            viewModelScope.launch(dbDispatcher) { runCatching { store.putAll(updates) } }
            deselected.clear()
        }
        refreshFiltered()
        refreshPending()
        if (selected.isEmpty()) return

        busyMessage = "Comprobando fotos…"
        viewModelScope.launch {
            val state = try {
                withContext(Dispatchers.IO) { repo.queryTrashState(selected) }
            } catch (e: Exception) {
                busyMessage = null
                message = UiMessage("No se pudo comprobar las fotos: ${e.message}")
                return@launch
            }
            // Archivos que ya no existen: se quitan de la cola sin más.
            val missing = selected.filter { it !in state }
            if (missing.isNotEmpty()) removeDecisions(missing)
            // Ya estaban en la papelera (p. ej. desde otra app): se registran como tal.
            val already = selected.filter { state[it] == true }
            markTrashed(already)
            val toTrash = selected.filter { state[it] == false }
            startOperation(RequestKind.TRASH, toTrash)
        }
    }

    // =====================================================================
    // Papelera de SwipeClean
    // =====================================================================

    fun openTrash() {
        trashSelected.clear()
        screen = Screen.TRASH
        refreshTrash()
    }

    fun closeTrash() {
        screen = Screen.SETUP
    }

    private fun refreshTrash() {
        val records = decisions.filterValues { it.decision == Decision.TRASHED }
        if (records.isEmpty()) {
            trashItems = emptyList()
            return
        }
        busyMessage = "Leyendo papelera…"
        viewModelScope.launch {
            try {
                val state = withContext(Dispatchers.IO) { repo.queryTrashState(records.keys) }
                // Ya no existen (la papelera se vació, normalmente a los 30 días).
                val purged = records.keys.filter { it !in state }
                if (purged.isNotEmpty()) removeDecisions(purged)
                // Restauradas fuera de la app.
                val restored = records.keys.filter { state[it] == false }
                if (restored.isNotEmpty()) {
                    markKept(restored)
                    reload()
                }
                trashItems = records
                    .filter { state[it.key] == true }
                    .map { (id, rec) -> itemFromRecord(id, rec) }
                    .sortedByDescending { it.dateMillis }
            } catch (e: Exception) {
                message = UiMessage("No se pudo leer la papelera: ${e.message}")
            } finally {
                busyMessage = null
            }
        }
    }

    fun toggleTrashSelection(id: Long) {
        if (trashSelected.containsKey(id)) trashSelected.remove(id) else trashSelected[id] = true
    }

    fun selectAllTrash(select: Boolean) {
        trashSelected.clear()
        if (select) trashItems.forEach { trashSelected[it.id] = true }
    }

    fun restoreSelected() {
        val ids = trashSelected.keys.toList()
        if (ids.isNotEmpty()) startOperation(RequestKind.RESTORE, ids)
    }

    fun restore(ids: List<Long>) {
        if (ids.isNotEmpty()) startOperation(RequestKind.RESTORE, ids)
    }

    fun deleteSelectedForever() {
        val ids = trashSelected.keys.toList()
        if (ids.isNotEmpty()) startOperation(RequestKind.DELETE_FOREVER, ids)
    }

    // =====================================================================
    // Operaciones por lotes con el diálogo oficial del sistema
    // =====================================================================

    private fun startOperation(kind: RequestKind, ids: List<Long>) {
        opKind = kind
        opChunks.clear()
        // Un diálogo del sistema por cada lote (evita límites de tamaño de la transacción).
        ids.chunked(CHUNK_SIZE).forEach { opChunks.addLast(it) }
        opDone.clear()
        opFailed = 0
        nextChunk()
    }

    private fun nextChunk() {
        val chunk = opChunks.removeFirstOrNull()
        if (chunk == null) {
            finishOperation(cancelled = false)
            return
        }
        busyMessage = "Esperando confirmación del sistema…"
        try {
            val sender = when (opKind) {
                RequestKind.TRASH -> repo.trashRequest(chunk, trash = true)
                RequestKind.RESTORE -> repo.trashRequest(chunk, trash = false)
                RequestKind.DELETE_FOREVER -> repo.deleteRequest(chunk)
            }
            val r = SystemRequest(opKind, chunk, sender)
            inFlight = r
            request = r
        } catch (e: Exception) {
            opFailed += chunk.size + opChunks.sumOf { it.size }
            opChunks.clear()
            finishOperation(cancelled = false, error = e.message)
        }
    }

    /** La Activity llama a esto justo al lanzar el diálogo, para no lanzarlo dos veces. */
    fun onRequestLaunched() {
        request = null
    }

    /** Resultado del diálogo del sistema. Nunca se informa éxito sin verificarlo en MediaStore. */
    fun onSystemResult(ok: Boolean) {
        val req = inFlight ?: return
        inFlight = null
        if (!ok) {
            opFailed += req.ids.size + opChunks.sumOf { it.size }
            opChunks.clear()
            finishOperation(cancelled = true)
            return
        }
        busyMessage = "Verificando…"
        viewModelScope.launch {
            val state = try {
                withContext(Dispatchers.IO) { repo.queryTrashState(req.ids) }
            } catch (e: Exception) {
                opFailed += req.ids.size
                nextChunk()
                return@launch
            }
            val confirmed = when (req.kind) {
                RequestKind.TRASH -> req.ids.filter { state[it] == true }
                RequestKind.RESTORE -> req.ids.filter { state[it] == false }
                RequestKind.DELETE_FOREVER -> req.ids.filter { it !in state }
            }
            when (req.kind) {
                RequestKind.TRASH -> markTrashed(confirmed)
                RequestKind.RESTORE -> markKept(confirmed)
                RequestKind.DELETE_FOREVER -> removeDecisions(confirmed)
            }
            opDone.addAll(confirmed)
            opFailed += req.ids.size - confirmed.size
            nextChunk()
        }
    }

    private fun finishOperation(cancelled: Boolean, error: String? = null) {
        busyMessage = null
        val done = opDone.toList()
        val failedNote = if (opFailed > 0 && !cancelled) " · ${opFailed} no se pudieron procesar" else ""
        when (opKind) {
            RequestKind.TRASH -> {
                if (done.isNotEmpty()) {
                    val gone = done.toHashSet()
                    allItems = allItems.filter { it.id !in gone }
                    byId = allItems.associateBy { it.id }
                }
                refreshFiltered()
                refreshPending()
                message = when {
                    done.isNotEmpty() -> UiMessage(
                        "${plural(done.size, "foto enviada", "fotos enviadas")} a la papelera$failedNote",
                        actionLabel = "Restaurar",
                        action = { restore(done) },
                    )
                    cancelled -> UiMessage("Cancelado. No se movió ninguna foto.")
                    else -> UiMessage("No se pudo mover ninguna foto${error?.let { ": $it" } ?: ""}")
                }
            }
            RequestKind.RESTORE -> {
                message = if (done.isNotEmpty()) {
                    UiMessage("${plural(done.size, "foto restaurada", "fotos restauradas")}$failedNote")
                } else {
                    UiMessage(if (cancelled) "Restauración cancelada." else "No se pudo restaurar${error?.let { ": $it" } ?: ""}")
                }
                trashSelected.clear()
                reload()
                if (screen == Screen.TRASH) refreshTrash()
            }
            RequestKind.DELETE_FOREVER -> {
                message = if (done.isNotEmpty()) {
                    UiMessage("${plural(done.size, "foto eliminada", "fotos eliminadas")} definitivamente$failedNote")
                } else {
                    UiMessage(if (cancelled) "Cancelado. No se eliminó nada." else "No se pudo eliminar${error?.let { ": $it" } ?: ""}")
                }
                trashSelected.clear()
                if (screen == Screen.TRASH) refreshTrash()
            }
        }
    }

    // =====================================================================
    // Utilidades de decisiones
    // =====================================================================

    private fun markTrashed(ids: List<Long>) = setDecision(ids, Decision.TRASHED)
    private fun markKept(ids: List<Long>) = setDecision(ids, Decision.KEEP)

    private fun setDecision(ids: List<Long>, decision: Decision) {
        if (ids.isEmpty()) return
        val updates = HashMap<Long, DecisionRecord>()
        ids.forEach { id ->
            val old = decisions[id]
            val item = byId[id]
            updates[id] = DecisionRecord(
                decision,
                old?.sizeBytes ?: item?.sizeBytes ?: 0L,
                old?.dateMillis ?: item?.dateMillis ?: 0L,
            )
        }
        decisions.putAll(updates)
        viewModelScope.launch(dbDispatcher) { runCatching { store.putAll(updates) } }
    }

    private fun removeDecisions(ids: List<Long>) {
        if (ids.isEmpty()) return
        ids.forEach { decisions.remove(it) }
        viewModelScope.launch(dbDispatcher) { runCatching { store.remove(ids) } }
    }

    fun messageShown(m: UiMessage) {
        if (message === m) message = null
    }

    private companion object {
        const val CHUNK_SIZE = 500
    }
}
