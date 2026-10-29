import HistorialBase from '../ui/HistorialCambios.jsx'
import { ESTADOS_ASISTENCIA } from './estados.js'

// Historial de ediciones de una asistencia, en orden cronológico (de la más antigua a la más reciente).
// momento(fechaHora) devuelve el texto de fecha y hora; nombres: { idDeportista: nombre completo }.
// Solo adapta los datos de asistencia al historial genérico (components/ui/HistorialCambios.jsx).
const verObs = (texto) => (texto ? `«${texto}»` : 'sin observación')

function textoCambio({ antes, despues }) {
  const partes = []
  if (antes.estado !== despues.estado) partes.push(`${ESTADOS_ASISTENCIA[antes.estado].texto} → ${ESTADOS_ASISTENCIA[despues.estado].texto}`)
  if (antes.observacion !== despues.observacion) partes.push(`Observación: ${verObs(antes.observacion)} → ${verObs(despues.observacion)}`)
  return partes.join(' · ')
}

export default function HistorialCambios({ historial, nombres, momento }) {
  const entradas = historial.map((entrada, i) => ({
    clave: `${entrada.fechaHora}-${i}`,
    encabezado: `${momento(entrada.fechaHora)} · ${entrada.responsable}`,
    lineas: entrada.cambios.map((c) => ({ clave: c.idDeportista, texto: `${nombres[c.idDeportista]}: ${textoCambio(c)}` })),
  }))
  return <HistorialBase entradas={entradas} vacio="Todavía no se ha corregido esta asistencia." />
}
