import { formatHora } from '../../utils/fechas.js'

// Textos de una sesión para las pantallas del horario
export const categoriaDeSesion = (s) => `${s.categoria}${s.grupo ? ` · grupo ${s.grupo}` : ''}`
// miId: id del entrenador con sesión (modo API). Con él, la franja propia dice "Tú"; la de otro, su nombre; sin nadie, "Por turnos".
export function entrenadorDeSesion(s, miId = null) {
  if (miId !== null && s.idEntrenador && String(s.idEntrenador) === String(miId)) return 'Tú'
  if (s.entrenador) return s.entrenador
  return s.idEntrenador ? 'Otro entrenador' : 'Por turnos'
}
export const rangoDeSesion = (s) => `${formatHora(s.hora)} – ${formatHora(s.horaFin)}`
