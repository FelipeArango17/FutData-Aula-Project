import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import AppShell from './components/AppShell.jsx'
import RoleRoute from './components/RoleRoute.jsx'
import PlaceholderPage from './pages/PlaceholderPage.jsx'
import { screens, extraScreens } from './pages/index.js'
import RoleProvider from './context/RoleProvider.jsx'
import AuthProvider from './context/AuthProvider.jsx'
import Login from './pages/auth/Login.jsx'
import Verificar from './pages/auth/Verificar.jsx'
import Recuperar from './pages/auth/Recuperar.jsx'
import Perfil from './pages/perfil/Perfil.jsx'
import Inscripcion from './pages/inscripcion/Inscripcion.jsx'
import { useAuth } from './context/AuthContext.js'
import EntrenadoresProvider from './context/EntrenadoresProvider.jsx'
import EntrenamientosProvider from './context/EntrenamientosProvider.jsx'
import { useRole } from './context/RoleContext.js'
import { roles, menus, getItems } from './config/menus.js'

// Cualquier ruta desconocida va al inicio del rol activo.
function RedirectToRole() {
  const { role } = useRole()
  return <Navigate to={`/${role}`} replace />
}

// "/" lleva al panel del rol si la sesión ya está verificada; si no, al inicio de sesión.
function Inicio() {
  const { estado, usuario } = useAuth()
  return <Navigate to={estado === 'verificada' ? `/${usuario.rol}` : '/login'} replace />
}

function App() {
  return (
    <BrowserRouter>
      <RoleProvider>
        <AuthProvider>
          <EntrenamientosProvider>
            <Routes>
              {/* Pantallas públicas: fuera del AppShell */}
              <Route path="/" element={<Inicio />} />
              <Route path="/login" element={<Login />} />
              <Route path="/verificar" element={<Verificar />} />
              <Route path="/recuperar" element={<Recuperar />} />
              <Route path="/inscripcion" element={<Inscripcion />} />
              <Route element={<AppShell />}>
                {/* Perfil: dentro del AppShell, para los 4 roles */}
                <Route path="/perfil" element={<Perfil />} />
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
        </AuthProvider>
      </RoleProvider>
    </BrowserRouter>
  )
}

export default App
