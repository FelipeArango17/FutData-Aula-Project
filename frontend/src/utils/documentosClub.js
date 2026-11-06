// Tipos de documento del club para un deportista (provisional, según lo que definió el club). Es el ÚNICO lugar con los
// identificadores internos, los nombres cortos, la descripción larga y si el documento es obligatorio o "si aplica".
// Lógica pura, sin estado ni red. No cambia el límite de archivo: PDF, JPG o PNG hasta 5 MB (utils/archivos.js).
// La "Foto tipo documento" que pide el club ES la foto de perfil del deportista (fotosService): NO se sube como documento, así que
// no es uno de estos tipos. Se muestra aparte en la lista de verificación (estadoFoto) y no cuenta para la inscripción.
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

// "Foto tipo documento": no es un documento, es la foto de perfil. Se muestra aparte, solo informativa: NO cuenta para la inscripción
// (el backend calcula estadoInscripcion solo con los 4 documentos obligatorios).
export const NOMBRE_FOTO = 'Foto tipo documento'
export const TOTAL_REQUISITOS_OBLIGATORIOS = IDS_OBLIGATORIOS.length // los 4 documentos obligatorios

export const ETIQUETA_SIN_CARGAR = 'Sin cargar'
export const ETIQUETA_FOTO_CARGADA = 'Cargada'
export const ETIQUETAS_ESTADO_VERIFICACION = { PENDIENTE: 'Pendiente', APROBADO: 'Aprobado', RECHAZADO: 'Rechazado' }

// Estado de un tipo según los documentos cargados (cualquier orden): el documento más reciente de ese tipo manda
// (cargadoEn, y a igual fecha el de id mayor). Sin documentos de ese tipo, null = "Sin cargar".
// Los ids pueden ser números (modo en memoria) o UUID (API): el desempate es por texto, sin restar.
export const masReciente = (a, b) => String(b.cargadoEn).localeCompare(String(a.cargadoEn)) || String(b.id).localeCompare(String(a.id), undefined, { numeric: true })

export function estadoDelTipo(documentos, idTipo) {
  const delTipo = documentos.filter((d) => d.tipo === idTipo)
  if (delTipo.length === 0) return null
  return [...delTipo].sort(masReciente)[0].estado
}

// Un solo documento activo (PENDIENTE o APROBADO) por tipo: "Subir" solo se ofrece en los tipos sin documento activo, es decir, sin
// documentos o con el último RECHAZADO. `tipos` por defecto son los 7 del club (el backend los acepta todos).
export const tiposParaSubir = (documentos, tipos = TIPOS_DOCUMENTO_CLUB) => tipos.filter((t) => {
  const estado = estadoDelTipo(documentos, t.id)
  return estado === null || estado === 'RECHAZADO'
})

// Los 3 "si aplica" (paz y salvo, carta de renuncia, traspaso nacional) NO cuentan: solo los 4 obligatorios deciden. Igual que el backend: COMPLETA si los 4 obligatorios tienen al menos un documento APROBADO; si no, PENDIENTE. `documentosFaltantes` son los
// ids de los obligatorios sin aprobado, en el orden del club. Lo usa el modo en memoria; con la API estos datos vienen del backend.
export function calcularInscripcion(documentos) {
  const documentosFaltantes = IDS_OBLIGATORIOS.filter((id) => !documentos.some((d) => d.tipo === id && d.estado === 'APROBADO'))
  return { estadoInscripcion: documentosFaltantes.length === 0 ? 'COMPLETA' : 'PENDIENTE', documentosFaltantes }
}

// "Faltan: Certificado de estudio, Habeas Data" (null si no falta nada)
export const textoFaltantes = (ids = []) => (ids.length === 0 ? null : `Faltan: ${ids.map((id) => NOMBRES_TIPO_DOCUMENTO[id] ?? id).join(', ')}`)

// Lista de verificación: los 7 tipos de documento, en orden. Fila = { id, nombre, descripcion, obligatorio, estado, etiquetaEstado, marca }
//   estado: 'PENDIENTE' | 'APROBADO' | 'RECHAZADO' | null (Sin cargar)
export function listaDeVerificacion(documentos) {
  return TIPOS_DOCUMENTO_CLUB.map((t) => {
    const estado = estadoDelTipo(documentos, t.id)
    return { ...t, estado, etiquetaEstado: estado ? ETIQUETAS_ESTADO_VERIFICACION[estado] : ETIQUETA_SIN_CARGAR, marca: t.obligatorio ? 'Obligatorio' : 'Si aplica' }
  })
}

// Estado informativo de la foto de perfil: { nombre, cargada, etiquetaEstado }. No es un requisito de la inscripción.
export const estadoFoto = (tieneFoto) => ({ nombre: NOMBRE_FOTO, cargada: Boolean(tieneFoto), etiquetaEstado: tieneFoto ? ETIQUETA_FOTO_CARGADA : ETIQUETA_SIN_CARGAR })

// { completos, total, texto }: los 4 documentos obligatorios con uno aprobado, igual que estadoInscripcion. "2 de 4 requisitos obligatorios completos"
export function resumenObligatorios(documentos) {
  const completos = IDS_OBLIGATORIOS.filter((id) => estadoDelTipo(documentos, id) === 'APROBADO').length
  const total = TOTAL_REQUISITOS_OBLIGATORIOS
  return { completos, total, texto: `${completos} de ${total} requisitos obligatorios completos` }
}
