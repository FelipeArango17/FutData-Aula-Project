import Badge from '../ui/Badge.jsx'
import { ETIQUETA_ACTIVO, ETIQUETA_REVOCADO } from '../../services/usuariosService.js'

// Estado del acceso con texto + ícono: Activo / Acceso revocado
export default function EstadoUsuario({ activo }) {
  return <Badge tone={activo ? 'success' : 'danger'}>{activo ? ETIQUETA_ACTIVO : ETIQUETA_REVOCADO}</Badge>
}
