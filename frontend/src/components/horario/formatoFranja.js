import { ENTRENADORES_CLUB, nombreDeCategoria } from '../../services/horarioClubData.js'
import { formatHora } from '../../utils/fechas.js'

// Textos de una franja para las pantallas del horario del club
export const nombreEntrenadorDe = (franja) => ENTRENADORES_CLUB.find((e) => e.id === franja.idEntrenador)?.nombre ?? 'Por turnos'
export const categoriaConGrupo = (franja) => `${nombreDeCategoria(franja.idCategoria)}${franja.grupo ? ` · grupo ${franja.grupo}` : ''}`
export const rangoHoras = (franja) => `${formatHora(franja.horaInicio)} – ${formatHora(franja.horaFin)}`
