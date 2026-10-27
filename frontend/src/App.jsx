import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import AppShell from './components/AppShell.jsx'
import RoleRoute from './components/RoleRoute.jsx'
import PlaceholderPage from './pages/PlaceholderPage.jsx'
import RoleProvider from './context/RoleProvider.jsx'
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
        <Routes>
          <Route element={<AppShell />}>
            {roles.map((role) => (
              <Route key={role} path={`/${role}`} element={<RoleRoute role={role} />}>
                <Route index element={<Navigate to={menus[role][0].items[0].to} replace />} />
                {getItems(role).map((item) => (
                  <Route key={item.to} path={item.to} element={<PlaceholderPage title={item.label} />} />
                ))}
                <Route path="*" element={<Navigate to={`/${role}`} replace />} />
              </Route>
            ))}
          </Route>
          <Route path="*" element={<RedirectToRole />} />
        </Routes>
      </RoleProvider>
    </BrowserRouter>
  )
}

export default App
