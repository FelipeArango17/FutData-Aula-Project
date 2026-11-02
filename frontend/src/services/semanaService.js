// Servicio de la pantalla "Horarios": la semana del club (franjas fijas + cancelaciones + extras).
//
// Modos (los dos no se mezclan: ids numéricos de ejemplo vs UUID de la API):
// - Ejemplo (sin VITE_API_MODULOS con "horarioclub"; siempre en las pruebas): todo en memoria sobre FRANJAS_EJEMPLO, con todas
//   las capacidades.
// - API (módulo "horarioclub"): la semana sale de GET /api/horario-club (datos reales). Se puede cambiar el entrenador de una
//   franja (PUT); cancelar y extras quedan ocultos hasta que exista el contrato 4B (ver utils/semanaApi.js). El módulo
//   "entrenamientos" todavía no cambia nada.
//
// Errores: Error con `codigo` -> 'VALIDACION' (con `errores`) | 'CONFLICTO' | 'NO_EXISTE' | 'NO_DISPONIBLE'; los de la API traen
// además `estado` y `mensaje` (el del backend, en español).
import { ENTRENADORES_CLUB, nombreDeCategoria, idCategoriaDe } from './horarioClubData.js'
import * as horarioClub from './horarioClubService.js'
import * as entrenadoresService from './entrenadoresService.js'
import { ID_ENTRENADOR_EJEMPLO, categoriasPorRol } from './entrenamientosService.js'
import { usarApiDe } from './usarApi.js'
import { capacidadesApi, excepcionesDeSemanaApi } from '../utils/semanaApi.js'
import { MENSAJE_MOTIVO, chocaEntrenadorEnSesiones, sesionesDeSemana, validarExtra, validarMotivo } from '../utils/semana.js'
import { NOMBRES_CATEGORIAS } from '../utils/categorias.js'
import { lunesDe } from '../utils/fechas.js'
import { hoy } from '../utils/hoy.js'

const usarApi = usarApiDe('horarioclub')
export const semanaPorApi = () => usarApi

const error = (codigo, mensaje, extra) => Object.assign(new Error(mensaje), { codigo }, extra)
const noDisponible = () => error('NO_DISPONIBLE', 'Esta acción todavía no está disponible.')

export function capacidades() {
  if (!usarApi) return { leer: true, cancelar: true, extras: true, rotarEntrenador: true }
  return { leer: true, ...capacidadesApi(), rotarEntrenador: true }
}

// Quién es el entrenador con sesión, para saber sobre qué categorías actúa. En modo API NO se puede saber: la sesión no trae el id
// del entrenador ni las franjas lo relacionan con la cuenta, y no se inventa un mapeo -> null (solo lectura). PENDIENTE (Omar).
export function identidadEntrenador() {
  if (usarApi) return null
  return { id: ID_ENTRENADOR_EJEMPLO, categorias: [...categoriasPorRol.entrenador] }
}

// Entrenadores que se pueden elegir (activos o no; la pantalla decide). Solo el administrador puede listarlos en la API.
export async function listarEntrenadores() {
  if (!usarApi) return ENTRENADORES_CLUB.map(({ id, nombre }) => ({ id, nombre, activo: true }))
  const lista = await entrenadoresService.listar()
  return lista.map((e) => ({ id: e.id, nombre: `${e.nombre} ${e.apellido}`.trim(), activo: e.activo !== false }))
}

// ---- Ejemplo: excepciones en memoria ----
let excepciones = []
let siguienteId = 1
const nombreEntrenador = (id) => ENTRENADORES_CLUB.find((e) => String(e.id) === String(id))?.nombre ?? null
const idNumerico = (valor) => (valor === '' || valor === null || valor === undefined ? null : Number(valor))

// Las franjas de ejemplo no traen nombres: se agregan para que todas tengan la misma forma que las de la API
const conNombres = (f) => ({ ...f, categoria: f.categoria ?? nombreDeCategoria(f.idCategoria), entrenador: f.entrenador ?? nombreEntrenador(f.idEntrenador) })

export async function listarSemana(lunes) {
  const franjas = (await horarioClub.listar()).map(conNombres)
  const deLaSemana = usarApi ? await excepcionesDeSemanaApi(lunes) : excepciones
  return sesionesDeSemana(franjas, deLaSemana, lunes)
}

