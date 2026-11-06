// Documentos del deportista con la API (módulo "documentos"). Sin React ni red, para poder probarlo. Los ids del backend son UUID
// (texto): nunca se convierten a número. La ruta del archivo nunca sale del backend; el archivo se ve con una URL firmada de 5 minutos.

export const ETIQUETA_ESTADO_INSCRIPCION_DOCUMENTOS = { COMPLETA: 'Completa', PENDIENTE: 'Pendiente' }

// Respuesta de GET /api/deportistas/{id}/documentos ({ documentos, estadoInscripcion, documentosFaltantes }) -> la forma de las pantallas.
// Es un objeto, no un arreglo. Del más reciente al más antiguo.
export function adaptarListaDocumentos(cuerpo) {
  const documentos = Array.isArray(cuerpo?.documentos) ? cuerpo.documentos : []
  return {
    documentos: [...documentos].sort((a, b) => String(b.cargadoEn).localeCompare(String(a.cargadoEn))),
    estadoInscripcion: cuerpo?.estadoInscripcion ?? 'PENDIENTE',
    documentosFaltantes: Array.isArray(cuerpo?.documentosFaltantes) ? cuerpo.documentosFaltantes : [],
  }
}

// avisoEnviado de rechazar: true = correo enviado; false = el rechazo quedó pero el correo falló; null = sin acudientes con correo.
// -> { texto, tono } para el aviso de la pantalla (tono 'warning' cuando hay algo que hacer a mano).
export function avisoDeRechazo(avisoEnviado) {
  if (avisoEnviado === true) return { texto: 'Documento rechazado. Se avisó al acudiente por correo.', tono: 'success' }
  if (avisoEnviado === false) return { texto: 'Documento rechazado, pero el correo al acudiente no se pudo enviar. Avísale por otro medio.', tono: 'warning' }
  return { texto: 'Documento rechazado. Este deportista no tiene acudientes con correo, así que no se envió ningún aviso.', tono: 'warning' }
}

const MENSAJE_ARCHIVO_INVALIDO = 'El archivo o el tipo de documento no son válidos. Usa un PDF, JPG o PNG de máximo 5 MB.'

// Error de apiClient -> Error con `codigo` y un `message` en español listo para mostrar (conserva `estado`):
//   400 -> 'VALIDACION' (el texto del backend si lo trae; si no, el de archivo o tipo inválido)   403 -> 'SIN_PERMISO'
//   404 -> 'NO_ENCONTRADO' (para el acudiente: no es suyo, o no existe; el backend responde igual)   409 -> 'DOCUMENTO_ACTIVO'
//   sin respuesta -> 'ERROR_DE_RED'; lo demás queda sin `codigo` (las pantallas muestran el texto general).
export function errorDeDocumentos(error, { rol } = {}) {
  if (!error || error.estado === undefined) return error
  const poner = (codigo, message) => Object.assign(error, { codigo, message })
  switch (error.estado) {
    case 0: return poner('ERROR_DE_RED', 'No pudimos conectar con el servidor. Revisa tu conexión e intenta de nuevo.')
    case 400: {
      const texto = error.mensaje && error.mensaje !== 'No se pudo completar la operación.' ? error.mensaje : MENSAJE_ARCHIVO_INVALIDO
      // campos ["tipo"]: un tipo que el backend no acepta (no debería pasar: el formulario solo ofrece los 7 del club)
      return Object.assign(poner('VALIDACION', texto), error.campos?.includes('tipo') ? { errores: { tipo: 'Selecciona un tipo de documento válido.' } } : {})
    }
    case 403: return poner('SIN_PERMISO', 'No tienes permiso para esta acción con los documentos.')
    case 404: return poner('NO_ENCONTRADO', rol === 'acudiente' ? 'No encontramos ese jugador entre los tuyos.' : 'No encontramos ese documento o ese deportista.')
    case 409: return poner('DOCUMENTO_ACTIVO', 'Ya hay un documento activo de este tipo.')
    default: return error
  }
}

// Texto para mostrar de un error de las pantallas: el de las que traen `codigo` (es seguro y está en español) o el general.
export const mensajeDeError = (error, general) => (error?.codigo ? error.message : general)
