// Tipos de documento del club para un deportista (provisional, según lo que definió el club). Es el ÚNICO lugar con los
// identificadores internos, los nombres cortos, la descripción larga y si el documento es obligatorio o "si aplica".
// Lógica pura, sin estado ni red. No cambia el límite de archivo: PDF, JPG o PNG hasta 5 MB (utils/archivos.js).
// La "Foto tipo documento" que pide el club ES la foto de perfil del deportista (fotosService): NO se sube como documento, así que
// no es uno de estos tipos. Solo aparece como un requisito más en la lista de verificación (REQUISITO_FOTO), según haya foto o no.
// Los 7 tipos que se pueden subir (4 obligatorios y 3 si aplica)
export const TIPOS_DOCUMENTO_CLUB = [
  {
    id: 'DOCUMENTO_IDENTIDAD', nombre: 'Documento de identidad', obligatorio: true,
    descripcion: 'Documento de identidad (fotocopia de la cédula, tarjeta de identidad o registro civil autenticado, según la edad)',
  },
  {
    id: 'CERTIFICADO_ESTUDIO', nombre: 'Certificado de estudio', obligatorio: true,
    descripcion: 'Certificado de estudio (original reciente o acta de grado)',
  },
  { id: 'FORMULARIO_INSCRIPCION', nombre: 'Formulario de inscripción', obligatorio: true, descripcion: 'Formulario de inscripción al club' },
  { id: 'AUTORIZACION_HABEAS_DATA', nombre: 'Habeas Data', obligatorio: true, descripcion: 'Autorización de tratamiento de datos (Habeas Data)' },
  { id: 'PAZ_Y_SALVO', nombre: 'Paz y salvo', obligatorio: false, descripcion: 'Paz y salvo (si viene de otro club, firmado y visado)' },
  {
    id: 'CARTA_RENUNCIA', nombre: 'Carta de renuncia', obligatorio: false,
    descripcion: 'Carta de renuncia al club anterior (si jugaba en otra liga o región fuera de Antioquia)',
  },
  { id: 'TRASPASO_NACIONAL', nombre: 'Traspaso nacional', obligatorio: false, descripcion: 'Traspaso nacional' },
]

export const IDS_TIPOS_DOCUMENTO = TIPOS_DOCUMENTO_CLUB.map((t) => t.id)
export const IDS_OBLIGATORIOS = TIPOS_DOCUMENTO_CLUB.filter((t) => t.obligatorio).map((t) => t.id)
export const NOMBRES_TIPO_DOCUMENTO = Object.fromEntries(TIPOS_DOCUMENTO_CLUB.map((t) => [t.id, t.nombre]))
export const DESCRIPCIONES_TIPO_DOCUMENTO = Object.fromEntries(TIPOS_DOCUMENTO_CLUB.map((t) => [t.id, t.descripcion]))

// Requisito "Foto tipo documento": no es un documento, es la foto de perfil. Su estado sale de si el deportista tiene foto.
export const REQUISITO_FOTO = { id: 'FOTO_PERFIL', nombre: 'Foto tipo documento', obligatorio: true }
export const TOTAL_REQUISITOS_OBLIGATORIOS = IDS_OBLIGATORIOS.length + 1 // los documentos obligatorios + la foto

export const ETIQUETA_SIN_CARGAR = 'Sin cargar'
export const ETIQUETA_FOTO_CARGADA = 'Cargada'
export const ETIQUETAS_ESTADO_VERIFICACION = { PENDIENTE: 'Pendiente', APROBADO: 'Aprobado', RECHAZADO: 'Rechazado' }

// Estado de un tipo según los documentos cargados (cualquier orden): el documento más reciente de ese tipo manda
// (cargadoEn, y a igual fecha el de id mayor). Sin documentos de ese tipo, null = "Sin cargar".
export function estadoDelTipo(documentos, idTipo) {
  const delTipo = documentos.filter((d) => d.tipo === idTipo)
  if (delTipo.length === 0) return null
  const reciente = [...delTipo].sort((a, b) => b.cargadoEn.localeCompare(a.cargadoEn) || b.id - a.id)[0]
  return reciente.estado
}

// Lista de verificación: la fila de la foto (que no se sube como documento) va justo después del documento de identidad, y luego
// los demás tipos, en orden. Fila = { id, nombre, descripcion?, obligatorio, esFoto, estado, etiquetaEstado, marca }
//   documentos: estado 'PENDIENTE' | 'APROBADO' | 'RECHAZADO' | null (Sin cargar)
//   foto: estado 'CARGADA' | null (Sin cargar), según `tieneFoto`
export function listaDeVerificacion(documentos, { tieneFoto = false } = {}) {
  const filas = TIPOS_DOCUMENTO_CLUB.map((t) => {
    const estado = estadoDelTipo(documentos, t.id)
    return { ...t, esFoto: false, estado, etiquetaEstado: estado ? ETIQUETAS_ESTADO_VERIFICACION[estado] : ETIQUETA_SIN_CARGAR, marca: t.obligatorio ? 'Obligatorio' : 'Si aplica' }
  })
  const foto = {
    ...REQUISITO_FOTO, esFoto: true, estado: tieneFoto ? 'CARGADA' : null, etiquetaEstado: tieneFoto ? ETIQUETA_FOTO_CARGADA : ETIQUETA_SIN_CARGAR, marca: 'Obligatorio',
  }
  return [filas[0], foto, ...filas.slice(1)]
}

// { completos, total, texto } con los requisitos obligatorios completos: los 4 documentos obligatorios aprobados y la foto cargada.
// "2 de 5 requisitos obligatorios completos"
export function resumenObligatorios(documentos, { tieneFoto = false } = {}) {
  const completos = IDS_OBLIGATORIOS.filter((id) => estadoDelTipo(documentos, id) === 'APROBADO').length + (tieneFoto ? 1 : 0)
  const total = TOTAL_REQUISITOS_OBLIGATORIOS
  return { completos, total, texto: `${completos} de ${total} requisitos obligatorios completos` }
}
