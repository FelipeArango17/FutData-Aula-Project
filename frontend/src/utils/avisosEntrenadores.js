// Textos y resultados de los avisos del alta de entrenadores y del reenvío del enlace. Sin React, para poder probarlos.

// Aviso tras registrar: correoEnviado true = éxito; false = advertencia; null/undefined = no se sabe (modo sin API).
export function avisoDeAlta(correoEnviado, correo) {
  if (correoEnviado === true) return { texto: `Enviamos el enlace a ${correo}`, tono: 'success' }
  if (correoEnviado === false) {
    return { texto: 'El entrenador quedó registrado, pero no se pudo enviar el correo. Usa Reenviar enlace en la lista.', tono: 'warning' }
  }
  return { texto: 'Entrenador registrado.', tono: 'success' }
}

// Llama a `reenviar(id)` y devuelve { ok: true, texto } o { ok: false, texto } con el mensaje del backend ante 4xx (por ejemplo 409).
export async function reenviarConAviso(entrenador, reenviar) {
  try {
    await reenviar(entrenador.id)
    return { ok: true, texto: `Enviamos de nuevo el enlace a ${entrenador.correo}` }
  } catch (error) {
    return { ok: false, texto: error?.mensaje ?? error?.message ?? 'No se pudo completar la operación.' }
  }
}
