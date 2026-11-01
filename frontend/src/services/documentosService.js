// Documentos del deportista (documento de identidad, ficha médica...). Por ahora en MEMORIA: no se sube ningún archivo a ningún
// servidor; solo se guardan los datos del archivo (nombre, tipo y tamaño). Las funciones son async y lanzan errores con `codigo`,
// como deportistasService, para cambiarlas luego por Supabase Storage + endpoints del backend sin tocar las pantallas.
//
// Forma de datos (tabla `documento` del backend):
//   Documento = { id, idDeportista, tipo, nombreOriginal, tipoContenido, tamanoBytes, estado, comentario, fechaVencimiento,
//                 cargadoPor, cargadoEn, revisadoPor, revisadoEn }
//     tipo              'DOCUMENTO_IDENTIDAD' | 'FICHA_MEDICA' | 'PERMISO' | 'CONTRATO' | 'OTRO'
//     tipoContenido     application/pdf, image/jpeg o image/png; tamanoBytes de 1 byte a 5 MB (utils/archivos.js)
//     estado            'PENDIENTE' | 'APROBADO' | 'RECHAZADO'
//     comentario        del administrador (obligatorio al rechazar); null si no hay
//     fechaVencimiento  "AAAA-MM-DD" o null (opcional; si viene no puede ser pasada)
//     cargadoPor/revisadoPor  correo de quien subió / revisó; cargadoEn/revisadoEn: fecha y hora ISO (revisado* es null si está pendiente)
//
// Errores: Error con `codigo` -> 'SIN_PERMISO' | 'ARCHIVO_INVALIDO' (solo falla el archivo; el mensaje dice el motivo y `errores`
//   trae { archivo }) | 'VALIDACION' (con `errores`: { campo: mensaje }; si falla el archivo junto con otro campo) |
//   'DOCUMENTO_NO_EXISTE'.
//
// PENDIENTE (backend): guardar el archivo en Supabase Storage; "Ver documento" mostrará una URL firmada (hoy la pantalla solo
// muestra el nombre y el tamaño, porque los documentos de ejemplo no tienen archivo). Al rechazar, el backend avisará al acudiente
// por correo (n8n); aquí no se envía nada.
// Solo el administrador sube o revisa documentos (el servicio lo simula; en producción lo valida el backend con el JWT).
import { TIPOS_DOCUMENTO, validarArchivo } from '../utils/archivos.js'
import { esFechaValida, hoyLocal } from '../utils/fechas.js'

export const TIPOS_DOCUMENTO_JUGADOR = ['DOCUMENTO_IDENTIDAD', 'FICHA_MEDICA', 'PERMISO', 'CONTRATO', 'OTRO']
export const ETIQUETAS_TIPO_DOCUMENTO_JUGADOR = {
  DOCUMENTO_IDENTIDAD: 'Documento de identidad', FICHA_MEDICA: 'Ficha médica', PERMISO: 'Permiso', CONTRATO: 'Contrato', OTRO: 'Otro',
}
export const ESTADOS_DOCUMENTO = ['PENDIENTE', 'APROBADO', 'RECHAZADO']
export const ETIQUETAS_ESTADO_DOCUMENTO = { PENDIENTE: 'Pendiente', APROBADO: 'Aprobado', RECHAZADO: 'Rechazado' }
export const COMENTARIO_MAXIMO = 300

const ADMIN_DEMO = 'administrador@pradofc.co'
const d = (id, idDeportista, tipo, nombreOriginal, tipoContenido, tamanoBytes, estado, extra = {}) => ({
  id, idDeportista, tipo, nombreOriginal, tipoContenido, tamanoBytes, estado, comentario: null, fechaVencimiento: null,
  cargadoPor: ADMIN_DEMO, cargadoEn: '2026-10-05T14:30:00.000Z', revisadoPor: null, revisadoEn: null, ...extra,
})

// Archivos ficticios (no existen). Santiago (1): identidad aprobada y ficha médica pendiente; Valeria (2): identidad rechazada;
// Mateo (6): ficha médica aprobada. Los demás no tienen documentos (estado vacío).
let documentos = [
  d(1, 1, 'DOCUMENTO_IDENTIDAD', 'registro-civil-ejemplo.pdf', 'application/pdf', 245 * 1024, 'APROBADO', { revisadoPor: ADMIN_DEMO, revisadoEn: '2026-10-06T16:00:00.000Z' }),
  d(2, 1, 'FICHA_MEDICA', 'ficha-medica-ejemplo.jpg', 'image/jpeg', Math.round(1.8 * 1024 * 1024), 'PENDIENTE', { fechaVencimiento: '2027-03-01', cargadoEn: '2026-10-20T15:10:00.000Z' }),
  d(3, 2, 'DOCUMENTO_IDENTIDAD', 'documento-ejemplo-borroso.png', 'image/png', 890 * 1024, 'RECHAZADO', {
    comentario: 'La imagen está borrosa; sube una foto más clara.', revisadoPor: ADMIN_DEMO, revisadoEn: '2026-10-08T13:45:00.000Z',
  }),
  d(4, 6, 'FICHA_MEDICA', 'ficha-medica-mateo-ejemplo.pdf', 'application/pdf', 512 * 1024, 'APROBADO', {
    fechaVencimiento: '2027-06-30', revisadoPor: ADMIN_DEMO, revisadoEn: '2026-09-30T17:20:00.000Z',
  }),
]
let siguienteId = documentos.length + 1

