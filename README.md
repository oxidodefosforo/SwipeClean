# SwipeClean

Revisa las fotos de tu galería deslizando como en Tinder y libera espacio.
Todo ocurre en el teléfono: sin internet, sin copias, sin modificar los originales.

| Gesto / botón | Acción |
|---|---|
| ➡️ derecha / **KEEP** | Conservar (no se toca la foto) |
| ⬅️ izquierda / **DELETE** | Añadir a la cola de eliminación (todavía no se borra) |
| ⬆️ arriba / **SKIP** | Saltar; vuelve al final de la cola una vez y luego queda para otra sesión |
| **UNDO** | Deshace la última decisión (la tarjeta vuelve animada) |

---

## 1. Compilar con Android Studio (recomendado)

1. Instala **Android Studio** (Ladybug 2024.2 o posterior).
2. `File → Open` y elige la carpeta `SwipeClean` (la que contiene `settings.gradle.kts`).
3. Espera a que termine el *Gradle Sync* (descarga Gradle 8.11.1, AGP 8.7.3 y dependencias la primera vez).
   Si Android Studio ofrece actualizar AGP, puedes aceptarlo.
4. Para generar el APK: `Build → Build App Bundle(s) / APK(s) → Build APK(s)`.
   - Debug: `app/build/outputs/apk/debug/app-debug.apk`
   - O por terminal: `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
     (optimizado con R8 y firmado con la clave *debug*, listo para instalar en tu propio teléfono).

### Compilación automática (GitHub Actions)
Cada `push` a `main` compila el APK y lo publica en **Releases** con versión `1.0.<n.º de compilación>`:
https://github.com/oxidodefosforo/SwipeClean/releases/latest

### Actualizar la app
Todas las versiones se firman con la misma clave (`signing/`), así que el APK nuevo se instala
**encima** del anterior y se conserva el progreso. Dentro de la app: **Buscar actualizaciones**
(al final de la pantalla inicial) abre la página de Releases. La app no tiene permiso de Internet,
por eso la descarga la hace el navegador.

> La clave está dentro del repositorio porque es privado y de uso personal. Si lo haces público,
> mueve `signing/` a GitHub Secrets antes. Si pierdes la clave, tendrás que desinstalar para actualizar.

## 2. Instalar en el Samsung Galaxy

**Opción A — cable USB (desde Android Studio)**
1. En el teléfono: `Ajustes → Acerca del teléfono → Información de software` y toca 7 veces
   **Número de compilación** para activar las opciones de desarrollador.
2. `Ajustes → Opciones de desarrollador → Depuración USB` activada.
3. Conecta el cable, acepta la huella RSA y pulsa ▶ **Run** en Android Studio.

**Opción B — archivo APK**
1. Copia el `.apk` al teléfono (cable, Drive, correo…).
2. Ábrelo desde **Mis Archivos**. One UI pedirá permitir *Instalar apps desconocidas* para esa app: acéptalo.
3. Si aparece **Auto Blocker** (One UI 6+), desactívalo temporalmente en
   `Ajustes → Seguridad y privacidad → Auto Blocker`, instala, y vuelve a activarlo.
4. Si Play Protect avisa de una app desconocida, elige *Instalar de todos modos*.

Requisito: Android 11 o superior (Galaxy S20 en adelante, A-series recientes, etc.).

---

## 3. Cómo funciona la eliminación (y por qué es segura)

1. Deslizar a la izquierda **solo** añade la foto a una cola local. Puedes seguir sin interrupciones.
2. En **Resumen / Revisar eliminación** ves las miniaturas, el espacio estimado y puedes
   deseleccionar fotos (pasan a "conservadas").
3. Al pulsar **Enviar a la papelera**, la app comprueba que cada archivo sigue existiendo y llama a
   `MediaStore.createTrashRequest()`: **Android muestra su propio diálogo** de confirmación
   (uno por cada lote de hasta 500 fotos).
4. Tras aceptar, la app **vuelve a consultar MediaStore** y solo marca como eliminadas las fotos
   cuyo estado `IS_TRASHED` se confirmó. El mensaje de éxito llega después de esa verificación.
5. Puedes **Restaurar** desde el aviso que aparece justo después o desde **Papelera de SwipeClean**.
6. El borrado permanente solo ocurre si lo pides en la papelera, con un diálogo de la app **y** otro de Android.

## 4. Limitaciones de Android y alternativa usada

| Limitación | Qué hace la app |
|---|---|
| Ninguna app puede borrar fotos de otras apps sin confirmación del usuario (Android 11+). | Cola + un único diálogo oficial por lote, en vez de uno por foto. |
| La papelera del sistema (MediaStore) conserva las fotos unos **30 días** y luego Android las borra; mientras tanto **siguen ocupando espacio**. | El resumen muestra el espacio *que se liberará*. Para liberarlo ya: Papelera de SwipeClean → *Eliminar definitivamente*. |
| La **Papelera de la Galería de Samsung** es propia de Samsung y puede no mostrar fotos enviadas a la papelera estándar de Android. | La app guarda el registro y ofrece su propia pantalla de Papelera para restaurar o eliminar. |
| En la papelera, algunas miniaturas pueden no cargarse (Android restringe la lectura de elementos en papelera). | Se muestran como cuadros oscuros; restaurar y eliminar siguen funcionando. |
| **Acceso limitado** (Android 14+, "Seleccionar fotos"): la app solo ve las fotos elegidas. | Banner en la pantalla inicial para ampliar la selección o permitir todas. |
| Fotos solo en la nube (Google Fotos, OneDrive) no están en el teléfono. | No aparecen; la app trabaja solo con archivos locales. |

## 5. Permisos

- `READ_MEDIA_IMAGES` (Android 13+) — solo imágenes, nada de vídeo ni audio.
- `READ_MEDIA_VISUAL_USER_SELECTED` (Android 14+) — para el modo de acceso limitado.
- `READ_EXTERNAL_STORAGE` solo en Android 11–12.
- **Sin permiso de Internet**: es técnicamente imposible que la app suba nada.

## 6. Persistencia

Base SQLite local (`swipeclean.db`) con una fila por foto decidida (id de MediaStore, decisión,
tamaño, fecha) y `SharedPreferences` para filtros, semilla del orden aleatorio y sesión activa.
Al reabrir la app, continúa automáticamente la sesión: las fotos ya revisadas no vuelven a mostrarse
y el orden aleatorio se mantiene. *Reiniciar progreso* borra las decisiones (no toca ninguna foto).

## 7. Rendimiento (10.000+ fotos)

- Solo se leen metadatos ligeros de MediaStore (id, fecha, tamaño, álbum).
- Las imágenes se cargan con **Coil**, submuestreadas al tamaño de la tarjeta; se precargan las 4 siguientes.
- Las miniaturas del resumen usan `LazyVerticalGrid` y se piden a 320 px.
- Las escrituras en la base de datos van en un hilo de fondo, en orden.

## 8. Estructura

```
app/src/main/java/com/swipeclean/app/
├── MainActivity.kt          Navegación, lanzadores de permisos y diálogos del sistema
├── Permissions.kt           Acceso completo / limitado / ninguno
├── data/
│   ├── Models.kt            MediaItem, Decision, Filter…
│   ├── MediaRepository.kt   Consultas MediaStore, papelera, verificación
│   ├── DecisionStore.kt     SQLite con las decisiones
│   └── SettingsStore.kt     Filtros y sesión
└── ui/
    ├── MainViewModel.kt     Cola, UNDO, estadísticas, operaciones por lotes
    ├── components/          Tarjeta deslizable, botones, selectores
    ├── screens/             Permiso, Inicio, Swipe, Resumen, Papelera
    └── theme/               Tema oscuro
```
