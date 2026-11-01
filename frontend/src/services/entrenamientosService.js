// Servicio de entrenamientos (HU-026). Por ahora trabaja sobre datos de ejemplo EN MEMORIA.
// Las funciones son async y devuelven lo que devolvería una API REST, para cambiarlas luego por
// Axios (GET/POST/PUT/DELETE /entrenamientos) sin tocar las pantallas.
//
// Forma de datos propuesta para el frontend:
//   Entrenamiento = { id, categoria, fecha, hora, lugar, idEntrenador }
//     id           número
//     categoria    texto, por ejemplo "Sub-12"
//     fecha        "AAAA-MM-DD"
//     hora         "HH:MM" (24 h)
//     lugar        texto, por ejemplo "Cancha 2"
//     idEntrenador número (Entrenador.id_entrenador del diccionario de datos)
//
// PENDIENTE: confirmar con el modelo de datos del backend. Entrenamiento, Categoría y Lugar
// NO existen todavía en docs/info-sprints/sprint-1/diccionario-datos.md (solo Entrenador, con
// `horario_trabajo`). Todo lo de este archivo es una propuesta y puede cambiar.
import { NOMBRES_CATEGORIAS } from '../utils/categorias.js'
import { sumarMinutos } from '../utils/fechas.js'

// Día "de hoy" del mockup de Figma; con él los datos de ejemplo caen en la semana del 19 al 24 de octubre.
export const HOY_EJEMPLO = '2026-10-22'

// Las categorías son las del club (utils/categorias.js, regla provisional por año de nacimiento).
// PENDIENTE: confirmar con el modelo de datos del backend (categorías y lugares aún no existen).
export const CATEGORIAS = NOMBRES_CATEGORIAS
export const LUGARES = ['Cancha 1', 'Cancha 2']

// Categorías que puede ver y editar cada rol (dato de ejemplo hasta que exista el login).
export const categoriasPorRol = {
  entrenador: ['Sub-10', 'Sub-12'],
  administrador: CATEGORIAS,
}

// Entrenadores de ejemplo; el rol "entrenador" de la sesión de ejemplo es Andrés Mora.
export const ENTRENADORES = [
  { id: 1, nombre: 'Andrés Mora' },
  { id: 2, nombre: 'Carlos Díaz' },
]
export const ID_ENTRENADOR_EJEMPLO = 1

// Jugadores por categoría, solo para el mensaje simulado "Se notificará a N jugadores".
const JUGADORES_POR_CATEGORIA = {
  Párvulos: 10, Cachorros: 14, 'Sub-9': 13, 'Sub-10': 15, 'Sub-11': 16, 'Sub-12': 17, 'Sub-13': 15, 'Sub-14': 16, 'Sub-15': 14, 'Sub-16': 12,
}

let entrenamientos = [
  { id: 1, categoria: 'Cachorros', fecha: '2026-10-19', hora: '16:00', lugar: 'Cancha 1', idEntrenador: 2 },
  { id: 2, categoria: 'Sub-12', fecha: '2026-10-19', hora: '17:30', lugar: 'Cancha 2', idEntrenador: 1 },
  { id: 3, categoria: 'Sub-10', fecha: '2026-10-20', hora: '16:00', lugar: 'Cancha 1', idEntrenador: 2 },
  { id: 4, categoria: 'Sub-14', fecha: '2026-10-20', hora: '17:30', lugar: 'Cancha 1', idEntrenador: 2 },
  { id: 5, categoria: 'Cachorros', fecha: '2026-10-21', hora: '16:00', lugar: 'Cancha 1', idEntrenador: 2 },
  { id: 6, categoria: 'Sub-12', fecha: '2026-10-21', hora: '17:30', lugar: 'Cancha 2', idEntrenador: 1 },
  { id: 7, categoria: 'Sub-10', fecha: '2026-10-22', hora: '16:00', lugar: 'Cancha 1', idEntrenador: 2 },
  { id: 8, categoria: 'Sub-14', fecha: '2026-10-22', hora: '17:30', lugar: 'Cancha 1', idEntrenador: 2 },
  { id: 9, categoria: 'Sub-16', fecha: '2026-10-23', hora: '17:30', lugar: 'Cancha 1', idEntrenador: 2 },
  { id: 10, categoria: 'Sub-12', fecha: '2026-10-24', hora: '09:00', lugar: 'Cancha 2', idEntrenador: 1 },
  { id: 11, categoria: 'Sub-12', fecha: '2026-10-24', hora: '10:00', lugar: 'Cancha 2', idEntrenador: 1 },
]
let siguienteId = 12

