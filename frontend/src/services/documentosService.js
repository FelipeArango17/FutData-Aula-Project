// Documentos del deportista (los 7 tipos del club, en utils/documentosClub.js; la foto tipo documento es la foto de perfil y no se sube aquí). Por ahora en MEMORIA: no se sube ningún archivo a ningún
// servidor; solo se guardan los datos del archivo (nombre, tipo y tamaño). Las funciones son async y lanzan errores con `codigo`,
// como deportistasService, para cambiarlas luego por Supabase Storage + endpoints del backend sin tocar las pantallas.
//
// Forma de datos (tabla `documento` del backend):
//   Documento = { id, idDeportista, tipo, nombreOriginal, tipoContenido, tamanoBytes, estado, comentario, fechaVencimiento,
//                 cargadoPor, cargadoPorCorreo, cargadoEn, revisadoPor, revisadoEn }
//     tipo              uno de IDS_TIPOS_DOCUMENTO (utils/documentosClub.js): 'DOCUMENTO_IDENTIDAD' | 'CERTIFICADO_ESTUDIO' |
//                       'FORMULARIO_INSCRIPCION' | 'AUTORIZACION_HABEAS_DATA' | 'PAZ_Y_SALVO' | 'CARTA_RENUNCIA' | 'TRASPASO_NACIONAL'
//     tipoContenido     application/pdf, image/jpeg o image/png; tamanoBytes de 1 byte a 5 MB (utils/archivos.js)
//     estado            'PENDIENTE' | 'APROBADO' | 'RECHAZADO'
//     comentario        del administrador (obligatorio al rechazar); null si no hay
//     fechaVencimiento  "AAAA-MM-DD" o null (opcional; si viene no puede ser pasada)
//     cargadoPor        quién lo subió: 'Administrador' | 'Acudiente' (CARGADO_POR). Cualquiera de los dos puede subir cualquiera de los 7 tipos:
//                       si el acudiente no puede, lo sube el administrador, y al revés. Aprobar o rechazar sigue siendo solo del administrador.
//     cargadoPorCorreo/revisadoPor  correo de quien subió / revisó; cargadoEn/revisadoEn: fecha y hora ISO (revisado* es null si está pendiente)
//
// Errores: Error con `codigo` -> 'SIN_PERMISO' | 'ARCHIVO_INVALIDO' (solo falla el archivo; el mensaje dice el motivo y `errores`
//   trae { archivo }) | 'VALIDACION' (con `errores`: { campo: mensaje }; si falla el archivo junto con otro campo) |
//   'DOCUMENTO_NO_EXISTE'.
//
// MODO API (módulo "documentos" en VITE_API_MODULOS; bloque 3 del backend). Los ids son UUID (texto). Rutas: ver "Modo API" al final.
//   listarDocumentos(id)        -> { documentos, estadoInscripcion, documentosFaltantes } (en AMBOS modos)
//   subirDocumento(id, datos)   -> el documento creado (201)
//   verDocumento(idDocumento)   -> { url, vigenciaSegundos }: URL firmada de 5 minutos; pídela SOLO al tocar "Ver documento", no la guardes
//   aprobarDocumento(id)        -> { documento, estadoInscripcion }
//   rechazarDocumento(id, { comentario }) -> { documento, estadoInscripcion, avisoEnviado } (true: correo enviado; false: falló el correo;
//                                  null: el deportista no tiene acudientes con correo)
// Con la API los errores traen `codigo` y un `message` en español: 'VALIDACION' (400) | 'SIN_PERMISO' (403) | 'NO_ENCONTRADO' (404) |
// 'DOCUMENTO_ACTIVO' (409) | 'ERROR_DE_RED'. En memoria los de siempre (abajo). "Ver documento" solo existe con la API: los documentos de
// ejemplo no tienen archivo, y no se ofrece en la pantalla.
// El administrador sube y revisa documentos; el acudiente solo sube (y solo los de SUS deportistas). El modo en memoria lo simula; con la API lo
// valida el backend con el JWT.
import { TIPOS_DOCUMENTO, validarArchivo } from '../utils/archivos.js'
import { esFechaValida, hoyLocal } from '../utils/fechas.js'
import { IDS_TIPOS_DOCUMENTO, NOMBRES_TIPO_DOCUMENTO, calcularInscripcion, masReciente } from '../utils/documentosClub.js'
import { adaptarListaDocumentos, errorDeDocumentos } from '../utils/documentosApi.js'
import { apiRequest, subirArchivo } from './apiClient.js'
import { usarApiDe } from './usarApi.js'
import { esAcudienteDe, listarDeportistas } from './deportistasService.js'
import { obtenerFoto } from './fotosService.js'

