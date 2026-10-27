import { NavLink } from 'react-router-dom'
import Icon from './Icon.jsx'
import { bottomNavs } from '../config/menus.js'

// Barra inferior móvil (4 opciones). El administrador no tiene.
export default function BottomNav({ role }) {
  const items = bottomNavs[role]
  if (!items) return null

  return (
    <nav aria-label="Accesos rápidos" className="sticky bottom-0 z-20 border-t bg-surface pb-safe md:hidden">
      {/* 4 columnas si caben; con letra grande se acomoda solo en 2 filas */}
      <ul className="grid grid-cols-[repeat(auto-fit,minmax(4.5rem,1fr))]">
        {items.map((item) => (
          <li key={item.to}>
            <NavLink
              to={item.to}
              className={({ isActive }) =>
                `flex min-h-14 flex-col items-center justify-center gap-1 px-1 py-2 text-center text-small focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-focus ${
                  isActive ? 'font-semibold text-primary' : 'text-secondary'
                }`
              }
            >
              {({ isActive }) => (
                <>
                  {/* el ítem activo lleva fondo, además de negrita y aria-current */}
                  <span
                    className={`rounded-full px-2 py-0.5 ${isActive ? 'bg-brand-primary text-on-dark' : ''}`}
                  >
                    <Icon name={item.icon} className="h-5 w-5" />
                  </span>
                  <span className="max-w-full break-words">{item.label}</span>
                </>
              )}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  )
}
