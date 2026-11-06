// Lógica pura de la pantalla "Horarios": la semana del club (lunes a sábado) como lista de sesiones. Sin estado ni red.
//
// Las franjas son FIJAS todo el año; sobre ellas hay excepciones por fecha:
//   Excepcion = { id, tipo: 'CANCELACION' | 'EXTRA', fecha "AAAA-MM-DD", motivo | null,
//                 idFranja (solo CANCELACION),
//                 idCategoria, categoria, horaInicio, horaFin, sede, idEntrenador | null, entrenador | null, grupo | null (solo EXTRA) }
// Sesion = { clave, tipo: 'FRANJA' | 'EXTRA', idFranja | null, idExcepcion | null, estado: 'PROGRAMADO' | 'CANCELADO', motivo | null,
//            fecha, hora, horaFin, idCategoria, categoria, sede, idEntrenador | null, entrenador | null, grupo | null }
// Los ids pueden ser numéricos (datos de ejemplo) o UUID (API): siempre se comparan como texto.
import { NOMBRES_CATEGORIAS } from './categorias.js'
import { diaCorto, diasDeSemana, esDomingo, lunesDe, mesEnTexto, sumarDias } from './fechas.js'
import { chocaEntrenador } from './horarioClub.js'
import { MENSAJES, enLista, maximoCaracteres, validarCampos, validarFechaEntrenamiento, validarHoraEntrenamiento, validarHoraFinEntrenamiento } from './validaciones.js'

export const MAX_MOTIVO = 200
export const MENSAJE_MOTIVO = `El motivo puede tener como máximo ${MAX_MOTIVO} caracteres.`
export const SEDES_CLUB = ['Horizontes', 'UVA El Paraíso']

const ordenCategoria = (nombre) => {
  const i = NOMBRES_CATEGORIAS.indexOf(nombre)
  return i >= 0 ? i : NOMBRES_CATEGORIAS.length
}

// Lunes de la semana a mostrar. El club no entrena los domingos: ese día ya se muestra la semana que empieza mañana.
export const semanaEnCurso = (hoy) => (esDomingo(hoy) ? sumarDias(lunesDe(hoy), 7) : lunesDe(hoy))

// "Lun 2 nov"
export const fechaConMes = (fecha) => `${diaCorto(fecha)} ${mesEnTexto(fecha).slice(0, 3)}`

// Lo que se le dice al usuario al cancelar o crear un extra. El correo lo manda el backend; la pantalla no cuenta notificados.
export const textoAviso = (categoria) => `Se avisará por correo a los acudientes de ${categoria}.`

// Mensaje tras una acción que avisa a los acudientes. `avisoEnviado` es el de la API: true (salió el correo), false (el cambio SÍ quedó
// hecho pero no se pudo avisar: no es un fallo, pero se dice) o null (la categoría no tiene acudientes con correo). undefined = modo
// ejemplo (en memoria): `textoEjemplo` es lo que se añade entonces. Devuelve { texto, tono }.
export function avisoDeAccion(texto, avisoEnviado, categoria, textoEjemplo = '') {
  if (avisoEnviado === true) return { texto: `${texto} Se envió el aviso por correo a los acudientes de ${categoria}.`, tono: 'success' }
  if (avisoEnviado === false) return { texto: `${texto} No se pudo avisar por correo a los acudientes de ${categoria}: avísales por otro medio.`, tono: 'warning' }
  if (avisoEnviado === null) return { texto: `${texto} ${categoria} no tiene acudientes con correo, así que no se envió ningún aviso.`, tono: 'success' }
  return { texto: `${texto} ${textoEjemplo}`.trim(), tono: 'success' }
}

const porSesion = (a, b) =>
  a.fecha.localeCompare(b.fecha) || a.hora.localeCompare(b.hora) || ordenCategoria(a.categoria) - ordenCategoria(b.categoria) || (a.grupo ?? '').localeCompare(b.grupo ?? '')

// Las sesiones de lunes a sábado de la semana que empieza en `lunes`: cada franja en su fecha (cancelada si hay una cancelación
// de esa franja ese día) más los extras de esa semana. Ordenadas por fecha, hora y categoría.
export function sesionesDeSemana(franjas, excepciones, lunes) {
  const dias = diasDeSemana(lunes)
  const cancelaciones = new Map(
    excepciones.filter((e) => e.tipo === 'CANCELACION').map((e) => [`${e.idFranja}|${e.fecha}`, e]),
  )
  const deFranjas = franjas
    .filter((f) => f.dia >= 1 && f.dia <= 6)
    .map((f) => {
      const fecha = dias[f.dia - 1]
      const cancelacion = cancelaciones.get(`${f.id}|${fecha}`)
      return {
        clave: `f-${f.id}-${fecha}`,
        tipo: 'FRANJA',
        idFranja: f.id,
        idExcepcion: cancelacion?.id ?? null,
        estado: cancelacion ? 'CANCELADO' : 'PROGRAMADO',
        motivo: cancelacion?.motivo ?? null,
        fecha,
        hora: f.horaInicio,
        horaFin: f.horaFin,
        idCategoria: f.idCategoria,
        categoria: f.categoria,
        sede: f.sede,
        idEntrenador: f.idEntrenador ?? null,
        entrenador: f.entrenador ?? null,
        grupo: f.grupo ?? null,
      }
    })
  const extras = excepciones
    .filter((e) => e.tipo === 'EXTRA' && dias.includes(e.fecha))
    .map((e) => ({
      clave: `x-${e.id}`,
      tipo: 'EXTRA',
      idFranja: null,
      idExcepcion: e.id,
      estado: 'PROGRAMADO',
      motivo: e.motivo ?? null,
      fecha: e.fecha,
      hora: e.horaInicio,
      horaFin: e.horaFin,
      idCategoria: e.idCategoria,
      categoria: e.categoria,
      sede: e.sede,
      idEntrenador: e.idEntrenador ?? null,
      entrenador: e.entrenador ?? null,
      grupo: e.grupo ?? null,
    }))
  return [...deFranjas, ...extras].sort(porSesion)
}