export async function cancelar({ fecha, idFranja, motivo }) {
  if (usarApi) throw noDisponible()
  if (fecha < hoy()) throw error('VALIDACION', 'No se puede cancelar un entrenamiento de una fecha pasada.', { errores: { fecha: 'No se puede cancelar una fecha pasada.' } })
  if (validarMotivo(motivo)) throw error('VALIDACION', MENSAJE_MOTIVO, { errores: { motivo: MENSAJE_MOTIVO } })
  const franja = (await horarioClub.listar()).find((f) => String(f.id) === String(idFranja))
  if (!franja) throw error('NO_EXISTE', 'La franja no existe.')
  if (excepciones.some((e) => e.tipo === 'CANCELACION' && String(e.idFranja) === String(idFranja) && e.fecha === fecha)) {
    throw error('CONFLICTO', 'Ese entrenamiento ya está cancelado.')
  }
  const nueva = { id: siguienteId++, tipo: 'CANCELACION', fecha, idFranja: franja.id, motivo: String(motivo ?? '').trim() || null }
  excepciones = [...excepciones, nueva]
  return { ...nueva, categoria: nombreDeCategoria(franja.idCategoria) }
}

export async function reactivar(idExcepcion) {
  if (usarApi) throw noDisponible()
  const actual = excepciones.find((e) => e.tipo === 'CANCELACION' && String(e.id) === String(idExcepcion))
  if (!actual) throw error('NO_EXISTE', 'La cancelación no existe.')
  excepciones = excepciones.filter((e) => e !== actual)
  return { id: actual.id }
}

const extraDesde = (id, d) => ({
  id,
  tipo: 'EXTRA',
  fecha: d.fecha,
  motivo: String(d.motivo ?? '').trim() || null,
  idCategoria: idCategoriaDe(d.categoria),
  categoria: d.categoria,
  horaInicio: d.hora,
  horaFin: d.horaFin,
  sede: d.sede,
  idEntrenador: idNumerico(d.idEntrenador),
  entrenador: nombreEntrenador(d.idEntrenador),
  grupo: d.grupo || null,
})

// ¿El entrenador ya tiene otra sesión que se cruza ese día? (cuenta franjas no canceladas y extras de esa semana)
export async function detectarConflicto(datos, ignorarId = null) {
  if (usarApi) return { hayConflicto: false }
  const sesiones = await listarSemana(lunesDe(datos.fecha))
  const hayConflicto = chocaEntrenadorEnSesiones(
    { idEntrenador: datos.idEntrenador, fecha: datos.fecha, hora: datos.hora, horaFin: datos.horaFin },
    sesiones,
    ignorarId === null ? null : `x-${ignorarId}`,
  )
  return { hayConflicto }
}

async function validarYComprobar(datos, ignorarId) {
  const entrenadores = await listarEntrenadores()
  const errores = validarExtra(datos, { categorias: NOMBRES_CATEGORIAS, entrenadores, hoy: hoy() })
  if (Object.keys(errores).length > 0) throw error('VALIDACION', 'Hay datos que corregir.', { errores })
  if ((await detectarConflicto(datos, ignorarId)).hayConflicto) {
    throw error('CONFLICTO', 'Ese entrenador ya tiene otra sesión que se cruza con este horario.', { errores: { idEntrenador: 'Ese entrenador ya tiene otra sesión que se cruza con este horario.' } })
  }
}

export async function crearExtra(datos) {
  if (usarApi) throw noDisponible()
  await validarYComprobar(datos, null)
  const nuevo = extraDesde(siguienteId++, datos)
  excepciones = [...excepciones, nuevo]
  return nuevo
}

export async function editarExtra(id, datos) {
  if (usarApi) throw noDisponible()
  const actual = excepciones.find((e) => e.tipo === 'EXTRA' && String(e.id) === String(id))
  if (!actual) throw error('NO_EXISTE', 'El entrenamiento extra no existe.')
  await validarYComprobar(datos, actual.id)
  const nuevo = extraDesde(actual.id, datos)
  excepciones = excepciones.map((e) => (e === actual ? nuevo : e))
  return nuevo
}

export async function eliminarExtra(id) {
  if (usarApi) throw noDisponible()
  const actual = excepciones.find((e) => e.tipo === 'EXTRA' && String(e.id) === String(id))
  if (!actual) throw error('NO_EXISTE', 'El entrenamiento extra no existe.')
  excepciones = excepciones.filter((e) => e !== actual)
  return { id: actual.id }
}

// Rotar: misma franja, otro entrenador, para TODAS las semanas. El PUT reemplaza todos los campos: se mandan los siete tal como
// están y solo cambia idEntrenador ('' o null = por turnos). El 409 de cruce llega con el mensaje del backend.
export async function cambiarEntrenadorDeFranja(idFranja, idEntrenador) {
  const franja = (await horarioClub.listar()).find((f) => String(f.id) === String(idFranja))
  if (!franja) throw error('NO_EXISTE', 'La franja no existe.', { estado: 404, mensaje: 'La franja no existe.' })
  return horarioClub.editar(franja.id, {
    idCategoria: franja.idCategoria,
    dia: franja.dia,
    horaInicio: franja.horaInicio,
    horaFin: franja.horaFin,
    sede: franja.sede,
    idEntrenador: idEntrenador ?? '',
    grupo: franja.grupo ?? '',
  })
}
