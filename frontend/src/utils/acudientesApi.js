// "Mis jugadores" del acudiente con la API (módulo "acudientes"). Sin React ni red, para poder probarlo. Los ids del backend son UUID
// (texto): nunca se convierten a número. Contrato: MiJugadorResponseDTO (lista) y MiJugadorFichaResponseDTO (ficha).
//   Lista: id, nombre, apellido, fechaNacimiento, edad, categoria*, idCategoria*, grupo*, posicion*, foto*, parentesco*, activo, estadoInscripcion
//   Ficha: lo anterior + tipoDocumento, documento, telefono, direccion, documentosFaltantes (vacía si y solo si la inscripción está COMPLETA)
//   (* puede venir null: categoría fuera de 4 a 16 años; grupo solo en Cachorros y Semillero; foto es una URL firmada temporal)
import { ETIQUETA_ESTADO_INSCRIPCION, ETIQUETA_TIPO_DOCUMENTO } from './deportistasApi.js'
import { NOMBRES_TIPO_DOCUMENTO } from './documentosClub.js'
import { MESES } from './fechas.js'

export const ETIQUETA_INSCRIPCION = ETIQUETA_ESTADO_INSCRIPCION
export const TONO_INSCRIPCION = { COMPLETA: 'success', PENDIENTE: 'warning' }

// La API manda el parentesco sin tilde (Tia, Tio); en pantalla se usa la forma con tilde. Puede ser null.
const PARENTESCO_DESDE_API = { Tia: 'Tía', Tio: 'Tío' }
const textoOpcional = (valor) => (typeof valor === 'string' && valor.trim() !== '' ? valor : null)

// MiJugadorResponseDTO -> jugador de las pantallas. Lo que no viene queda en null (las pantallas lo ocultan).
export function adaptarMiJugador(dto) {
  return {
    id: dto.id,
    nombre: dto.nombre,
    apellido: dto.apellido,
    fechaNacimiento: dto.fechaNacimiento ?? null,
    edad: typeof dto.edad === 'number' ? dto.edad : null,
    categoria: textoOpcional(dto.categoria),
    grupoEntrenamiento: textoOpcional(dto.grupo),
    posicion: textoOpcional(dto.posicion),
    foto: textoOpcional(dto.foto),
    parentesco: textoOpcional(dto.parentesco) ? (PARENTESCO_DESDE_API[dto.parentesco] ?? dto.parentesco) : null,
    activo: dto.activo !== false,
    estadoInscripcion: dto.estadoInscripcion === 'COMPLETA' ? 'COMPLETA' : 'PENDIENTE',
  }
}

export const adaptarMisJugadores = (lista) => (Array.isArray(lista) ? lista.map(adaptarMiJugador) : [])

// MiJugadorFichaResponseDTO -> ficha. documentosFaltantes: [{ tipo, nombre }] con el nombre corto del club (un tipo desconocido se muestra tal cual).
export function adaptarFichaJugador(dto) {
  const faltantes = Array.isArray(dto.documentosFaltantes) ? dto.documentosFaltantes : []
  return {
    ...adaptarMiJugador(dto),
    tipoDocumento: ETIQUETA_TIPO_DOCUMENTO[dto.tipoDocumento] ?? textoOpcional(dto.tipoDocumento),
    documento: textoOpcional(dto.documento),
    telefono: textoOpcional(dto.telefono),
    direccion: textoOpcional(dto.direccion),
    documentosFaltantes: faltantes.map((tipo) => ({ tipo, nombre: NOMBRES_TIPO_DOCUMENTO[tipo] ?? tipo })),
  }
}

export const nombreCompleto = (jugador) => `${jugador.nombre} ${jugador.apellido}`.trim()

// "2018-03-14" -> "14 de marzo de 2018"
export function fechaNacimientoLegible(fecha) {
  const coincidencia = /^(\d{4})-(\d{2})-(\d{2})$/.exec(fecha ?? '')
  if (!coincidencia) return null
  const [, anio, mes, dia] = coincidencia
  const nombreMes = MESES[Number(mes) - 1]
  return nombreMes ? `${Number(dia)} de ${nombreMes} de ${anio}` : null
}

export const textoJugadores = (n) => `${n} ${n === 1 ? 'jugador' : 'jugadores'}`

export const MENSAJE_SIN_JUGADORES = 'Aún no tienes jugadores vinculados. Pide al club que los vincule.'
export const MENSAJE_JUGADOR_NO_ENCONTRADO = 'No encontramos ese jugador entre los tuyos.'
const MENSAJE_GENERICO_API = 'No se pudo completar la operación.'

// Estados HTTP donde reintentar no sirve: el jugador no es suyo, no existe o la cuenta no tiene permiso.
export const esErrorSinReintento = (error) => [400, 403, 404].includes(error?.estado)

// Texto del servidor que el acudiente no debe ver: menciona "id", UUID, rutas, HTTP o excepciones.
const esTextoTecnico = (texto) => /\bids?\b|uuid|\/api|endpoint|http|exception|\bnull\b/i.test(texto)

// Mensaje para mostrar según el error de apiClient. Solo el 403 usa el texto del servidor (si no es técnico); el 404 y el 400
// (id que no es UUID) siempre dicen "no es tuyo". Nunca se muestran nombres de endpoint ni ids.
export function mensajeDeErrorAcudiente(error, porDefecto) {
  const estado = error?.estado
  const deServidor = error?.mensaje && error.mensaje !== MENSAJE_GENERICO_API && !esTextoTecnico(error.mensaje) ? error.mensaje : null
  if (estado === 0 || error?.codigo === 'RED') return 'No pudimos conectar con el servidor. Revisa tu conexión e intenta de nuevo.'
  if (estado === 401) return 'Tu sesión venció. Inicia sesión de nuevo.'
  if (estado === 403) return deServidor ?? 'No tienes permiso para ver esta información.'
  if (estado === 404) return MENSAJE_JUGADOR_NO_ENCONTRADO
  if (estado === 400) return MENSAJE_JUGADOR_NO_ENCONTRADO
  if (estado === 503) return 'El servicio no está disponible en este momento. Intenta de nuevo más tarde.'
  return porDefecto
}
