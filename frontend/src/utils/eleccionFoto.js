// Qué pasa cuando alguien elige un archivo de foto (lógica pura de SubidaFoto, para poder probarla sin navegador).
//   archivo inválido            -> { error, adjuntada: false, pendiente: false }  (nada se adjunta; el error se muestra)
//   válido y guardarAlElegir    -> se entrega a onGuardar(archivo) de inmediato: { error: null, adjuntada: true, pendiente: false }
//                                  (si onGuardar falla: { error: mensaje, adjuntada: false, pendiente: false })
//   válido y sin guardarAlElegir -> queda pendiente hasta pulsar "Guardar foto": { error: null, adjuntada: false, pendiente: true }
import { TIPOS_FOTO, validarArchivo } from './archivos.js'

export async function resolverEleccionDeFoto(archivo, { guardarAlElegir = false, onGuardar } = {}) {
  const motivo = validarArchivo(archivo, { tipos: TIPOS_FOTO })
  if (motivo) return { error: motivo, adjuntada: false, pendiente: false }
  if (!guardarAlElegir) return { error: null, adjuntada: false, pendiente: true }
  try {
    await onGuardar(archivo)
    return { error: null, adjuntada: true, pendiente: false }
  } catch (e) {
    return { error: e.message || 'No se pudo guardar la foto. Intenta de nuevo.', adjuntada: false, pendiente: false }
  }
}