// Con "documentos" en VITE_API_MODULOS las funciones hablan con el backend; sin él (y siempre en las pruebas) siguen en memoria.
// No se mezclan: los ids reales son UUID (texto) y los de ejemplo son números.
const usarApi = usarApiDe('documentos')
export const documentosPorApi = () => usarApi

// Los tipos y sus nombres cortos viven en utils/documentosClub.js; aquí solo se reexportan para las pantallas y el servicio.
export const TIPOS_DOCUMENTO_JUGADOR = IDS_TIPOS_DOCUMENTO
export const ETIQUETAS_TIPO_DOCUMENTO_JUGADOR = NOMBRES_TIPO_DOCUMENTO
export const ESTADOS_DOCUMENTO = ['PENDIENTE', 'APROBADO', 'RECHAZADO']
export const ETIQUETAS_ESTADO_DOCUMENTO = { PENDIENTE: 'Pendiente', APROBADO: 'Aprobado', RECHAZADO: 'Rechazado' }
export const COMENTARIO_MAXIMO = 300
export const CARGADO_POR = { administrador: 'Administrador', acudiente: 'Acudiente' }

const ADMIN_DEMO = 'administrador@pradofc.co'
const d = (id, idDeportista, tipo, nombreOriginal, tipoContenido, tamanoBytes, estado, extra = {}) => ({
  id, idDeportista, tipo, nombreOriginal, tipoContenido, tamanoBytes, estado, comentario: null, fechaVencimiento: null,
  cargadoPor: CARGADO_POR.administrador, cargadoPorCorreo: ADMIN_DEMO, cargadoEn: '2026-10-05T14:30:00.000Z', revisadoPor: null, revisadoEn: null, ...extra,
})

