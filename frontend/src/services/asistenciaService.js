// Servicio de asistencia (HU-021 registrar, HU-022 editar). Por ahora trabaja sobre datos de ejemplo EN MEMORIA.
// Las funciones son async y devuelven lo que devolvería una API REST, para cambiarlas luego por
// Axios (GET /entrenamientos?asistencia, GET/POST/PUT /entrenamientos/:id/asistencia) sin tocar las pantallas.
// Los entrenamientos salen de entrenamientosService y los deportistas de deportistasService (no se duplican datos).
//
// Forma de datos propuesta para el frontend:
//   actor     = { rol: 'entrenador' | 'administrador', idEntrenador, nombre }
//   Registro  = { items, registradoPor, registradoEn, historial }
//     items         [{ idDeportista, estado: 'presente' | 'ausente' | 'justificado', observacion }]
//     registradoEn  "AAAA-MM-DDTHH:MM" (hora local, sin zona horaria)
//     historial     [{ fechaHora, responsable, cambios: [{ idDeportista, antes: {estado, observacion}, despues: {estado, observacion} }] }]
//
// Errores: Error con `codigo` -> 'SIN_PERMISO' | 'ENTRENAMIENTO_NO_EXISTE' | 'ENTRENAMIENTO_FUTURO' | 'YA_REGISTRADA' |
//   'NO_REGISTRADA' | 'FALTAN_ESTADOS' (con `faltan`: ids) | 'ESTADO_INVALIDO' | 'OBSERVACION_LARGA' |
//   'DEPORTISTA_NO_PERTENECE' | 'SIN_CAMBIOS' | 'ERROR_DE_RED'.
//
// PENDIENTE: confirmar con Juan / el modelo de datos (docs/info-sprints/sprint-1/diccionario-datos.md, sección 2.8):
// - D5: la asistencia se registra POR ENTRENAMIENTO. El diccionario guarda una fila por deportista y entrenamiento
//   (`Asistencia.id_entrenamiento`); aquí se agrupan en un Registro por entrenamiento para facilitar la pantalla.
// - El diccionario guarda `presente` (boolean). Faltan el estado "justificado", el límite de 200 caracteres de la
//   observación (hoy `observaciones` es text), la fecha y hora del registro y el historial de cambios (HU-022).
//   Propuesta: `estado` varchar y una tabla `AsistenciaCambio`.
// - Solo se listan deportistas ACTIVOS del grupo; el historial nunca se borra.
// - RF008: los permisos se validan aquí solo como simulación. En producción los valida el backend con el JWT.
// - "Hoy" es HOY_EJEMPLO. La fecha de los registros nuevos usa ese día con la hora real, para que la demo sea coherente.
import { ENTRENADORES, HOY_EJEMPLO, entrenamientosDeEjemplo, listar as listarEntrenamientos } from './entrenamientosService.js'
import { obtener as obtenerEntrenador } from './entrenadoresService.js'
import { idsDeEjemploDeCategoria, listarCategorias, listarDeportistas } from './deportistasService.js'

export const ESTADOS = ['presente', 'ausente', 'justificado']
export const MAX_OBSERVACION = 200

// Registros de ejemplo. El entrenamiento de Sub-12 del martes 20 de octubre (de Nicolás) ya está registrado y
// tiene un cambio en el historial, para probar la edición sin empezar de cero. Los demás de la semana hasta hoy (22 de
// octubre) están sin registrar; los del viernes 23 y el sábado 24 son futuros. Los ids salen de la plantilla del club.
const ID_REGISTRADO = entrenamientosDeEjemplo().find((e) => e.categoria === 'Sub-12' && e.fecha === '2026-10-20').id
// Los deportistas del registro sembrado son los de ejemplo de Sub-12 (sin ids fijos): el 1.º, 2.º y 5.º presentes, el 3.º ausente
// sin aviso y el 4.º justificado (cita médica).
const ID_DEPORTISTAS_REGISTRADO = idsDeEjemploDeCategoria('Sub-12')
const ESTADOS_SEMBRADOS = [['presente', ''], ['presente', ''], ['ausente', 'Sin aviso'], ['justificado', 'Cita médica'], ['presente', '']]
const sembrar = () =>
  new Map([
    [
      ID_REGISTRADO,
      {
        items: ID_DEPORTISTAS_REGISTRADO.map((idDeportista, i) => ({ idDeportista, estado: ESTADOS_SEMBRADOS[i % 5][0], observacion: ESTADOS_SEMBRADOS[i % 5][1] })),
        registradoPor: 'Nicolás',
        registradoEn: '2026-10-20T19:05',
        historial: [
          {
            fechaHora: '2026-10-20T20:10',
            responsable: 'Nicolás',
            cambios: [
              {
                idDeportista: ID_DEPORTISTAS_REGISTRADO[3],
                antes: { estado: 'ausente', observacion: '' },
                despues: { estado: 'justificado', observacion: 'Cita médica' },
              },
            ],
          },
        ],
      },
    ],
  ])
