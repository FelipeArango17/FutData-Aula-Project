import { formatHora } from '../../utils/fechas.js'

// Textos de una sesión para las pantallas del horario
export const categoriaDeSesion = (s) => `${s.categoria}${s.grupo ? ` · grupo ${s.grupo}` : ''}`
export const entrenadorDeSesion = (s) => s.entrenador ?? 'Por turnos'
export const rangoDeSesion = (s) => `${formatHora(s.hora)} – ${formatHora(s.horaFin)}`
