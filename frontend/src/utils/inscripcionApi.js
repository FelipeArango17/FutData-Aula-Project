// Adaptador de la inscripción única con la API (módulo "inscripcion"): POST /api/inscripciones (backend/README.md, "Inscripción única").
// Sin React ni red, para poder probarlo. Los ids del backend son UUID (texto): nunca se convierten a número ni se muestran.
//
// Cuerpo:   { deportista: {nombre, apellido, tipoDocumento, documento, fechaNacimiento, telefono, direccion, posicion?, grupo?},
//             acudiente: {nombre, apellido, correo, telefono, parentesco} }   (parentesco sin tilde: Tia, Tio)
// Respuesta 201: { deportista, acudiente: {id, nombre, apellido, correo, reutilizado, cuentaCreada}, parentesco, correoEnviado: true|false|null }
// Errores: 400 con `campos` prefijados (deportista.<campo>, acudiente.<campo>, todos juntos) y 409 en deportista.documento o acudiente.correo.
import { adaptarDeportista, cuerpoDeportista } from './deportistasApi.js'

const PARENTESCOS_API = ['Madre', 'Padre', 'Abuelo', 'Abuela', 'Tia', 'Tio', 'Otro']
const PARENTESCO_DESDE_API = { Tia: 'Tía', Tio: 'Tío' }
const sinTilde = (texto) => String(texto ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').trim()

// "Tía" / "Tia" / "tía" -> "Tia". Un valor desconocido se manda tal cual (recortado): el backend responde 400 en acudiente.parentesco.
export function parentescoParaApi(parentesco) {
  const buscado = sinTilde(parentesco).toLowerCase()
  return PARENTESCOS_API.find((p) => p.toLowerCase() === buscado) ?? String(parentesco ?? '').trim()
}

const parentescoDesdeApi = (parentesco) => PARENTESCO_DESDE_API[parentesco] ?? parentesco ?? null

const recortar = (valor) => String(valor ?? '').trim()

// Valores de los pasos 1 y 2 -> cuerpo del POST. La categoría no se envía (se calcula al leer); posición y grupo solo si se escribieron.
export function cuerpoInscripcion(jugador, acudiente) {
  const { posicion, grupo, ...deportista } = cuerpoDeportista(jugador)
  return {
    deportista: { ...deportista, ...(posicion ? { posicion } : {}), ...(grupo ? { grupo } : {}) },
    acudiente: {
      nombre: recortar(acudiente.nombre),
      apellido: recortar(acudiente.apellido),
      correo: recortar(acudiente.correo),
      telefono: recortar(acudiente.telefono),
      parentesco: parentescoParaApi(acudiente.parentesco),
    },
  }
}

// Respuesta 201 -> { deportista (forma de las pantallas), acudiente, parentesco (con tilde, para mostrar), correoEnviado }.
// correoEnviado solo vale true, false o null; cualquier otra cosa se trata como null (no se dice nada del correo).
export function adaptarInscripcion(dto) {
  if (!dto?.deportista?.id || !dto.acudiente) throw new Error('Respuesta de inscripción inválida.')
  const { id, nombre, apellido, correo, reutilizado, cuentaCreada } = dto.acudiente
  return {
    deportista: adaptarDeportista(dto.deportista),
    acudiente: { id, nombre, apellido, correo, reutilizado: reutilizado === true, cuentaCreada: cuentaCreada === true },
    parentesco: parentescoDesdeApi(dto.parentesco),
    correoEnviado: typeof dto.correoEnviado === 'boolean' ? dto.correoEnviado : null,
  }
}

// ---- Avisos del resultado (pantalla de confirmación) ----

export const TEXTO_INSCRIPCION_PENDIENTE = 'El jugador quedó inscrito con estado Pendiente hasta que se aprueben los 4 documentos obligatorios.'

// [{ texto, tono: 'danger' | undefined }] según correoEnviado y acudiente.reutilizado. Con null no se dice nada del correo.
export function avisosDeInscripcion({ acudiente, correoEnviado }) {
  const avisos = []
  const correo = acudiente?.correo
  if (correoEnviado === true) avisos.push({ texto: `Se envió el enlace para crear la contraseña a ${correo}.` })
  if (correoEnviado === false) {
    avisos.push({
      texto: `La inscripción quedó hecha, pero no se pudo enviar el correo con el enlace para crear la contraseña a ${correo}. `
        + 'Avísale al acudiente.',
      tono: 'danger',
    })
  }
  if (acudiente?.reutilizado) {
    const quien = `${acudiente.nombre ?? ''} ${acudiente.apellido ?? ''}`.trim()
    avisos.push({ texto: `${quien || 'El acudiente'} ya existía como acudiente (${correo}); sus datos no se modificaron.` })
  }
  return avisos
}

// ---- Errores ----

export const MENSAJES_ERROR = {
  GENERICO: 'No se pudo inscribir al jugador. Intenta de nuevo; lo que escribiste no se perdió.',
  REVISAR: 'Revisa los datos del jugador y del acudiente e intenta de nuevo.',
  PERMISO: 'No tienes permiso para inscribir jugadores.',
  DOS_PASOS: 'Para inscribir jugadores debes completar la verificación en dos pasos. Cierra sesión, vuelve a entrar e intenta de nuevo.',
  SESION: 'Tu sesión venció. Inicia sesión de nuevo.',
  NO_DISPONIBLE: 'El servicio no está disponible en este momento. No se guardó nada; intenta de nuevo en unos minutos.',
  SIN_CONEXION: 'No pudimos conectar con el servidor. Revisa tu conexión y mira en la lista de deportistas si la inscripción alcanzó a guardarse antes de intentar de nuevo.',
  RESPUESTA: 'No recibimos la confirmación del servidor. Mira en la lista de deportistas si la inscripción quedó hecha antes de intentar de nuevo.',
}

export const MENSAJE_DOCUMENTO_REPETIDO = 'Ya hay un jugador inscrito con este documento.'
export const MENSAJE_CORREO_NO_DISPONIBLE = 'Este correo no se puede usar: pertenece a una cuenta con otro rol o hay otra inscripción en curso con él. Usa otro correo o intenta de nuevo en un momento.'
const MENSAJE_CAMPO = 'Revisa este campo.'

// Campo del backend (sin prefijo) -> campo del formulario, por paso
const CAMPOS = {
  deportista: { paso: 1, formulario: (c) => (c === 'grupo' ? 'grupoEntrenamiento' : c), validos: ['nombre', 'apellido', 'tipoDocumento', 'documento', 'fechaNacimiento', 'telefono', 'direccion', 'posicion', 'grupo'] },
  acudiente: { paso: 2, formulario: (c) => c, validos: ['nombre', 'apellido', 'correo', 'telefono', 'parentesco'] },
}
const DUPLICADOS = { 'deportista.documento': MENSAJE_DOCUMENTO_REPETIDO, 'acudiente.correo': MENSAJE_CORREO_NO_DISPONIBLE }

const escapar = (texto) => texto.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
const capitalizar = (t) => `${t.charAt(0).toUpperCase()}${t.slice(1).replace(/\.$/, '')}.`

// El backend manda `mensaje` como "campo: texto; campo: texto", y algún texto lleva "; " dentro: solo se corta donde sigue un campo de `campos`.
function textosPorCampo(mensaje, campos) {
  const inicio = new RegExp(`(?:^|; )(?=(?:${campos.map(escapar).join('|')}): )`)
  const resultado = {}
  for (const parte of String(mensaje ?? '').split(inicio)) {
    const separador = parte.indexOf(': ')
    if (separador > 0 && campos.includes(parte.slice(0, separador))) resultado[parte.slice(0, separador)] = capitalizar(parte.slice(separador + 2))
  }
  return resultado
}

// Error de apiClient (400/409 con `campos`) -> { porPaso: { 1: {campo: texto}, 2: {...} }, otros: true si algún campo no es del formulario }.
// Los 409 usan un texto propio (el del backend dice el campo, nunca de quién es el dato).
export function erroresDeInscripcionPorPaso(error) {
  const campos = Array.isArray(error?.campos) ? error.campos.filter((c) => typeof c === 'string') : []
  const textos = textosPorCampo(error?.mensaje ?? error?.message, campos)
  const porPaso = {}
  let otros = false
  for (const campo of campos) {
    const [prefijo, ...resto] = campo.split('.')
    const grupo = CAMPOS[prefijo]
    const nombre = resto.join('.')
    if (!grupo || !grupo.validos.includes(nombre)) {
      otros = true
      continue
    }
    const texto = error.estado === 409 && DUPLICADOS[campo] ? DUPLICADOS[campo] : textos[campo] ?? MENSAJE_CAMPO
    porPaso[grupo.paso] = { ...porPaso[grupo.paso], [grupo.formulario(nombre)]: texto }
  }
  return { porPaso, otros }
}

// Error de apiClient -> Error para el asistente (nunca con el texto, el id ni la traza del servidor):
//   con errores de campo: codigo 'VALIDACION' (o 'DOCUMENTO_DUPLICADO' / 'CORREO_NO_DISPONIBLE' en un 409), `paso` (el primero con error),
//     `errores` (los de ese paso) y `erroresPorPaso` (todos);
//   sin campos: `mensajeUsuario` amable y codigo SIN_PERMISO | 2FA_REQUERIDA | SESION | NO_DISPONIBLE | SIN_CONEXION | ERROR.
export function errorDeInscripcion(error) {
  const hacer = (codigo, mensajeUsuario, extra = {}) => Object.assign(new Error(mensajeUsuario), { codigo, mensajeUsuario, estado: error?.estado, ...extra })
  const estado = error?.estado
  if (estado === 400 || estado === 409) {
    const { porPaso, otros } = erroresDeInscripcionPorPaso(error)
    const pasos = Object.keys(porPaso).map(Number).sort()
    if (pasos.length > 0) {
      const codigo = estado === 409 ? (porPaso[1]?.documento ? 'DOCUMENTO_DUPLICADO' : 'CORREO_NO_DISPONIBLE') : 'VALIDACION'
      return hacer(codigo, MENSAJES_ERROR.REVISAR, { paso: pasos[0], errores: porPaso[pasos[0]], erroresPorPaso: porPaso, hayOtros: otros })
    }
    return hacer('ERROR', estado === 400 ? MENSAJES_ERROR.REVISAR : MENSAJES_ERROR.GENERICO)
  }
  if (estado === 403) return error.codigo === '2FA_REQUERIDA' ? hacer('2FA_REQUERIDA', MENSAJES_ERROR.DOS_PASOS) : hacer('SIN_PERMISO', MENSAJES_ERROR.PERMISO)
  if (estado === 401) return hacer('SESION', MENSAJES_ERROR.SESION)
  if (estado === 503) return hacer('NO_DISPONIBLE', MENSAJES_ERROR.NO_DISPONIBLE)
  if (estado === 0 || error?.codigo === 'RED') return hacer('SIN_CONEXION', MENSAJES_ERROR.SIN_CONEXION)
  return hacer('ERROR', MENSAJES_ERROR.GENERICO)
}
