import { Navigate, Outlet } from 'react-router-dom'
import { useRole } from '../context/RoleContext.js'

// Deja pasar solo si el rol activo es el de la ruta; si no, manda al inicio del rol activo.
export default function RoleRoute({ role }) {
  const { role: activeRole } = useRole()
  if (role !== activeRole) return <Navigate to={`/${activeRole}`} replace />
  return <Outlet />
}