// Archivos ficticios (no existen). Santiago (1): identidad aprobada, certificado pendiente y formulario aprobado;
// Valeria (2): identidad rechazada y Habeas Data pendiente; Mateo (6): los 4 obligatorios aprobados (identidad, certificado,
// formulario y Habeas Data) y un paz y salvo pendiente. Los demás no tienen documentos (estado vacío).
// La foto tipo documento no está aquí: es la foto de perfil (fotosService).
let documentos = [
  d(1, 1, 'DOCUMENTO_IDENTIDAD', 'registro-civil-ejemplo.pdf', 'application/pdf', 245 * 1024, 'APROBADO', { revisadoPor: ADMIN_DEMO, revisadoEn: '2026-10-06T16:00:00.000Z' }),
  d(2, 1, 'CERTIFICADO_ESTUDIO', 'certificado-estudio-ejemplo.jpg', 'image/jpeg', Math.round(1.8 * 1024 * 1024), 'PENDIENTE', { cargadoEn: '2026-10-20T15:10:00.000Z', cargadoPor: CARGADO_POR.acudiente, cargadoPorCorreo: 'acudiente@pradofc.co' }),
  d(3, 2, 'DOCUMENTO_IDENTIDAD', 'documento-ejemplo-borroso.png', 'image/png', 890 * 1024, 'RECHAZADO', {
    comentario: 'La imagen está borrosa; sube una foto más clara.', revisadoPor: ADMIN_DEMO, revisadoEn: '2026-10-08T13:45:00.000Z',
  }),
  d(4, 6, 'DOCUMENTO_IDENTIDAD', 'tarjeta-identidad-mateo-ejemplo.pdf', 'application/pdf', 512 * 1024, 'APROBADO', { revisadoPor: ADMIN_DEMO, revisadoEn: '2026-09-30T17:20:00.000Z' }),
  d(5, 1, 'FORMULARIO_INSCRIPCION', 'formulario-inscripcion-ejemplo.pdf', 'application/pdf', 410 * 1024, 'APROBADO', { revisadoPor: ADMIN_DEMO, revisadoEn: '2026-10-06T16:10:00.000Z' }),
  d(6, 2, 'AUTORIZACION_HABEAS_DATA', 'habeas-data-ejemplo.pdf', 'application/pdf', 150 * 1024, 'PENDIENTE', { cargadoEn: '2026-10-21T13:00:00.000Z', cargadoPor: CARGADO_POR.acudiente, cargadoPorCorreo: 'acudiente@pradofc.co' }),
  d(7, 6, 'CERTIFICADO_ESTUDIO', 'certificado-estudio-mateo-ejemplo.pdf', 'application/pdf', 600 * 1024, 'APROBADO', { revisadoPor: ADMIN_DEMO, revisadoEn: '2026-09-30T17:30:00.000Z' }),
  d(8, 6, 'FORMULARIO_INSCRIPCION', 'formulario-inscripcion-mateo-ejemplo.pdf', 'application/pdf', 380 * 1024, 'APROBADO', { revisadoPor: ADMIN_DEMO, revisadoEn: '2026-09-30T17:35:00.000Z' }),
  d(9, 6, 'AUTORIZACION_HABEAS_DATA', 'habeas-data-mateo-ejemplo.pdf', 'application/pdf', 140 * 1024, 'APROBADO', { revisadoPor: ADMIN_DEMO, revisadoEn: '2026-09-30T17:40:00.000Z' }),
  d(10, 6, 'PAZ_Y_SALVO', 'paz-y-salvo-mateo-ejemplo.pdf', 'application/pdf', 200 * 1024, 'PENDIENTE', { cargadoEn: '2026-10-21T10:00:00.000Z' }),
]
let siguienteId = documentos.length + 1

const error = (codigo, mensaje) => Object.assign(new Error(mensaje), { codigo })
const simularRed = () => new Promise((resolve) => setTimeout(resolve, 250))
const copiar = (x) => ({ ...x })

function exigirAdministrador(actor) {
  if (actor?.rol !== 'administrador') throw error('SIN_PERMISO', 'No tienes permiso para revisar documentos.')
}

// Sube el administrador (cualquier deportista) o el acudiente (solo los suyos). Devuelve el texto de cargadoPor.
function quienSube(actor, idDeportista) {
  if (actor?.rol === 'administrador') return CARGADO_POR.administrador
  if (actor?.rol === 'acudiente' && esAcudienteDe(actor.correo, idDeportista)) return CARGADO_POR.acudiente
  throw error('SIN_PERMISO', 'No tienes permiso para subir documentos de este deportista.')
}

// Reglas de los datos al subir (las usan el formulario y el servicio). `archivo` es un File o { name, type, size }.
// Devuelve { campo: mensaje } solo con los campos con error.
export function validarDatosDocumento({ tipo, archivo, fechaVencimiento }, hoy = hoyLocal(), tiposAceptados = IDS_TIPOS_DOCUMENTO) {
  const errores = {}
  if (!tiposAceptados.includes(tipo)) errores.tipo = 'Selecciona qué tipo de documento es.'
  const motivo = validarArchivo(archivo, { tipos: TIPOS_DOCUMENTO })
  if (motivo) errores.archivo = motivo
  const vence = String(fechaVencimiento ?? '').trim()
  if (vence) {
    if (!/^\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$/.test(vence) || !esFechaValida(vence)) errores.fechaVencimiento = 'La fecha de vencimiento no es válida.'
    else if (vence < hoy) errores.fechaVencimiento = 'La fecha de vencimiento no puede ser pasada.'
  }
  return errores
}