const error = (codigo, mensaje) => Object.assign(new Error(mensaje), { codigo })
const simularRed = () => new Promise((resolve) => setTimeout(resolve, 250))
const copiar = (x) => ({ ...x })

function exigirAdministrador(actor) {
  if (actor?.rol !== 'administrador') throw error('SIN_PERMISO', 'No tienes permiso para subir ni revisar documentos.')
}

// Reglas de los datos al subir (las usan el formulario y el servicio). `archivo` es un File o { name, type, size }.
// Devuelve { campo: mensaje } solo con los campos con error.
export function validarDatosDocumento({ tipo, archivo, fechaVencimiento }, hoy = hoyLocal()) {
  const errores = {}
  if (!TIPOS_DOCUMENTO_JUGADOR.includes(tipo)) errores.tipo = 'Selecciona qué tipo de documento es.'
  const motivo = validarArchivo(archivo, { tipos: TIPOS_DOCUMENTO })
  if (motivo) errores.archivo = motivo
  const vence = String(fechaVencimiento ?? '').trim()
  if (vence) {
    if (!/^\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$/.test(vence) || !esFechaValida(vence)) errores.fechaVencimiento = 'La fecha de vencimiento no es válida.'
    else if (vence < hoy) errores.fechaVencimiento = 'La fecha de vencimiento no puede ser pasada.'
  }
  return errores
}

// Documentos del deportista, del más reciente al más antiguo
export async function listarDocumentos(idDeportista) {
  await simularRed()
  return documentos
    .filter((x) => x.idDeportista === Number(idDeportista))
    .sort((a, b) => b.cargadoEn.localeCompare(a.cargadoEn) || b.id - a.id)
    .map(copiar)
}

// Sube (en memoria) un documento PENDIENTE. Solo el administrador (por el acudiente cuando este no pueda).
export async function subirDocumento(idDeportista, { tipo, archivo, fechaVencimiento } = {}, actor) {
  await simularRed()
  exigirAdministrador(actor)
  const errores = validarDatosDocumento({ tipo, archivo, fechaVencimiento })
  const campos = Object.keys(errores)
  if (campos.length === 1 && errores.archivo) throw Object.assign(error('ARCHIVO_INVALIDO', errores.archivo), { errores })
  if (campos.length > 0) throw Object.assign(error('VALIDACION', 'Hay datos que corregir.'), { errores })
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
    cargadoPor: actor.correo ?? null,
    cargadoEn: new Date().toISOString(),
    revisadoPor: null,
    revisadoEn: null,
  }
  documentos = [...documentos, nuevo]
  return copiar(nuevo)
}

// Aprueba o rechaza un documento. Rechazar exige un comentario (hasta COMENTARIO_MAXIMO caracteres); al aprobar es opcional.
export async function revisarDocumento(idDocumento, { estado, comentario } = {}, actor) {
  await simularRed()
  exigirAdministrador(actor)
  const actual = documentos.find((x) => x.id === Number(idDocumento))
  if (!actual) throw error('DOCUMENTO_NO_EXISTE', 'El documento no existe.')
  const errores = {}
  const texto = String(comentario ?? '').trim()
  if (estado !== 'APROBADO' && estado !== 'RECHAZADO') errores.estado = 'Elige si se aprueba o se rechaza.'
  if (estado === 'RECHAZADO' && !texto) errores.comentario = 'Escribe el motivo del rechazo.'
  else if (texto.length > COMENTARIO_MAXIMO) errores.comentario = `El comentario no puede superar los ${COMENTARIO_MAXIMO} caracteres.`
  if (Object.keys(errores).length > 0) throw Object.assign(error('VALIDACION', 'Hay datos que corregir.'), { errores })
  const revisado = { ...actual, estado, comentario: texto || null, revisadoPor: actor.correo ?? null, revisadoEn: new Date().toISOString() }
  documentos = documentos.map((x) => (x.id === actual.id ? revisado : x))
  return copiar(revisado)
}
