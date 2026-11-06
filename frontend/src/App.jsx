import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import AppShell from './components/AppShell.jsx'
import RoleRoute from './components/RoleRoute.jsx'
import PlaceholderPage from './pages/PlaceholderPage.jsx'
import { screens, extraScreens } from './pages/index.js'
import RoleProvider from './context/RoleProvider.jsx'
import AccesibilidadProvider from './context/AccesibilidadProvider.jsx'
import AuthProvider from './context/AuthProvider.jsx'
import Login from './pages/auth/Login.jsx'
import Verificar from './pages/auth/Verificar.jsx'
import Recuperar from './pages/auth/Recuperar.jsx'
import Restablecer from './pages/auth/Restablecer.jsx'
import CrearContrasena from './pages/auth/CrearContrasena.jsx'
import Perfil from './pages/perfil/Perfil.jsx'
import Inscripcion from './pages/inscripcion/Inscripcion.jsx'
import { useAuth } from './context/AuthContext.js'
import EntrenadoresProvider from './context/EntrenadoresProvider.jsx'
import { useRole } from './context/RoleContext.js'
import EsperaSesion from './components/EsperaSesion.jsx'
import { roles, menus, getItems, RUTA_INSCRIPCION } from './config/menus.js'
import { autenticacionReal } from './services/supabaseClient.js'
import { modulosConApi } from './services/usarApi.js'
import { inicioVisibleDe, opcionDeRuta, opcionVisible } from './utils/menusVisibles.js'

// Cualquier ruta desconocida va al inicio del rol activo.
function RedirectToRole() {
  const { role } = useRole()
  return <Navigate to={role ? `/${role}` : '/login'} replace />
}

// "/" lleva al panel del rol si la sesión ya está verificada; si no, al inicio de sesión.
function Inicio() {
  const { estado, usuario, restaurada } = useAuth()
  if (!restaurada) return <EsperaSesion />
  return <Navigate to={estado === 'verificada' ? `/${usuario.rol}` : '/login'} replace />
}

// Destino cuando la opción no está conectada a la API: la primera opción visible del rol (o el login si no hay ninguna).
function destinoOculto(role, modulos) {
  return inicioVisibleDe({ menus, role, autenticacionReal, modulos }) ?? '/login'
}

function App() {
  const modulos = modulosConApi()
  const visible = (item) => opcionVisible(item, { autenticacionReal, modulos })
  return (
    <BrowserRouter>
      <AccesibilidadProvider>
        <AuthProvider>
          <RoleProvider>
            <Routes>
              {/* Pantallas públicas: fuera del AppShell */}
              <Route path="/" element={<Inicio />} />
              <Route path="/login" element={<Login />} />
              <Route path="/verificar" element={<Verificar />} />
              <Route path="/recuperar" element={<Recuperar />} />
              <Route path="/restablecer" element={<Restablecer />} />
              <Route path="/crear-contrasena" element={<CrearContrasena />} />
              <Route path="/inscripcion" element={visible(RUTA_INSCRIPCION) ? <Inscripcion /> : <Navigate to="/login" replace />} />
              <Route element={<AppShell />}>
                {/* Perfil: dentro del AppShell, para los 4 roles */}
                <Route path="/perfil" element={<Perfil />} />
                {roles.map((role) => (
                  <Route key={role} path={`/${role}`} element={role === 'administrador' ? <EntrenadoresProvider><RoleRoute role={role} /></EntrenadoresProvider> : <RoleRoute role={role} />}>
                    <Route index element={<Navigate to={destinoOculto(role, modulos)} replace />} />
                    {getItems(role).map((item) => {
                      if (!visible(item)) return <Route key={item.to} path={item.to} element={<Navigate to={destinoOculto(role, modulos)} replace />} />
                      const Screen = screens[item.to]
                      return (
                        <Route key={item.to} path={item.to} element={Screen ? <Screen /> : <PlaceholderPage title={item.label} />} />
                      )
                    })}
                    {(extraScreens[role] ?? []).map(({ path, Screen }) => {
                      const madre = opcionDeRuta(menus, role, path)
                      const oculta = madre && !visible(madre)
                      return <Route key={path} path={path} element={oculta ? <Navigate to={destinoOculto(role, modulos)} replace /> : <Screen />} />
                    })}
                    <Route path="*" element={<Navigate to={`/${role}`} replace />} />
                  </Route>
                ))}
              </Route>
              <Route path="*" element={<RedirectToRole />} />
            </Routes>
          </RoleProvider>
        </AuthProvider>
      </AccesibilidadProvider>
    </BrowserRouter>
  )
}

export default App
