# Design system FutData: equivalencias Figma a Tailwind

Tarea 287 · HU-043 · Fuente de verdad: archivo de Figma "Mockups FutData" (páginas Fundamentos, Componentes y Pantallas).
Archivos de este documento: `tokens.css` (variables CSS) y `tailwind.config.js` (Tailwind v3).

## 1. Cómo está armado

Figma tiene 3 colecciones de variables y 8 estilos de texto. En código se replican así:

- Cada variable de Figma tiene un nombre de código (`codeSyntax` WEB) que coincide con la variable CSS de `tokens.css`. Ejemplo: `color/bg/app` → `var(--color-bg-app)`.
- Los colores semánticos cambian de valor según el tema. El tema se elige con un atributo en `<html>`: `data-theme="normal"`, `"alto-contraste"` u `"oscuro"` (sin atributo = normal).
- `tailwind.config.js` no guarda hex: apunta a esas variables. Por eso las clases (`bg-app`, `text-primary`) cambian solas al cambiar el tema.

## 2. Colores semánticos

Colección `Color` (modos Normal, Alto contraste, Oscuro). Los valores salen de los primitivos (colección `Primitives`, 27 colores, definidos en `tokens.css`).

| Token Figma | Variable CSS | Clase Tailwind | Normal | Alto contraste | Oscuro |
|---|---|---|---|---|---|
| `color/bg/app` | `--color-bg-app` | `bg-app` | `#F7F8FA` (gray/50) | `#FFFFFF` (gray/0) | `#0F2C4C` (navy/900) |
| `color/bg/surface` | `--color-bg-surface` | `bg-surface` | `#FFFFFF` (gray/0) | `#FFFFFF` (gray/0) | `#1A3D63` (navy/700) |
| `color/bg/sidebar` | `--color-bg-sidebar` | `bg-sidebar` | `#0F2C4C` (navy/900) | `#000000` (black) | `#FFFFFF` (gray/0) |
| `color/text/primary` | `--color-text-primary` | `text-primary` | `#1A2332` (gray/900) | `#000000` (black) | `#FFFFFF` (gray/0) |
| `color/text/secondary` | `--color-text-secondary` | `text-secondary` | `#4B5563` (gray/600) | `#1A2332` (gray/900) | `#E5E7EB` (gray/200) |
| `color/text/on-dark` | `--color-text-on-dark` | `text-on-dark` | `#FFFFFF` (gray/0) | `#FFFFFF` (gray/0) | `#0F2C4C` (navy/900) |
| `color/border/default` | `--color-border-default` | `border-default` | `#E5E7EB` (gray/200) | `#1A2332` (gray/900) | `#2A4F7A` (navy/500) |
| `color/brand/primary` | `--color-brand-primary` | `bg-brand-primary` | `#0F2C4C` (navy/900) | `#000000` (black) | `#FFFFFF` (gray/0) |
| `color/brand/primary-hover` | `--color-brand-primary-hover` | `hover:bg-brand-primary-hover` | `#1A3D63` (navy/700) | `#1A2332` (gray/900) | `#E5E7EB` (gray/200) |
| `color/brand/accent` | `--color-brand-accent` | `bg-brand-accent` | `#3CB043` (green/500) | `#1F7A29` (green/700) | `#3CB043` (green/500) |
| `color/brand/accent-text` | `--color-brand-accent-text` | `text-brand-accent` | `#1F7A29` (green/700) | `#1F7A29` (green/700) | `#9BE3A0` (green/200) |
| `color/status/success-bg` | `--color-status-success-bg` | `bg-success` | `#E8F7E8` (green/50) | `#FFFFFF` (gray/0) | `#0F3D1E` (green/900) |
| `color/status/success-text` | `--color-status-success-text` | `text-success / border-success` | `#1F7A29` (green/700) | `#1F7A29` (green/700) | `#9BE3A0` (green/200) |
| `color/status/warning-bg` | `--color-status-warning-bg` | `bg-warning` | `#FEF3E2` (amber/50) | `#FFFFFF` (gray/0) | `#4A3000` (amber/900) |
| `color/status/warning-text` | `--color-status-warning-text` | `text-warning / border-warning` | `#7A4B00` (amber/800) | `#7A4B00` (amber/800) | `#FFD58A` (amber/200) |
| `color/status/danger-bg` | `--color-status-danger-bg` | `bg-danger` | `#FDECEA` (red/50) | `#FFFFFF` (gray/0) | `#4A1512` (red/900) |
| `color/status/danger-text` | `--color-status-danger-text` | `text-danger / border-danger` | `#B3261E` (red/700) | `#B3261E` (red/700) | `#FFB4AB` (red/200) |
| `color/focus/ring` | `--color-focus-ring` | `ring-focus / outline-focus` | `#2A4F7A` (navy/500) | `#0F2C4C` (navy/900) | `#A9C8F5` (blue/200) |

