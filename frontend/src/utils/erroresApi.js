// Convierte un error de la API (ver services/apiClient.js) en { campo: texto } para marcar los campos de un formulario.
// El backend manda `campos` (los campos con error) y `mensaje` con el formato "campo: texto; campo: texto".
// duplicados: texto propio por campo para los 409 de "ya existe" (el mensaje del backend dice el campo, nunca de quién).
// Sin `campos` devuelve {}: es un error general (permisos, red, servidor) que la pantalla muestra como aviso.
const capitalizar = (texto) => texto.charAt(0).toUpperCase() + texto.slice(1)

export function erroresPorCampo(error, { duplicados = {} } = {}) {
  const campos = Array.isArray(error?.campos) ? error.campos : []
  if (campos.length === 0) return {}
  const mensaje = String(error.mensaje ?? error.message ?? '')
  const porCampo = {}
  for (const parte of mensaje.split('; ')) {
    const separador = parte.indexOf(': ')
    if (separador > 0) porCampo[parte.slice(0, separador)] = parte.slice(separador + 2)
  }
  const errores = {}
  for (const campo of campos) {
    if (error.estado === 409 && duplicados[campo]) errores[campo] = duplicados[campo]
    else if (porCampo[campo]) errores[campo] = `${capitalizar(porCampo[campo].replace(/\.$/, ''))}.`
    else errores[campo] = mensaje
  }
  return errores
}
