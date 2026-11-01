import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.js'
import { useRole } from '../context/RoleContext.js'
import { decidirAcceso, destinoDeAcceso } from '../utils/accesoRutas.js'
import EsperaSesion from './EsperaSesion.jsx'

// Muestra a sus hijos solo si decidirAcceso lo permite; si no, muestra la espera o redirige.
// rolRuta omitido = cualquier rol con la sesión verificada (AppShell y /perfil).
// Sin sesión se guarda la ruta pedida (state.desde) para volver a ella después de entrar.
export default function Guardia({ rolRuta, children }) {
  const { estado, restaurada } = useAuth()
  const { role } = useRole()
  const location = useLocation()
  const decision = decidirAcceso({
    estado: restaurada ? estado : 'restaurando',
    rolUsuario: role,
    rolRuta: rolRuta ?? role,
  })
  const desde = `${location.pathname}${location.search}${location.hash}`
  if (decision === 'cargando') return <EsperaSesion />
  const destino = destinoDeAcceso(decision, { rolUsuario: role, desde })
  if (destino) return <Navigate to={destino.to} replace state={destino.state} />
  return children
}