// ---- Permisos ----
// El administrador actúa sobre cualquier categoría; el entrenador solo sobre las suyas (`propias`: lista de nombres, o null si no se
// pueden identificar: entonces no actúa sobre ninguna); acudiente y tesorero solo ven.
export function puedeActuarSobre(rol, categoria, propias = null) {
  if (rol === 'administrador') return true
  if (rol === 'entrenador') return Array.isArray(propias) && propias.includes(categoria)
  return false
}

// Categorías en las que el rol puede crear extras
export function categoriasCreables(rol, propias, todas = NOMBRES_CATEGORIAS) {
  if (rol === 'administrador') return [...todas]
  if (rol === 'entrenador' && Array.isArray(propias)) return todas.filter((c) => propias.includes(c))
  return []
}

// Nada se cancela ni se crea en fechas pasadas (hoy sí)
export const esFechaModificable = (fecha, hoy) => fecha >= hoy

export const puedeCrearExtra = (rol, capacidades, propias) => Boolean(capacidades?.extras) && categoriasCreables(rol, propias).length > 0

// Qué botones lleva una sesión. `capacidades`: lo que ofrece el servicio (en modo API sin 4B no hay cancelar ni extras).
// En modo API la sesión trae `puedeGestionar` (calculado por el backend): manda sobre la regla local.
export function accionesDe(sesion, { rol, propias = null, capacidades = {}, hoy }) {
  const actua = typeof sesion.puedeGestionar === 'boolean' ? sesion.puedeGestionar : puedeActuarSobre(rol, sesion.categoria, propias)
  const futura = esFechaModificable(sesion.fecha, hoy)
  const esFranja = sesion.tipo === 'FRANJA'
  return {
    cancelar: esFranja && Boolean(capacidades.cancelar) && actua && futura && sesion.estado === 'PROGRAMADO',
    reactivar: esFranja && Boolean(capacidades.cancelar) && actua && futura && sesion.estado === 'CANCELADO' && sesion.idExcepcion !== null,
    // Rotar entrenadores cambia la franja para todas las semanas: solo el administrador
    rotar: esFranja && Boolean(capacidades.rotarEntrenador) && rol === 'administrador',
    editarExtra: !esFranja && Boolean(capacidades.editarExtra ?? capacidades.extras) && actua && futura,
    eliminarExtra: !esFranja && Boolean(capacidades.extras) && actua && futura,
  }
}

// ---- Choque de entrenador ----
// ¿El entrenador de `candidata` ya tiene otra sesión (no cancelada, o un extra) que se cruza ese día? Misma regla que las franjas:
// "por turnos" no choca con nadie. `ignorarClave`: la sesión que se edita.
export function chocaEntrenadorEnSesiones(candidata, sesiones, ignorarClave = null) {
  const existentes = sesiones
    .filter((s) => s.estado !== 'CANCELADO')
    .map((s) => ({ id: s.clave, idEntrenador: s.idEntrenador, fecha: s.fecha, hora: s.hora, horaFin: s.horaFin }))
  return chocaEntrenador(
    { idEntrenador: candidata.idEntrenador, fecha: candidata.fecha, hora: candidata.hora, horaFin: candidata.horaFin },
    existentes,
    ignorarClave,
  )
}

// ---- Validaciones del formulario de extras ----
// valores: { categoria, fecha, hora, horaFin, sede, idEntrenador ('' = por turnos), grupo, motivo }
export function validarExtra(valores, { categorias, entrenadores = [], hoy }) {
  return validarCampos(valores, {
    categoria: [enLista(categorias, MENSAJES.entrenamientoCategoria)],
    idEntrenador: [enLista(['', ...entrenadores.map((e) => String(e.id))], MENSAJES.entrenamientoEntrenador)],
    fecha: [(valor) => validarFechaEntrenamiento(valor, hoy)],
    hora: [validarHoraEntrenamiento],
    horaFin: [(valor, todos) => validarHoraFinEntrenamiento(valor, todos.hora)],
    sede: [enLista(SEDES_CLUB, MENSAJES.entrenamientoSede)],
    motivo: [maximoCaracteres(MAX_MOTIVO, MENSAJE_MOTIVO)],
  })
}

export const validarMotivo = (motivo) => (String(motivo ?? '').trim().length > MAX_MOTIVO ? MENSAJE_MOTIVO : null)
