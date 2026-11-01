import { useCallback, useEffect, useRef, useState } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import Sidebar from './Sidebar.jsx'
import TopBar from './TopBar.jsx'
import Drawer from './Drawer.jsx'
import BottomNav from './BottomNav.jsx'
import RoleSwitcher from './RoleSwitcher.jsx'
import PanelAccesibilidad from './PanelAccesibilidad.jsx'
import { useRole } from '../context/RoleContext.js'
import Guardia from './Guardia.jsx'

// Estructura única para los 4 roles; solo cambia el menú.
// Móvil: TopBar + contenido + BottomNav en columna. Desde md: Sidebar + contenido.
// Sin sesión verificada no se pinta nada del panel (ni menú ni contenido): la Guardia espera o redirige.
export default function AppShell() {
  return (
    <Guardia>
      <Panel />
    </Guardia>
  )
}

function Panel() {
  const { role } = useRole()
  const [drawerOpen, setDrawerOpen] = useState(false)
  const menuButtonRef = useRef(null)
  const accesibilidadSidebarRef = useRef(null)
  const location = useLocation()

  // Al llegar desde la verificación 2FA el foco va al título de la pantalla (el botón que se pulsó ya no existe)
  useEffect(() => {
    if (!location.state?.enfocarTitulo) return
    const titulo = document.querySelector('main h1')
    titulo?.setAttribute('tabindex', '-1')
    titulo?.classList.add('focus:outline-none')
    titulo?.focus()
  }, [location.key, location.state])

  const closeDrawer = useCallback(() => setDrawerOpen(false), [])

  // Al cerrar el Drawer el foco vuelve al botón de menú (ya sin inert, por eso va en un efecto)
  const wasOpen = useRef(false)
  useEffect(() => {
    if (drawerOpen) {
      wasOpen.current = true
    } else if (wasOpen.current) {
      wasOpen.current = false
      menuButtonRef.current?.focus()
    }
  }, [drawerOpen])

  return (
    <>
      {/* inert: con el Drawer abierto, el resto de la página no recibe foco ni clics */}
      <div inert={drawerOpen} className="flex min-h-app flex-col md:flex-row">
        <Sidebar role={role} accesibilidadRef={accesibilidadSidebarRef} />
        <div className="flex min-w-0 flex-1 flex-col">
          <TopBar
            onOpenMenu={() => setDrawerOpen(true)}
            menuButtonRef={menuButtonRef}
            drawerOpen={drawerOpen}
          />
          <main className="min-w-0 flex-1 p-4 md:p-8">
            <RoleSwitcher />
            <Outlet />
          </main>
          <BottomNav role={role} />
        </div>
      </div>
      <Drawer open={drawerOpen} onClose={closeDrawer} role={role} />
      {/* Si el botón que abrió el panel ya no existe al cerrarlo, el foco va a uno de estos dos */}
      <PanelAccesibilidad menuButtonRef={menuButtonRef} accesibilidadSidebarRef={accesibilidadSidebarRef} />
    </>
  )
}
