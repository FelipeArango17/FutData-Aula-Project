import Icon from '../Icon.jsx'
import { ESTADOS_ASISTENCIA } from './estados.js'

// Estado de asistencia en solo lectura: texto + ícono
export default function EstadoChip({ estado }) {
  const { texto, icono, clases } = ESTADOS_ASISTENCIA[estado]
  return (
    <span className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-small font-semibold ${clases}`}>
      <Icon name={icono} className="h-3.5 w-3.5 shrink-0" />
      <span className="break-words">{texto}</span>
    </span>
  )
}
