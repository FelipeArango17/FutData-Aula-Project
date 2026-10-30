// Logo FD + nombre. Va sobre fondo oscuro (TopBar, Drawer, Sidebar).
// El texto «FD» cambia de color según el tema para mantener el contraste sobre el verde de cada uno.
export default function Logo() {
  return (
    <span className="flex min-w-0 items-center gap-2">
      <span
        aria-hidden="true"
        className="flex min-h-8 min-w-8 shrink-0 items-center justify-center rounded-md bg-brand-accent text-label text-primary [[data-theme=alto-contraste]_&]:text-on-dark [[data-theme=oscuro]_&]:text-on-dark"
      >
        FD
      </span>
      <span className="text-h3">FutData</span>
    </span>
  )
}
