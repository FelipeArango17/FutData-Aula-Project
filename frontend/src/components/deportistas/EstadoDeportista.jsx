import Badge from '../ui/Badge.jsx'

// Estado del deportista con texto + ícono: Activo / Inactivo
export default function EstadoDeportista({ activo }) {
  return <Badge tone={activo ? 'success' : 'neutral'}>{activo ? 'Activo' : 'Inactivo'}</Badge>
}
