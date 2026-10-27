# Pruebas de responsive en dispositivos (HU-044 · tarea 345)

Objetivo: comprobar que la interfaz funciona en **Android 9**, **iOS 15** y **escritorio 1366×768**.
Navegadores mínimos soportados: Chrome 100+ (Android 9 con Chrome actualizado por Play Store), Safari 15 (iOS 15), y los navegadores de escritorio actuales. Ancho mínimo: 320 px.

## Qué ya se probó (automático, Chromium)

Con Playwright sobre el build (`npm run build && npx vite preview`):

- 4 roles × 17 pantallas × anchos 320, 360, 390, 430 y 1366 px × texto al 100 % y 200 %: sin scroll horizontal, sin elementos fuera de pantalla, sin errores de consola.
- Perfiles tipo Android 9 (360×640), iPhone SE/8, iPhone 12, horizontal, iPad y escritorio 1366×768 y 1920×1080, con toques: TopBar en móvil, Sidebar en pantalla ancha, barra inferior pegada al fondo, cajón alcanzable hasta "Cerrar sesión".

**No cubre:** Safari y Firefox reales, lectores de pantalla, la barra de direcciones que aparece y desaparece en móvil, ni zoom real del navegador. Por eso hace falta lo de abajo.

## Cómo abrir la app en el celular

1. Computador y celular en la **misma red wifi**.
2. En `frontend`: `npm run dev -- --host`
3. Vite muestra la dirección `Network:` (por ejemplo `http://192.168.1.20:5173`). Ábrela en el navegador del celular.
4. Si no carga: revisa que el firewall del computador permita conexiones entrantes en el puerto 5173.
5. Para probar el build real: `npm run build && npx vite preview --host`.

Android con cable (opcional): en Chrome de escritorio `chrome://inspect#devices` permite ver la consola del celular.
iPhone con Mac: Safari → Desarrollo → [tu iPhone] permite ver la consola.

## Lista de comprobación (repetir en cada dispositivo)

Usa el selector de rol (solo en desarrollo) y revisa cada rol. Marca ✔ o anota el problema.

| # | Qué comprobar | Android 9 | iOS 15 | Escritorio 1366×768 |
|---|---|---|---|---|
| 1 | La página carga y se ve el logo y la barra superior (móvil) o la barra lateral (escritorio) | | | |
| 2 | No hay scroll horizontal en ninguna pantalla | | | |
| 3 | El botón de las tres rayas abre el cajón y se cierra con la X, tocando fuera y con la tecla atrás/Esc | | | |
| 4 | El cajón muestra todas las opciones y se llega a "Cerrar sesión" haciendo scroll | | | |
| 5 | La barra inferior queda pegada al borde de abajo (no flota ni se tapa con la barra del navegador o la barra de gestos) | | | n/a |
| 6 | En iPhone con notch: la barra superior no queda tapada por la isla/notch | n/a | | n/a |
| 7 | Gira el celular a horizontal: nada queda cortado y se puede hacer scroll | | | n/a |
| 8 | Los botones se pueden tocar sin errar (tamaño mínimo 44 px) | | | n/a |
| 9 | Control de asistencia (entrenador): marcar presente/ausente/justificado y ver el resumen cambiar | | | |
| 10 | Formularios (Inscribir jugador, Registrar pago, Nuevo entrenamiento): al tocar un campo no se amplía la página ni queda tapado por el teclado | | | |
| 11 | Filtros (Pagos, Documentos, Usuarios): se aplican y el botón activo se distingue | | | |
| 12 | Texto grande: sube el tamaño de letra del sistema al máximo y revisa 3 pantallas (Inicio, Pagos, Asistencia). Nada se corta ni se sale | | | |
| 13 | Gráfico de recaudo del tesorero: se ven los valores y los meses | | | |
| 14 | Colores: los estados (Vencido, Pagado, Pendiente) se entienden sin depender solo del color (llevan ícono y texto) | | | |
| 15 | Escritorio: con la ventana a 1366×768 el contenido usa bien el ancho y la barra lateral hace scroll si el menú no cabe | n/a | n/a | |

## Registro de resultados

| Dispositivo | Sistema y versión | Navegador y versión | Fecha | Quién probó | Problemas encontrados |
|---|---|---|---|---|---|
| | Android 9 | Chrome | | | |
| | iOS 15 | Safari | | | |
| | Windows / macOS | Chrome | | | |

## Cosas que vale la pena mirar con atención

- **iOS 15 (antes de 15.4):** no entiende `100dvh` (ya hay respaldo con `100vh`) ni `:focus-visible` (el anillo de foco puede no verse; no afecta al tacto). Antes de 15.5 tampoco entiende `inert`, así que con el cajón abierto el teclado externo podría enfocar elementos de atrás.
- **Android 9:** si el Chrome del celular no se actualiza (por debajo de la versión 100) el diseño puede romperse; revisa la versión en `chrome://version`.
- Las pantallas usan datos de ejemplo; los botones que necesitan backend (guardar, exportar) todavía no hacen nada.
