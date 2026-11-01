import { Outlet } from 'react-router-dom'
import Guardia from './Guardia.jsx'

// Deja pasar solo con la sesión verificada y si el rol de la sesión es el de la ruta. Sin sesión va a /login (y vuelve
// después); con otro rol, al inicio del rol de la persona.
export default function RoleRoute({ role }) {
  return (
    <Guardia rolRuta={role}>
      <Outlet />
    </Guardia>
  )
}
