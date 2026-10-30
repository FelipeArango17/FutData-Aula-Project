import { NavLink, useNavigate } from 'react-router-dom'
import { menus } from '../config/menus.js'
import { useAuth } from '../context/AuthContext.js'
import { useAccesibilidad } from '../context/AccesibilidadContext.js'

const itemClasses =
  'flex min-h-11 w-full items-center gap-3 rounded-md px-3 py-2 text-left text-body-medium text-on-dark ' +
  'hover:bg-brand-primary-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2'

// Lista de grupos del rol + Accesibilidad y Cerrar sesión al final.
// La usan Sidebar (escritorio) y Drawer (móvil).
export default function NavMenu({ role, onNavigate, accesibilidadRef }) {
  const { cerrarSesion } = useAuth()
  const { abrirPanel } = useAccesibilidad()
  const navigate = useNavigate()

  // replace: el botón Atrás no regresa a la pantalla desde la que se cerró la sesión
  function salir() {
    onNavigate?.() // en el celular, cierra el cajón del menú
    cerrarSesion()
    navigate('/login', { replace: true, state: { aviso: 'Sesión cerrada' } })
  }

  // En el cajón móvil se cierra primero el cajón; el panel recuerda quién lo abrió (ver AccesibilidadProvider)
  function abrirAccesibilidad() {
    abrirPanel()
    onNavigate?.()
  }

  return (
    <nav aria-label="Secciones" className="flex flex-1 flex-col gap-4 p-4">
      {menus[role].map((group, index) => (
        <div key={group.title ?? index}>
          {group.title && (
            <h2 className="px-3 pb-2 text-label uppercase texto-atenuado">{group.title}</h2>
          )}
          <ul className="flex flex-col gap-1">
            {group.items.map((item) => (
              <li key={item.to}>
                {/* NavLink pone aria-current="page" en el ítem activo */}
                <NavLink
                  to={item.to}
                  onClick={onNavigate}
                  className={({ isActive }) => `${itemClasses} ${isActive ? 'bg-brand-primary-hover font-semibold' : ''}`}
                >
                  {({ isActive }) => (
                    <>
                      {/* marca extra para no depender solo del color */}
                      <span
                        aria-hidden="true"
                        className={`min-h-5 w-1 shrink-0 rounded-full ${isActive ? 'bg-brand-accent' : ''}`}
                      />
                      <span className="min-w-0 break-words">{item.label}</span>
                    </>
                  )}
                </NavLink>
              </li>
            ))}
          </ul>
        </div>
      ))}

      <div className="mt-auto flex flex-col gap-1">
        <div className="mb-3 h-px bg-brand-primary-hover" />
        <NavLink
          to="/perfil"
          onClick={onNavigate}
          className={({ isActive }) => `${itemClasses} ${isActive ? 'bg-brand-primary-hover font-semibold' : ''}`}
        >
          {({ isActive }) => (
            <>
              <span aria-hidden="true" className={`min-h-5 w-1 shrink-0 rounded-full ${isActive ? 'bg-brand-accent' : ''}`} />
              <span className="min-w-0 break-words">Mi perfil</span>
            </>
          )}
        </NavLink>
        <button type="button" ref={accesibilidadRef} onClick={abrirAccesibilidad} aria-haspopup="dialog" className={itemClasses}>
          <span aria-hidden="true" className="min-h-5 w-1 shrink-0" />
          Accesibilidad
        </button>
        <button type="button" onClick={salir} className={itemClasses}>
          <span aria-hidden="true" className="min-h-5 w-1 shrink-0" />
          Cerrar sesión
        </button>
      </div>
    </nav>
  )
}