let registros = sembrar()

// Simulación de red y de reloj: se pueden cambiar (pruebas y verificación manual de errores).
const ahoraPorDefecto = () => {
  const real = new Date()
  const [a, m, d] = HOY_EJEMPLO.split('-').map(Number)
  return new Date(a, m - 1, d, real.getHours(), real.getMinutes(), real.getSeconds())
}
let retardoMs = 200
let ahora = ahoraPorDefecto
let fallosPendientes = 0
export function configurarSimulacion({ retardo, fallos, ahora: nuevoAhora } = {}) {
  if (retardo !== undefined) retardoMs = retardo
  if (fallos !== undefined) fallosPendientes = fallos
  if (nuevoAhora !== undefined) ahora = nuevoAhora ?? ahoraPorDefecto
}

const error = (codigo, mensaje, extra) => Object.assign(new Error(mensaje), { codigo }, extra)

async function simularRed() {
  await new Promise((resolve) => setTimeout(resolve, retardoMs))
  if (fallosPendientes > 0) {
    fallosPendientes -= 1
    throw error('ERROR_DE_RED', 'No se pudo conectar con el servidor.')
  }
}

const dos = (n) => String(n).padStart(2, '0')
const marcaDeTiempo = () => {
  const f = ahora()
  return `${f.getFullYear()}-${dos(f.getMonth() + 1)}-${dos(f.getDate())}T${dos(f.getHours())}:${dos(f.getMinutes())}`
}

const esFuturo = (entrenamiento) => entrenamiento.fecha > HOY_EJEMPLO
const copia = (x) => structuredClone(x)
const observacionLimpia = (texto) => String(texto ?? '').trim()

function responsableDe(actor) {
  if (actor.nombre) return actor.nombre
  if (actor.rol === 'administrador') return 'Administrador'
  return ENTRENADORES.find((e) => e.id === Number(actor.idEntrenador))?.nombre ?? 'Entrenador'
}

// Categorías que el actor puede gestionar: null = todas (administrador)
async function categoriasPermitidas(actor) {
  if (actor?.rol === 'administrador') return null
  if (actor?.rol === 'entrenador') {
    const entrenador = await obtenerEntrenador(actor.idEntrenador)
    if (entrenador) return entrenador.categorias
  }
  throw error('SIN_PERMISO', 'No tienes permiso para gestionar la asistencia.')
}

async function cargarEntrenamiento(idEntrenamiento, actor) {
  const encontrado = (await listarEntrenamientos()).find((e) => e.id === Number(idEntrenamiento))
  if (!encontrado) throw error('ENTRENAMIENTO_NO_EXISTE', 'El entrenamiento no existe.')
  const permitidas = await categoriasPermitidas(actor)
  if (permitidas && !permitidas.includes(encontrado.categoria)) {
    throw error('SIN_PERMISO', 'No tienes permiso para gestionar la asistencia de este grupo.')
  }
  return encontrado
}

// Todos los deportistas del grupo del entrenamiento (activos o no)
async function deportistasDelGrupo(entrenamiento) {
  const categoria = (await listarCategorias()).find((c) => c.nombre === entrenamiento.categoria)
  return categoria ? listarDeportistas({ idCategoria: categoria.id }) : []
}

const datosEntrenamiento = (e) => ({
  ...e,
  futuro: esFuturo(e),
  registrada: registros.has(e.id),
  entrenador: ENTRENADORES.find((x) => x.id === e.idEntrenador)?.nombre ?? 'Por turnos',
})

// Entrenamientos que el actor puede gestionar, ya ocurridos o de hoy. Con incluirFuturos también los próximos
// (la pantalla los muestra con el aviso "aún no ocurre"). fecha: solo los de ese día.
export async function listarEntrenamientosParaAsistencia({ actor, fecha, incluirFuturos = false } = {}) {
  await simularRed()
  const permitidas = await categoriasPermitidas(actor)
  return (await listarEntrenamientos())
    .filter((e) => !permitidas || permitidas.includes(e.categoria))
    .filter((e) => incluirFuturos || !esFuturo(e))
    .filter((e) => !fecha || e.fecha === fecha)
    .map(datosEntrenamiento)
}

