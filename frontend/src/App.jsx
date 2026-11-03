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
import { roles, menus, getItems } from './config/menus.js'
import LlevarEnlaceDeCorreo from './components/auth/LlevarEnlaceDeCorreo.jsx'
import { urlInicial } from './services/urlInicial.js'
import { urlEsDeEnlaceDeCorreo, urlEsDeEnlaceVencido } from './utils/recuperacion.js'

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

// Enlace del correo de Supabase (invitación o recuperación, también vencido o usado): se lleva a /restablecer UNA vez, al cargar.
const ENLACE_DE_CORREO = urlEsDeEnlaceDeCorreo(urlInicial) || urlEsDeEnlaceVencido(urlInicial)

function App() {
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
            <LlevarEnlaceDeCorreo activo={ENLACE_DE_CORREO} />
          </RoleProvider>
        </AuthProvider>
      </AccesibilidadProvider>
    </BrowserRouter>
  )
}

export default App