const copiar = (e) => ({ ...e })
const mismoHorario = (a, b) => a.fecha === b.fecha && a.hora === b.hora && a.lugar === b.lugar

const datosLimpios = ({ categoria, fecha, hora, lugar, idEntrenador }) => ({
  categoria,
  fecha,
  hora,
  lugar,
  idEntrenador: Number(idEntrenador),
})

export async function listar() {
  return entrenamientos.map(copiar)
}

// Conflicto = otro entrenamiento en el mismo lugar, fecha y hora.
// Devuelve { hayConflicto, alternativas: [{ tipo: 'hora', hora } | { tipo: 'lugar', lugar }] }.
export async function detectarConflicto(datos, ignorarId = null) {
  const otros = entrenamientos.filter((e) => e.id !== ignorarId)
  if (!otros.some((e) => mismoHorario(e, datos))) return { hayConflicto: false, alternativas: [] }

  const alternativas = []
  // Misma fecha y lugar, más tarde (pasos de 1 h 30 min, como el 11:30 del diseño)
  for (let hora = sumarMinutos(datos.hora, 90); hora; hora = sumarMinutos(hora, 90)) {
    if (!otros.some((e) => mismoHorario(e, { ...datos, hora }))) {
      alternativas.push({ tipo: 'hora', hora })
      break
    }
  }
  // Misma fecha y hora, en otro lugar libre
  const lugarLibre = LUGARES.find((lugar) => lugar !== datos.lugar && !otros.some((e) => mismoHorario(e, { ...datos, lugar })))
  if (lugarLibre) alternativas.push({ tipo: 'lugar', lugar: lugarLibre })

  return { hayConflicto: true, alternativas }
}

// crear y editar vuelven a comprobar el conflicto: una API real también lo haría (error 409).
async function validarSinConflicto(datos, ignorarId) {
  const conflicto = await detectarConflicto(datos, ignorarId)
  if (conflicto.hayConflicto) throw Object.assign(new Error('Conflicto de horario'), { conflicto })
}

// Cada operación devuelve cuántos jugadores recibirían el aviso (simulado, no se envía nada).
export async function crear(datos) {
  const nuevo = datosLimpios(datos)
  await validarSinConflicto(nuevo, null)
  const entrenamiento = { id: siguienteId++, ...nuevo }
  entrenamientos = [...entrenamientos, entrenamiento]
  return { entrenamiento: copiar(entrenamiento), notificados: JUGADORES_POR_CATEGORIA[nuevo.categoria] ?? 0 }
}

export async function editar(id, datos) {
  const actual = entrenamientos.find((e) => e.id === id)
  if (!actual) throw new Error('El entrenamiento no existe')
  const cambios = datosLimpios(datos)
  await validarSinConflicto(cambios, id)
  const entrenamiento = { id, ...cambios }
  entrenamientos = entrenamientos.map((e) => (e.id === id ? entrenamiento : e))
  return { entrenamiento: copiar(entrenamiento), notificados: JUGADORES_POR_CATEGORIA[cambios.categoria] ?? 0 }
}

export async function eliminar(id) {
  const actual = entrenamientos.find((e) => e.id === id)
  if (!actual) throw new Error('El entrenamiento no existe')
  entrenamientos = entrenamientos.filter((e) => e.id !== id)
  return { id, notificados: JUGADORES_POR_CATEGORIA[actual.categoria] ?? 0 }
}
