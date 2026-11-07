import { useEffect, useRef } from 'react'
import Icon from './Icon.jsx'
import Logo from './Logo.jsx'
import NavMenu from './NavMenu.jsx'
import { roleLabels, sampleUsers } from '../config/menus.js'
import { useAuth } from '../context/AuthContext.js'

// Cajón del menú en móvil. Se cierra con Esc, tocando el fondo o al elegir una opción.
export default function Drawer({ open, onClose, role }) {
  const closeButtonRef = useRef(null)
  const auth = useAuth()

  useEffect(() => {
    if (!open) return
    const onKeyDown = (event) => {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKeyDown)
    document.body.style.overflow = 'hidden' // la página de atrás no se desplaza
    closeButtonRef.current?.focus()
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      document.body.style.overflow = ''
    }
  }, [open, onClose])

  if (!open) return null

  // Misma fuente que el saludo del inicio: la sesión verificada. Solo sin sesión verificada (selector de rol en desarrollo) queda el ejemplo.
  const usuario = auth?.estado === 'verificada' && auth.usuario?.rol === role ? auth.usuario : null
  const nombreSesion = String(usuario?.nombre ?? '').trim()
  const rolMostrado = usuario ? roleLabels[usuario.rol] : roleLabels[role]
  // Con sesión verificada nunca se cae al ejemplo: nombre, si no el correo, si no el rol.
  const name = usuario ? nombreSesion || String(usuario.correo ?? '').trim() || rolMostrado : sampleUsers[role]
  const initials = name.split(/\s+/).slice(0, 2).map((word) => word[0]).join('').toUpperCase()

  return (
    <div className="fixed inset-0 z-40 md:hidden">
      <div className="absolute inset-0 bg-sidebar opacity-60" onClick={onClose} aria-hidden="true" />
      <div
        id="drawer"
        role="dialog"
        aria-modal="true"
        aria-label="Menú"
        className="absolute inset-y-0 left-0 flex w-72 max-w-full flex-col overflow-y-auto bg-sidebar pb-safe pt-safe text-on-dark"
      >
        <div className="flex flex-wrap items-center justify-between gap-2 py-2 pl-6 pr-2">
          <Logo />
          <button
            type="button"
            ref={closeButtonRef}
            onClick={onClose}
            aria-label="Cerrar menú"
            className="flex min-h-11 min-w-11 items-center justify-center rounded-md hover:bg-brand-primary-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2"
          >
            <Icon name="close" />
          </button>
        </div>
        {/* Solo informativo: sin elementos enfocables */}
        <div className="flex flex-wrap items-center gap-3 px-6 py-4">
          <span
            aria-hidden="true"
            className="flex min-h-10 min-w-10 shrink-0 items-center justify-center rounded-full bg-brand-primary-hover text-body-medium"
          >
            {initials}
          </span>
          <div className="min-w-0 flex-1 basis-40">
            <p className="break-words text-body-medium">{name}</p>
            <p className="break-words text-small texto-atenuado">{rolMostrado}</p>
          </div>
        </div>
        <NavMenu role={role} onNavigate={onClose} />
      </div>
    </div>
  )
}