// { documentos (del más reciente al más antiguo), estadoInscripcion ('COMPLETA' | 'PENDIENTE'), documentosFaltantes (tipos obligatorios sin aprobar) }
export async function listarDocumentos(idDeportista) {
  if (usarApi) return adaptarListaDocumentos(await llamar(() => apiRequest(rutaDocumentos(idDeportista))))
  await simularRed()
  const delDeportista = documentos.filter((x) => x.idDeportista === Number(idDeportista))
  return { documentos: [...delDeportista].sort(masReciente).map(copiar), ...calcularInscripcion(delDeportista) }
}

// Los jugadores cuyos documentos ve el acudiente: [{ id, nombre, tieneFoto }]. Con la API salen de GET /api/acudientes/me/deportistas (solo
// los suyos; ya trae la URL de la foto). En memoria, los deportistas cuyo correo de acudiente es el del actor.
export async function listarJugadoresDelAcudiente(actor) {
  if (usarApi) {
    const lista = await llamar(() => apiRequest('/acudientes/me/deportistas'), actor)
    return (Array.isArray(lista) ? lista : []).map((d) => ({ id: d.id, nombre: `${d.nombre} ${d.apellido}`, tieneFoto: Boolean(d.foto) }))
  }
  const propios = (await listarDeportistas()).filter((d) => d.acudiente && d.acudiente.toLowerCase() === actor.correo.toLowerCase())
  return Promise.all(propios.map(async (d) => ({ id: d.id, nombre: `${d.nombre} ${d.apellido}`, tieneFoto: Boolean(await obtenerFoto(d.id)) })))
}

// Sube (en memoria) un documento PENDIENTE: el administrador o el acudiente del deportista, cualquiera de los 7 tipos.
export async function subirDocumento(idDeportista, { tipo, archivo, fechaVencimiento } = {}, actor) {
  if (!usarApi) await simularRed()
  const cargadoPor = usarApi ? null : quienSube(actor, idDeportista)
  const errores = validarDatosDocumento({ tipo, archivo, fechaVencimiento })
  const campos = Object.keys(errores)
  if (campos.length === 1 && errores.archivo) throw Object.assign(error('ARCHIVO_INVALIDO', errores.archivo), { errores })
  if (campos.length > 0) throw Object.assign(error('VALIDACION', 'Hay datos que corregir.'), { errores })
  if (usarApi) {
    // multipart: tipo, archivo y fechaVencimiento (opcional, AAAA-MM-DD); el backend valida el contenido del archivo y el 409 de duplicado activo
    return llamar(() => subirArchivo(rutaDocumentos(idDeportista), archivo, { tipo, fechaVencimiento: String(fechaVencimiento ?? '').trim() || null }), actor)
  }
  const nuevo = {
    id: siguienteId++,
    idDeportista: Number(idDeportista),
    tipo,
    nombreOriginal: archivo.name,
    tipoContenido: archivo.type,
    tamanoBytes: archivo.size,
    estado: 'PENDIENTE',
    comentario: null,
    fechaVencimiento: String(fechaVencimiento ?? '').trim() || null,
    cargadoPor,
    cargadoPorCorreo: actor.correo ?? null,
    cargadoEn: new Date().toISOString(),
    revisadoPor: null,
    revisadoEn: null,
  }
  documentos = [...documentos, nuevo]
  return copiar(nuevo)
}

