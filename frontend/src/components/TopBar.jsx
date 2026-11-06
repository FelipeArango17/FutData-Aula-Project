import Icon from './Icon.jsx'
import Logo from './Logo.jsx'
import { useAccesibilidad } from '../context/AccesibilidadContext.js'
import { autenticacionReal } from '../services/supabaseClient.js'
import { modulosConApi } from '../services/usarApi.js'
import { opcionVisible } from '../utils/menusVisibles.js'

const buttonClasses =
  'flex min-h-11 min-w-11 items-center justify-center rounded-md hover:bg-brand-primary-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2'

// Barra superior móvil: menú, logo, y a la derecha accesibilidad y notificaciones. Se oculta desde md.
export default function TopBar({ onOpenMenu, menuButtonRef, drawerOpen }) {
  const { abrirPanel } = useAccesibilidad()
  // La campana es el acceso a los avisos: solo existe si el módulo "avisos" está conectado a la API.
  const verAvisos = opcionVisible({ modulo: 'avisos' }, { autenticacionReal, modulos: modulosConApi() })
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
        <div className="ml-auto flex">
          <button type="button" onClick={abrirPanel} aria-label="Accesibilidad" aria-haspopup="dialog" className={buttonClasses}>
            <Icon name="accessibility" />
          </button>
          {verAvisos && (
            <button type="button" aria-label="Notificaciones" className={buttonClasses}>
              <Icon name="bell" />
            </button>
          )}
        </div>
      </div>
    </header>
  )
}