Notas de uso:
- `text-success`, `text-warning` y `text-danger` también sirven como color de borde con `border-success`, etc.
- El estado nunca se comunica solo con color: el badge lleva ícono y texto además del color.
- Las clases con opacidad (`bg-app/50`) no funcionan con variables CSS planas en Tailwind v3. Si hace falta transparencia, se agrega una variable aparte.
- Los colores por defecto de Tailwind (`bg-gray-200`, etc.) siguen disponibles. Para el producto, usar solo los tokens de arriba.

## 3. Tipografía

Familia: **Plus Jakarta Sans** (400, 500, 600, 700). Clase base: `font-sans` (definida en la config). `tokens.css` la importa desde Google Fonts; si se prefiere autoalojar, usar el paquete `@fontsource/plus-jakarta-sans`.

| Familia de estilo | Estilo en Figma | Clase Tailwind | Tamaño / interlineado | Peso |
|---|---|---|---|---|
| Display | Display/H1 | `text-display` | 28 / 36 px | 700 |
| Heading | Heading/H2 | `text-h2` | 22 / 30 px | 600 |
| Heading | Heading/H3 | `text-h3` | 18 / 26 px | 600 |
| Body | Body/Base | `text-body` | 14 / 22 px | 400 |
| Body | Body/Medium | `text-body-medium` | 14 / 22 px | 500 |
| Body | Body/Small | `text-small` | 12 / 18 px | 400 |
| Label | Label/Caps | `text-label` | 11 / 16 px | 600 |
| KPI | KPI/Number | `text-kpi` | 26 / 32 px | 700 |

Notas:
- **Los tamaños van en `rem`, no en `px`** (28 px = `1.75rem`, 22 = `1.375rem`, 18 = `1.125rem`, 14 = `0.875rem`, 12 = `0.75rem`, 11 = `0.6875rem`, 26 = `1.625rem`; los interlineados también). Así el texto escala cuando el usuario sube el tamaño de letra del navegador o de la app (CU-08, hasta 200 %). En `px` ese ajuste no tendría efecto. La tabla de arriba muestra los valores de Figma en px; en `tailwind.config.js` están en rem.
- `Label/Caps` no aplica transformación de mayúsculas en Figma; si una pantalla lo muestra en mayúsculas, añadir la clase `uppercase` en ese uso.
- `KPI` es para los números grandes de las tarjetas de métricas financieras y deportivas.

## 4. Espaciado y radios

Los valores de Figma coinciden con la escala por defecto de Tailwind, así que **no hace falta configuración extra**.

| Token Figma | Valor | Clase de espaciado (`p-`, `m-`, `gap-`) |
|---|---|---|
| spacing/xs | 4 px | `1` (`p-1`, `gap-1`) |
| spacing/sm | 8 px | `2` |
| spacing/md | 12 px | `3` |
| spacing/lg | 16 px | `4` |
| spacing/xl | 24 px | `6` |
| spacing/2xl | 32 px | `8` |

| Token Figma | Valor | Clase Tailwind |
|---|---|---|
| radius/sm | 6 px | `rounded-md` |
| radius/md | 8 px | `rounded-lg` |
| radius/lg | 12 px | `rounded-xl` |
| radius/xl | 16 px | `rounded-2xl` |
| radius/full | 999 px | `rounded-full` |

Las variables `--spacing-*` y `--radius-*` de `tokens.css` existen como referencia y para CSS propio.

## 5. Contraste (WCAG 2.1 AA)

Calculado con la fórmula de luminancia relativa de WCAG sobre los valores de los tokens. Texto: mínimo 4.5:1. Elementos que no son texto (foco, bordes de campos): 3:1. ⚠ = no cumple.

