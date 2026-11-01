// Servicio de entrenamientos (HU-026). Por ahora trabaja sobre datos de ejemplo EN MEMORIA.
// Las funciones son async y devuelven lo que devolvería una API REST, para cambiarlas luego por
// Axios (GET/POST/PUT/DELETE /entrenamientos) sin tocar las pantallas.
//
// Forma de datos propuesta para el frontend:
//   Entrenamiento = { id, categoria, fecha, hora, horaFin, sede, idEntrenador, grupo }
//     id           número
//     categoria    texto, por ejemplo "Sub-12"
//     fecha        "AAAA-MM-DD"
//     hora         "HH:MM" (24 h) de inicio; horaFin "HH:MM" de fin
//     sede         "Horizontes" o "UVA El Paraíso"
//     idEntrenador número, o null = "Por turnos" (Párvulos, Cachorros y Semillero no tienen entrenador fijo)
//     grupo        "mañana" | "tarde" | null (solo Cachorros y Semillero, martes y jueves: dos sesiones alternativas el mismo día)
//   Los entrenamientos de ejemplo de la semana de HOY_EJEMPLO salen de la plantilla semanal del club
//   (horarioClubData.js + utils/horarioClub.js).
//
// Conflicto: un entrenador no puede tener dos entrenamientos que se cruzan en la misma fecha. Varias categorías sí pueden
// coincidir en hora y sede (el club lo hace), y "Por turnos" no choca con nadie.
//
// PENDIENTE: confirmar con el modelo de datos del backend. Entrenamiento, Categoría y Sede
// NO existen todavía en docs/info-sprints/sprint-1/diccionario-datos.md (solo Entrenador, con
// `horario_trabajo`). Todo lo de este archivo es una propuesta y puede cambiar.
import { NOMBRES_CATEGORIAS } from '../utils/categorias.js'
import { chocaEntrenador, entrenamientosDeSemana } from '../utils/horarioClub.js'
import { lunesDe, sumarMinutos } from '../utils/fechas.js'
import { ENTRENADORES_CLUB, FRANJAS_EJEMPLO, SEDES } from './horarioClubData.js'

// Día "de hoy" del mockup de Figma; con él los datos de ejemplo caen en la semana del 19 al 24 de octubre.
export const HOY_EJEMPLO = '2026-10-22'

// Las categorías son las del club (utils/categorias.js, regla provisional por año de nacimiento).
// PENDIENTE: confirmar con el modelo de datos del backend (categorías y sedes aún no existen).
export const CATEGORIAS = NOMBRES_CATEGORIAS
export { SEDES }

// Entrenadores de ejemplo (horarioClubData.js); el rol "entrenador" de la sesión de ejemplo es Nicolás.
// Todos activos: este servicio no gestiona altas ni bajas (eso lo hace entrenadoresService).
export const ENTRENADORES = ENTRENADORES_CLUB.map(({ id, nombre }) => ({ id, nombre, activo: true }))
export const ID_ENTRENADOR_EJEMPLO = 1

// Categorías que puede ver y editar cada rol (dato de ejemplo hasta que exista el login).
export const categoriasPorRol = {
  entrenador: ENTRENADORES_CLUB.find((e) => e.id === ID_ENTRENADOR_EJEMPLO).categorias,
  administrador: CATEGORIAS,
}

// Jugadores por categoría, solo para el mensaje simulado "Se notificará a N jugadores".
const JUGADORES_POR_CATEGORIA = {
  Párvulos: 10, Cachorros: 14, Semillero: 28, 'Sub-11': 16, 'Sub-12': 17, 'Sub-13': 15, 'Sub-14': 16, 'Sub-15': 14, 'Sub-16': 12,
}

// Entrenamientos de la semana de HOY_EJEMPLO, sacados de la plantilla semanal. Se calculan una vez para poder compartir ids
// (la asistencia de ejemplo se apoya en ellos).
export const entrenamientosDeEjemplo = () => entrenamientosDeSemana(lunesDe(HOY_EJEMPLO), FRANJAS_EJEMPLO)

let entrenamientos = entrenamientosDeEjemplo()
let siguienteId = entrenamientos.length + 1

const copiar = (e) => ({ ...e })

const datosLimpios = ({ categoria, fecha, hora, horaFin, sede, idEntrenador, grupo }) => ({
  categoria,
  fecha,
  hora,
  horaFin,
  sede,
  idEntrenador: idEntrenador === '' || idEntrenador === null || idEntrenador === undefined ? null : Number(idEntrenador),
  grupo: grupo || null,
})

export async function listar() {
  return entrenamientos.map(copiar)
}

// Conflicto = el mismo entrenador ya tiene otro entrenamiento que se cruza ese día. Sin entrenador ("Por turnos") no hay conflicto.
// Devuelve { hayConflicto, alternativas: [{ tipo: 'hora', hora, horaFin }] } con el siguiente horario libre del entrenador
// (pasos de 1 h 30 min, con la misma duración).
export async function detectarConflicto(datos, ignorarId = null) {
  const propuesto = datosLimpios(datos)
  if (!chocaEntrenador(propuesto, entrenamientos, ignorarId)) return { hayConflicto: false, alternativas: [] }

  const alternativas = []
  const minutos = (hora) => Number(hora.slice(0, 2)) * 60 + Number(hora.slice(3))
  const duracion = minutos(propuesto.horaFin) - minutos(propuesto.hora)
  for (let hora = sumarMinutos(propuesto.hora, 90); hora; hora = sumarMinutos(hora, 90)) {
    const horaFin = sumarMinutos(hora, duracion)
    if (!horaFin) break
    if (!chocaEntrenador({ ...propuesto, hora, horaFin }, entrenamientos, ignorarId)) {
      alternativas.push({ tipo: 'hora', hora, horaFin })
      break
    }
  }
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
