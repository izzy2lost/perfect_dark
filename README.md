# Perfect Dark — Android port with touch controls

Port no oficial de **Perfect Dark** (Nintendo 64, 2000) para Android con **controles táctiles nativos**. Basado en el port de PC del equipo de [`fgsfdsfgs/perfect_dark`](https://github.com/fgsfdsfgs/perfect_dark) y en el trabajo inicial de Android de [`izzy2lost/perfect_dark`](https://github.com/izzy2lost/perfect_dark).

> Necesitas aportar tu propia copia legal del ROM — aquí no se distribuye.

---

## Características

- Port del decomp de Perfect Dark corriendo nativo en ARM (arm64‑v8a / armeabi‑v7a / x86_64 / x86).
- **Overlay táctil al estilo CoD Mobile / Fortnite**:
  - Mitad izquierda: zona de movimiento con stick flotante (se ancla donde pones el pulgar).
  - Mitad derecha: zona de cámara tipo drag-to-look (arrastras, gira; sueltas, se detiene).
  - Botones flotantes para FIRE, AIM, USE, RELOAD, ALT FIRE, cambio de arma, menú radial, crouch, pausa y back.
  - Iconos vectoriales que escalan con el tamaño de cada botón.
- **Editor de layout en vivo**: entras desde un pill flotante en la esquina superior derecha, sin salir del juego. Puedes arrastrar botones, cambiarles el tamaño individual, ajustar sensibilidad X/Y independiente del look-pad, y persistir los cambios.
- **Auto-fade configurable**: el overlay desaparece tras 7 s sin tocarlo y reaparece al instante con cualquier toque.
- **Auto-launch al juego** cuando el ROM ya está en la carpeta de la app.
- Soporte completo para controladores Bluetooth/USB (funcionan a la par del touch).
- Incluye mejoras recientes del upstream: fix de SIGBUS en ARM 32-bit, fix de wrap vertical de cámara, separación de sensibilidad crosshair/cámara, acción Recenter Camera, UI Accept/Cancel swap.

## Requisitos

- **Android 5.0 (Lollipop, API 21)** o superior.
- GPU con **OpenGL ES 3.0**.
- ~20 MB libres para el APK + espacio para el ROM (~32 MB).
- Una copia legal del ROM de Perfect Dark en formato `.z64`:
  - `pd.ntsc-final.z64` — **versión recomendada** (NTSC-U v1.1, MD5 `e03b088b6ac9e0080440efed07c1e40f`).
  - NTSC-U v1.0 también se acepta con advertencia.

## Cómo instalar y jugar

1. Descarga el APK más reciente de [Releases](https://github.com/mgrz18/perfect_dark/releases).
2. Instálalo en tu teléfono Android (habilita "Instalar apps desconocidas" para tu gestor de archivos o navegador si Android te lo pide).
3. Abre la app. La primera vez te mostrará el launcher pidiendo el ROM.
4. Toca **Select ROM** y elige tu archivo `.z64`. Se copia a `Android/data/com.perfectdark.port/files/data/pd.ntsc-final.z64` y se verifica el hash MD5.
5. Si el hash coincide, la app arranca directo al juego. En las siguientes veces saltará el launcher automáticamente.

## Controles táctiles

### Disposición por defecto

| Zona / botón | Acción |
|---|---|
| Mitad izquierda de la pantalla | **Movimiento** (stick flotante, se ancla donde pones el dedo) |
| Mitad derecha de la pantalla | **Cámara** (drag-to-look, tipo CoD Mobile / Quake Mobile) |
| FIRE | Disparo principal (N64: Z) |
| AIM | Apuntar (N64: R) |
| USE | Usar / interactuar |
| RELOAD | Recargar |
| ALT | Disparo alternativo |
| ← / → | Arma anterior / siguiente |
| WPN | Menú radial de armas |
| CRC | Cambiar crouch |
| ≡ | START / pausa |
| ✕ | Cancelar / back |

### Editor de layout en vivo

Pulsa el pill **`EDIT`** en la esquina superior derecha para entrar al modo edición:

- Toca cualquier botón para seleccionarlo → aparece una barrita `−  r=0.055  +` para ajustar su tamaño individual.
- Arrastra cualquier botón para moverlo. Los pads de movimiento y cámara son invisibles y no se editan.
- Barra superior:
  - **SAVE** / **RESET** / **CANCEL** en la primera fila.
  - **FADE: ON/OFF** para activar o desactivar el auto-fade del overlay.
  - **X ±** / **Y ±** para ajustar sensibilidad de cámara por eje (rango 0.05 – 5.0).
  - Pill **`▲`** para colapsar la barra y liberar la parte superior si estás alineando botones ahí.

Los cambios quedan guardados al dar SAVE.

## Créditos

Este port se sostiene sobre el trabajo de muchas personas. Los créditos correctos son:

- **Equipo original del decomp** — [`n64decomp/perfect_dark`](https://github.com/n64decomp/perfect_dark): la ingeniería inversa completa del juego desde la ROM de N64. Sin ese decomp ninguno de los ports existe.
- **[`fgsfdsfgs`](https://github.com/fgsfdsfgs)** y colaboradores — [`fgsfdsfgs/perfect_dark`](https://github.com/fgsfdsfgs/perfect_dark): el port a plataformas modernas (Windows, Linux, macOS, Switch). El motor que corre en Android es esencialmente su trabajo.
- **[`izzy2lost`](https://github.com/izzy2lost)** — [`izzy2lost/perfect_dark`](https://github.com/izzy2lost/perfect_dark): andamiaje inicial de Android (Gradle project, SDL2 Java wrappers, LauncherActivity con picker de ROM vía SAF). Esta rama se basa en su fork.
- **[AL2009man](https://github.com/AL2009man)**, **[joshuarwood](https://github.com/joshuarwood)**, **[rafccq](https://github.com/rafccq)**, **[TartanSpartan](https://github.com/TartanSpartan)**, **[emileb](https://github.com/emileb)** — PRs cherry-pickeados para fixes y mejoras relevantes (ver el historial de commits).
- **Rare / Nintendo** — creadores originales de Perfect Dark (2000).

Los controles táctiles nativos, el editor de layout en vivo, el look-pad con inyección de mouse delta, el auto-fade configurable, y la integración general de este branch fueron construidos con la asistencia de **[Claude](https://claude.com/)** de Anthropic. No habría sido posible hacer todo esto tan rápido sin ese apoyo.

## Soporte / problemas

Si encuentras un bug, tienes una petición, o simplemente quieres comentar algo:

- Abre un **[Issue](https://github.com/mgrz18/perfect_dark/issues)** explicando:
  - Qué hiciste exactamente.
  - Qué esperabas vs. qué pasó.
  - Modelo de teléfono + versión de Android.
  - Región de tu ROM (NTSC v1.1 recomendada).
  - Logs de `adb logcat` si puedes capturarlos.

También se aceptan Pull Requests a la rama `touch-controls`.

## Licencia

Mismo esquema que [`fgsfdsfgs/perfect_dark`](https://github.com/fgsfdsfgs/perfect_dark). El código decompilado y el port son reingeniería a partir de una copia ejecutable; **ni este repo ni sus binarios distribuyen assets, ROMs, audio ni texturas del juego original**, y el usuario debe aportar su propia copia legal del ROM.

## Estado conocido

- Juego funciona completo en single-player y multiplayer en split-screen local.
- Netplay multijugador por red **no** está incluido (existe en la rama `port-net` del upstream pero aún es experimental y no se ha integrado aquí).
- En teléfonos low-end con armeabi-v7a puede haber problemas de rendimiento puntuales.
- Si el overlay táctil se ve mal alineado o prefieres los defaults, resetéalo desde el editor (`EDIT` → `RESET` → `SAVE`).
