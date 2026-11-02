// Lógica de la pantalla /crear-contrasena sin React, para poder probarla. La pantalla solo pinta y navega.
import { MENSAJES, cumplePoliticaContrasena, requerido, validarCampos } from './validaciones.js'

export const MENSAJE_ENLACE_INVALIDO = 'El enlace no es válido o venció; pide al administrador que te lo reenvíe'
export const MENSAJE_CONTRASENA_CREADA = 'Tu contraseña quedó creada. Ya puedes iniciar sesión.'

// { campo: mensaje } solo con los campos con error. Las contraseñas no se recortan: los espacios cuentan.
export function validarContrasenas(valores) {
  return validarCampos(valores, {
    contrasena: [
      requerido(MENSAJES.loginContrasenaObligatoria, { recortar: false }),
      (v) => (cumplePoliticaContrasena(v) ? null : MENSAJES.contrasenaPolitica),
    ],
    confirmar: [
      requerido(MENSAJES.contrasenaConfirmar, { recortar: false }),
      (v, todos) => (v === todos.contrasena ? null : MENSAJES.contrasenaNoCoincide),
    ],
  })
}

export const MENSAJE_REINTENTAR = 'Inténtalo de nuevo; si persiste, pide que te reenvíen el enlace.'

const sinAcentos = (t) => String(t ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim()

// Un 400 de POST /api/auth/crear-contrasena puede ser de dos clases (contrato del backend):
//  - la contraseña incumple la política: el cuerpo trae el texto de la política (y puede traer campos: ['contrasena']);
//  - el token es inválido o venció: cualquier otro mensaje.
// Se distingue por el mensaje (igual al de MENSAJES.contrasenaPolitica, sin importar tildes o mayúsculas) o por `campos`.
export function esErrorDePolitica(error) {
  if (error?.estado !== 400) return false
  if (Array.isArray(error.campos) && error.campos.includes('contrasena')) return true
  return sinAcentos(error.mensaje ?? error.message) === sinAcentos(MENSAJES.contrasenaPolitica)
}

// Valida y envía. Devuelve { tipo: 'errores', errores } | { tipo: 'listo' } | { tipo: 'fallo', texto }.
// Sin token no llama a `crear`. Errores del backend: 400 de política -> error del campo contraseña; 400 de token -> enlace
// inválido (mensaje fijo); 503 -> reintentar; red y otros usan el mensaje del cliente.
export async function enviarContrasena({ token, valores, crear }) {
  if (!token) return { tipo: 'fallo', texto: MENSAJE_ENLACE_INVALIDO }
  const errores = validarContrasenas(valores)
  if (Object.keys(errores).length > 0) return { tipo: 'errores', errores }
  try {
    await crear(token, valores.contrasena)
    return { tipo: 'listo' }
  } catch (error) {
    if (esErrorDePolitica(error)) return { tipo: 'errores', errores: { contrasena: error.mensaje || MENSAJES.contrasenaPolitica } }
    if (error?.estado === 503) return { tipo: 'fallo', texto: MENSAJE_REINTENTAR }
    return { tipo: 'fallo', texto: error?.estado === 400 ? MENSAJE_ENLACE_INVALIDO : (error?.mensaje ?? error?.message ?? 'No se pudo completar la operación.') }
  }
}
