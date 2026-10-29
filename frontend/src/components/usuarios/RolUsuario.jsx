import Badge from '../ui/Badge.jsx'
import { ETIQUETAS_ROL } from '../../services/usuariosService.js'

// Rol con texto + ícono (nunca solo color)
export default function RolUsuario({ rol }) {
  return <Badge tone="neutral">{ETIQUETAS_ROL[rol]}</Badge>
}
