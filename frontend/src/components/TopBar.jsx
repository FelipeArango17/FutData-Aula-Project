import Icon from './Icon.jsx'
import Logo from './Logo.jsx'

const buttonClasses =
  'flex min-h-11 min-w-11 items-center justify-center rounded-md hover:bg-brand-primary-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2'

// Barra superior móvil: menú, logo, y a la derecha accesibilidad y notificaciones. Se oculta desde md.
export default function TopBar({ onOpenMenu, menuButtonRef, drawerOpen }) {
  return (
    <header className="sticky top-0 z-20 bg-sidebar pt-safe text-on-dark md:hidden">
      {/* flex-wrap: con letra muy grande los íconos bajan a otra línea en vez de desbordar */}
      <div className="flex min-h-14 flex-wrap items-center gap-x-2 px-2">
        <button
          type="button"
          ref={menuButtonRef}
          onClick={onOpenMenu}
          aria-label="Abrir menú"
          aria-expanded={drawerOpen}
          aria-controls="drawer"
          className={buttonClasses}
        >
          <Icon name="menu" />
        </button>
        <Logo />
        {/* Sin acción todavía */}
        <div className="ml-auto flex">
          <button type="button" aria-label="Accesibilidad" className={buttonClasses}>
            <Icon name="accessibility" />
          </button>
          <button type="button" aria-label="Notificaciones" className={buttonClasses}>
            <Icon name="bell" />
          </button>
        </div>
      </div>
    </header>
  )
}
