import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import AppShell from './components/AppShell.jsx'
import RoleRoute from './components/RoleRoute.jsx'
import PlaceholderPage from './pages/PlaceholderPage.jsx'
import { screens, extraScreens } from './pages/index.js'
import RoleProvider from './context/RoleProvider.jsx'
import EntrenadoresProvider from './context/EntrenadoresProvider.jsx'
import EntrenamientosProvider from './context/EntrenamientosProvider.jsx'
import { useRole } from './context/RoleContext.js'
import { roles, menus, getItems } from './config/menus.js'

// Cualquier ruta desconocida va al inicio del rol activo.
function RedirectToRole() {
  const { role } = useRole()
  return <Navigate to={`/${role}`} replace />
}

function App() {
  return (
    <BrowserRouter>
      <RoleProvider>
        <EntrenamientosProvider>
          <Routes>
            <Route element={<AppShell />}>
              {roles.map((role) => (
                <Route key={role} path={`/${role}`} element={role === 'administrador' ? <EntrenadoresProvider><RoleRoute role={role} /></EntrenadoresProvider> : <RoleRoute role={role} />}>
                  <Route index element={<Navigate to={menus[role][0].items[0].to} replace />} />
                  {getItems(role).map((item) => {
                    const Screen = screens[item.to]
                    return (
                      <Route key={item.to} path={item.to} element={Screen ? <Screen /> : <PlaceholderPage title={item.label} />} />
                    )
                  })}
                  {(extraScreens[role] ?? []).map(({ path, Screen }) => (
                    <Route key={path} path={path} element={<Screen />} />
                  ))}
                  <Route path="*" element={<Navigate to={`/${role}`} replace />} />
                </Route>
              ))}
            </Route>
            <Route path="*" element={<RedirectToRole />} />
          </Routes>
        </EntrenamientosProvider>
      </RoleProvider>
    </BrowserRouter>
  )
}

export default App