function revisar(idDocumento, { estado, comentario }, actor) {
  exigirAdministrador(actor)
  const actual = documentos.find((x) => x.id === Number(idDocumento))
  if (!actual) throw error('DOCUMENTO_NO_EXISTE', 'El documento no existe.')
  const errores = {}
  const texto = String(comentario ?? '').trim()
  if (estado === 'RECHAZADO' && !texto) errores.comentario = 'Escribe el motivo del rechazo.'
  else if (texto.length > COMENTARIO_MAXIMO) errores.comentario = `El comentario no puede superar los ${COMENTARIO_MAXIMO} caracteres.`
  if (Object.keys(errores).length > 0) throw Object.assign(error('VALIDACION', 'Hay datos que corregir.'), { errores })
  const revisado = { ...actual, estado, comentario: texto || null, revisadoPor: actor.correo ?? null, revisadoEn: new Date().toISOString() }
  documentos = documentos.map((x) => (x.id === actual.id ? revisado : x))
  return { documento: copiar(revisado), estadoInscripcion: calcularInscripcion(documentos.filter((x) => x.idDeportista === actual.idDeportista)).estadoInscripcion }
}

// Aprueba un documento (solo el administrador). Devuelve { documento, estadoInscripcion }.
export async function aprobarDocumento(idDocumento, actor) {
  if (usarApi) return llamar(() => apiRequest(`${rutaDocumento(idDocumento)}/aprobar`, { method: 'POST' }), actor)
  await simularRed()
  return revisar(idDocumento, { estado: 'APROBADO' }, actor)
}

// Rechaza un documento con un comentario obligatorio de 1 a COMENTARIO_MAXIMO caracteres (solo el administrador).
// Devuelve { documento, estadoInscripcion, avisoEnviado }: true = correo enviado; false = el rechazo quedó pero el correo falló; null = el
// deportista no tiene acudientes con correo. En memoria no se envía nada: avisoEnviado es null.
export async function rechazarDocumento(idDocumento, { comentario } = {}, actor) {
  if (usarApi) {
    const texto = String(comentario ?? '').trim()
    if (!texto || texto.length > COMENTARIO_MAXIMO) {
      throw Object.assign(error('VALIDACION', 'Hay datos que corregir.'), { errores: { comentario: texto ? `El comentario no puede superar los ${COMENTARIO_MAXIMO} caracteres.` : 'Escribe el motivo del rechazo.' } })
    }
    return llamar(() => apiRequest(`${rutaDocumento(idDocumento)}/rechazar`, { method: 'POST', body: JSON.stringify({ comentario: texto }) }), actor)
  }
  await simularRed()
  return { ...revisar(idDocumento, { estado: 'RECHAZADO', comentario }, actor), avisoEnviado: null }
}

// Pide la URL firmada de un documento (válida 5 minutos): { url, vigenciaSegundos }. Llámala solo cuando el usuario toque "Ver documento".
export async function verDocumento(idDocumento, actor) {
  if (!usarApi) throw error('SIN_ARCHIVO', 'Los documentos de ejemplo no tienen archivo.')
  return llamar(() => apiRequest(`${rutaDocumento(idDocumento)}/descarga`), actor)
}

// ---- Modo API ----
// GET/POST /api/deportistas/{id}/documentos · GET /api/documentos/{id}/descarga · POST /api/documentos/{id}/aprobar|rechazar
// Roles: ADMINISTRADOR y ACUDIENTE dueño suben, listan y descargan; aprobar y rechazar solo el ADMINISTRADOR; entrenador y tesorero 403;
// un acudiente que no es dueño recibe 404.
function rutaDocumentos(idDeportista) {
  return `/deportistas/${encodeURIComponent(idDeportista)}/documentos`
}
function rutaDocumento(idDocumento) {
  return `/documentos/${encodeURIComponent(idDocumento)}`
}
async function llamar(operacion, actor) {
  try {
    return await operacion()
  } catch (e) {
    throw errorDeDocumentos(e, { rol: actor?.rol })
  }
}
