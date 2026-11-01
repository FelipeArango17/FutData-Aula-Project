// Lógica pura del horario semanal del club (franjas) y de los entrenamientos que salen de él. Sin estado ni red.
// Franja = { id, idCategoria, dia (1 = lunes … 6 = sábado), horaInicio "HH:mm", horaFin "HH:mm", sede, idEntrenador | null, grupo }
// (ver services/horarioClubData.js). idEntrenador null = "Por turnos": no genera choque de entrenador.
import { esHoraValida, sumarDias } from './fechas.js'
import { NOMBRES_CATEGORIAS } from './categorias.js'

export const SEDES_CLUB = ['Horizontes', 'UVA El Paraíso']
export const CATEGORIAS_CON_GRUPO = ['Cachorros', 'Semillero']

export const MENSAJES_FRANJA = {
  categoria: 'Selecciona la categoría.',
  dia: 'Selecciona el día.',
  diaRango: 'El día debe ser de lunes a sábado.',
  horaInicioObligatoria: 'Indica la hora de inicio.',
  horaFinObligatoria: 'Indica la hora de fin.',
  horaInvalida: 'La hora no es válida. Usa el formato HH:MM.',
  horaFinAnterior: 'La hora de fin debe ser posterior a la de inicio.',
  sede: 'Selecciona la sede.',
  grupo: 'El grupo solo aplica a Cachorros y Semillero, y es mañana o tarde.',
  entrenadorOcupado: 'Ese entrenador ya tiene otra franja que se cruza con este horario.',
}

// Dos intervalos [ini, fin) se solapan si cada uno empieza antes de que termine el otro (terminar justo cuando empieza otro no cuenta)
export const seSolapan = (iniA, finA, iniB, finB) => iniA < finB && iniB < finA

export const haySolapamiento = (a, b) => a.dia === b.dia && seSolapan(a.horaInicio, a.horaFin, b.horaInicio, b.horaFin)

const porHora = (a, b) => a.horaInicio.localeCompare(b.horaInicio) || a.idCategoria - b.idCategoria

// { 1: [...], …, 6: [...] } con las franjas de cada día ordenadas por hora
export function franjasPorDia(franjas) {
  return Object.fromEntries([1, 2, 3, 4, 5, 6].map((dia) => [dia, franjas.filter((f) => f.dia === dia).sort(porHora)]))
}

export const franjasDeCategoria = (franjas, idCategoria) => franjas.filter((f) => f.idCategoria === Number(idCategoria)).sort((a, b) => a.dia - b.dia || porHora(a, b))

export const franjasDeEntrenador = (franjas, idEntrenador) => franjas.filter((f) => f.idEntrenador === Number(idEntrenador)).sort((a, b) => a.dia - b.dia || porHora(a, b))

// Errores por campo ({ campo: mensaje }, vacío si todo está bien). `existentes`: las demás franjas; `ignorarId`: la que se edita.
// Dos categorías distintas pueden coincidir en hora; solo el mismo entrenador no puede estar en dos franjas que se cruzan el mismo día.
export function validarFranja(franja, existentes = [], ignorarId = null) {
  const errores = {}
  const nombreCategoria = NOMBRES_CATEGORIAS[Number(franja.idCategoria) - 1]
  if (!nombreCategoria) errores.idCategoria = MENSAJES_FRANJA.categoria

  const dia = Number(franja.dia)
  if (franja.dia === '' || franja.dia === null || franja.dia === undefined) errores.dia = MENSAJES_FRANJA.dia
  else if (!Number.isInteger(dia) || dia < 1 || dia > 6) errores.dia = MENSAJES_FRANJA.diaRango

  if (!franja.horaInicio) errores.horaInicio = MENSAJES_FRANJA.horaInicioObligatoria
  else if (!esHoraValida(franja.horaInicio)) errores.horaInicio = MENSAJES_FRANJA.horaInvalida
  if (!franja.horaFin) errores.horaFin = MENSAJES_FRANJA.horaFinObligatoria
  else if (!esHoraValida(franja.horaFin)) errores.horaFin = MENSAJES_FRANJA.horaInvalida
  else if (!errores.horaInicio && franja.horaFin <= franja.horaInicio) errores.horaFin = MENSAJES_FRANJA.horaFinAnterior

  if (!SEDES_CLUB.includes(franja.sede)) errores.sede = MENSAJES_FRANJA.sede

  if (franja.grupo && (!['mañana', 'tarde'].includes(franja.grupo) || !CATEGORIAS_CON_GRUPO.includes(nombreCategoria))) errores.grupo = MENSAJES_FRANJA.grupo

  const entrenador = franja.idEntrenador === '' || franja.idEntrenador === undefined ? null : franja.idEntrenador
  if (entrenador !== null && !errores.dia && !errores.horaInicio && !errores.horaFin) {
    const candidata = { ...franja, dia }
    const choque = existentes.some((f) => f.id !== ignorarId && f.idEntrenador === Number(entrenador) && haySolapamiento(f, candidata))
    if (choque) errores.idEntrenador = MENSAJES_FRANJA.entrenadorOcupado
  }
  return errores
}

// Los entrenamientos de una semana que salen de la plantilla: `lunes` es "AAAA-MM-DD" (el lunes de esa semana).
// Cada uno trae { id, categoria, fecha, hora, horaFin, sede, idEntrenador, grupo }; los ids van del 1 en adelante por fecha y hora.
export function entrenamientosDeSemana(lunes, franjas) {
  return franjas
    .map((f) => ({
      categoria: NOMBRES_CATEGORIAS[f.idCategoria - 1],
      fecha: sumarDias(lunes, f.dia - 1),
      hora: f.horaInicio,
      horaFin: f.horaFin,
      sede: f.sede,
      idEntrenador: f.idEntrenador,
      grupo: f.grupo,
      idCategoria: f.idCategoria,
    }))
    .sort((a, b) => a.fecha.localeCompare(b.fecha) || a.hora.localeCompare(b.hora) || a.idCategoria - b.idCategoria || (a.grupo ?? '').localeCompare(b.grupo ?? ''))
    .map((e, i) => ({ id: i + 1, categoria: e.categoria, fecha: e.fecha, hora: e.hora, horaFin: e.horaFin, sede: e.sede, idEntrenador: e.idEntrenador, grupo: e.grupo }))
}

// Entrenamientos de una fecha que se cruzan con el horario dado y son del mismo entrenador (null no choca). Misma regla que las franjas.
export function chocaEntrenador(entrenamiento, existentes, ignorarId = null) {
  if (entrenamiento.idEntrenador === null || entrenamiento.idEntrenador === undefined || entrenamiento.idEntrenador === '') return false
  return existentes.some(
    (e) => e.id !== ignorarId && e.idEntrenador === Number(entrenamiento.idEntrenador) && e.fecha === entrenamiento.fecha
      && seSolapan(e.hora, e.horaFin, entrenamiento.hora, entrenamiento.horaFin),
  )
}