| Combinación | Mínimo | Normal | Alto contraste | Oscuro |
|---|---|---|---|---|
| text/primary sobre bg/app | 4.5:1 | 14.85 | 21.00 | 14.14 |
| text/primary sobre bg/surface | 4.5:1 | 15.78 | 21.00 | 11.10 |
| text/secondary sobre bg/app | 4.5:1 | 7.11 | 15.78 | 11.42 |
| text/secondary sobre bg/surface | 4.5:1 | 7.56 | 15.78 | 8.97 |
| text/on-dark sobre bg/sidebar | 4.5:1 | 14.14 | 21.00 | 14.14 |
| text/on-dark sobre brand/primary | 4.5:1 | 14.14 | 21.00 | 14.14 |
| text/on-dark sobre brand/accent | 4.5:1 | 2.81 ⚠ | 5.42 | 5.04 |
| brand/accent-text sobre bg/surface | 4.5:1 | 5.42 | 5.42 | 7.34 |
| success-text sobre success-bg | 4.5:1 | 4.88 | 5.42 | 8.14 |
| warning-text sobre warning-bg | 4.5:1 | 6.75 | 7.41 | 8.83 |
| danger-text sobre danger-bg | 4.5:1 | 5.72 | 6.54 | 8.79 |
| focus/ring sobre bg/surface (no texto) | 3.0:1 | 8.41 | 14.14 | 6.49 |
| border/default sobre bg/surface (no texto) | 3.0:1 | 1.24 ⚠ | 15.78 | 1.32 ⚠ |

Qué significa en la práctica:
- **Las combinaciones de texto y fondo de la tabla (salvo la excepción de abajo) cumplen AA en los tres modos.**
- `text-on-dark` sobre `bg-brand-accent` no cumple en modo Normal (2.81:1). Ninguna pantalla del prototipo lo usa; en código, no combinarlos.
- `border-default` es suficiente para tarjetas y separadores decorativos, pero **no para el borde de un campo de formulario en modo Normal u Oscuro** (1.24:1 y 1.32:1, mínimo 3:1). En Alto contraste sí cumple. Para campos, usar un borde más oscuro (por ejemplo `text-secondary` como color de borde) o dejar este punto como mejora pendiente.
- El logo "FD" (texto `brand/primary` sobre `brand/accent`) da 5.04 (Normal), 3.87 (Alto contraste) y 2.81 (Oscuro). Un logotipo está exento del requisito de contraste de WCAG, pero en modo Oscuro conviene usar texto `navy/900` (5.04:1).
- El indicador verde del ítem activo del menú es un elemento visual que no es texto: 5.04 (Normal), 3.87 (Alto contraste) y 2.81 en Oscuro (bajo el 3:1 por poco). El ítem activo también cambia de peso y fondo, así que no depende solo del color.

## 6. Instalación en el frontend (React + Vite)

```bash
cd frontend
npm i -D tailwindcss@3 postcss autoprefixer
npx tailwindcss init -p     # crea tailwind.config.js y postcss.config.js
```

1. Reemplazar el `tailwind.config.js` generado por el de este documento. Usa `export default`, así que `package.json` debe tener `"type": "module"` (lo trae la plantilla de Vite).
2. Copiar `tokens.css` a `frontend/src/styles/tokens.css` e importarlo en `main.jsx` antes de `index.css`:
   ```js
   import './styles/tokens.css'
   import './index.css'
   ```
3. En `index.css`: `@tailwind base; @tailwind components; @tailwind utilities;`
4. Cambiar de tema:
   ```js
   document.documentElement.dataset.theme = 'oscuro'   // 'normal' | 'alto-contraste' | 'oscuro'
   ```
   Para guardar la preferencia (HU-048), usar `localStorage` al cambiar el tema y leerla al arrancar la aplicación.

Si el frontend usa Tailwind v4 (configuración en CSS con `@theme`), la versión de la config cambia; hay que adaptarla y probarla antes de usarla.

## 7. Ejemplos

```html
<!-- Botón primario -->
<button class="bg-brand-primary hover:bg-brand-primary-hover text-on-dark text-body-medium rounded-lg px-4 py-2 focus-visible:ring-2 ring-focus">Guardar</button>

<!-- Badge de estado: color + ícono + texto -->
<span class="bg-success text-success border border-success rounded-full px-3 py-1 text-small">✓ Pagado</span>

<!-- Tarjeta de indicador -->
<div class="bg-surface border border-default rounded-xl p-4">
  <p class="text-label text-secondary">INGRESOS DEL MES</p>
  <p class="text-kpi text-primary">$ 4.250.000</p>
</div>
```

## 8. Verificación

El `tailwind.config.js` se compiló con Tailwind 3.4.17 y se comprobó que se generan, con los valores esperados, todas las clases de color, tipografía, espaciado y radios que aparecen en las tablas de este documento. No se probó todavía dentro del frontend real, que aún no existe en el repositorio.