export async function obtenerAsistencia(idEntrenamiento, actor) {
  await simularRed()
  const entrenamiento = await cargarEntrenamiento(idEntrenamiento, actor)
  const registro = registros.get(entrenamiento.id) ?? null
  const idsRegistrados = new Set(registro?.items.map((i) => i.idDeportista))
  const deportistas = (await deportistasDelGrupo(entrenamiento)).filter((d) => d.activo || idsRegistrados.has(d.id))
  return {
    entrenamiento: datosEntrenamiento(entrenamiento),
    deportistas,
    registro: registro ? copia(registro) : null,
    puedeEditar: true, // si llegó hasta aquí, el actor tiene permiso sobre el grupo
  }
}

function validarObservacion(texto) {
  const limpia = observacionLimpia(texto)
  if (limpia.length > MAX_OBSERVACION) {
    throw error('OBSERVACION_LARGA', `La observación no puede superar ${MAX_OBSERVACION} caracteres.`)
  }
  return limpia
}

function validarEstado(estado) {
  if (!ESTADOS.includes(estado)) throw error('ESTADO_INVALIDO', 'El estado de asistencia no es válido.')
}

// items: [{ idDeportista, estado, observacion? }] con un estado para CADA deportista activo del grupo.
export async function registrarAsistencia(idEntrenamiento, items, actor) {
  await simularRed()
  const entrenamiento = await cargarEntrenamiento(idEntrenamiento, actor)
  if (esFuturo(entrenamiento)) throw error('ENTRENAMIENTO_FUTURO', 'Este entrenamiento aún no ocurre.')
  if (registros.has(entrenamiento.id)) throw error('YA_REGISTRADA', 'La asistencia de este entrenamiento ya fue registrada. Usa editar.')

  const activos = (await deportistasDelGrupo(entrenamiento)).filter((d) => d.activo)
  const idsActivos = new Set(activos.map((d) => d.id))
  const recibidos = new Map()
  for (const item of items ?? []) {
    const id = Number(item.idDeportista)
    if (!idsActivos.has(id)) throw error('DEPORTISTA_NO_PERTENECE', 'El deportista no pertenece a este grupo.', { idDeportista: id })
    if (item.estado) {
      validarEstado(item.estado)
      recibidos.set(id, { idDeportista: id, estado: item.estado, observacion: validarObservacion(item.observacion) })
    }
  }
  const faltan = activos.filter((d) => !recibidos.has(d.id)).map((d) => d.id)
  if (faltan.length > 0) throw error('FALTAN_ESTADOS', 'Falta marcar la asistencia de algunos deportistas.', { faltan })

  const registro = {
    items: activos.map((d) => recibidos.get(d.id)),
    registradoPor: responsableDe(actor),
    registradoEn: marcaDeTiempo(),
    historial: [],
  }
  registros.set(entrenamiento.id, registro)
  return copia(registro)
}

// cambios: [{ idDeportista, estado?, observacion? }]. Solo cuentan los cambios reales; cada edición queda en el historial.
export async function editarAsistencia(idEntrenamiento, cambios, actor) {
  await simularRed()
  const entrenamiento = await cargarEntrenamiento(idEntrenamiento, actor)
  const registro = registros.get(entrenamiento.id)
  if (!registro) throw error('NO_REGISTRADA', 'La asistencia de este entrenamiento todavía no se ha registrado.')

  const nuevosItems = registro.items.map((i) => ({ ...i }))
  const detalle = []
  for (const cambio of cambios ?? []) {
    const actual = nuevosItems.find((i) => i.idDeportista === Number(cambio.idDeportista))
    if (!actual) throw error('DEPORTISTA_NO_PERTENECE', 'El deportista no pertenece a este registro.', { idDeportista: Number(cambio.idDeportista) })
    const antes = { estado: actual.estado, observacion: actual.observacion }
    if (cambio.estado !== undefined) {
      validarEstado(cambio.estado)
      actual.estado = cambio.estado
    }
    if (cambio.observacion !== undefined) actual.observacion = validarObservacion(cambio.observacion)
    const despues = { estado: actual.estado, observacion: actual.observacion }
    if (antes.estado !== despues.estado || antes.observacion !== despues.observacion) {
      detalle.push({ idDeportista: actual.idDeportista, antes, despues })
    }
  }
  if (detalle.length === 0) throw error('SIN_CAMBIOS', 'No hiciste ningún cambio.')

  const actualizado = {
    ...registro,
    items: nuevosItems,
    historial: [...registro.historial, { fechaHora: marcaDeTiempo(), responsable: responsableDe(actor), cambios: detalle }],
  }
  registros.set(entrenamiento.id, actualizado)
  return copia(actualizado)
}
