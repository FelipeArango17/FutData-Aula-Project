// Lógica de "Recuperar contraseña" (/recuperar) y "Restablecer" (/restablecer) con Supabase Auth, sin React ni red, para
// poder probarla. Las pantallas solo pintan; los servicios (authService*.js) hablan con Supabase y lanzan errores con
// `codigo` propio (LIMITE, RED, POLITICA, IGUAL_ANTERIOR, ENLACE_INVALIDO, DESCONOCIDO) para no depender de textos de Supabase.
import { MENSAJES, validarCorreo } from './validaciones.js'
import { validarContrasenas } from './crearContrasena.js'

export const MENSAJE_RECUPERACION_ENVIADA = 'Si el correo está registrado, te enviamos un enlace para crear una contraseña nueva. Revisa también la carpeta de spam.'
export const MENSAJE_LIMITE = 'Hiciste muchos intentos. Espera un minuto e inténtalo de nuevo.'
export const MENSAJE_RECUPERACION_FALLO = 'No pudimos enviar el enlace. Inténtalo de nuevo más tarde.'
export const MENSAJE_RESTABLECER_FALLO = 'No pudimos guardar la contraseña. Inténtalo de nuevo.'
export const MENSAJE_SIN_RED = 'No pudimos conectar. Intenta de nuevo.'
export const MENSAJE_RESTABLECER_ENLACE_INVALIDO = 'El enlace no es válido o venció. Pide uno nuevo.'
export const MENSAJE_CONTRASENA_IGUAL = 'La contraseña nueva debe ser distinta de la anterior.'
export const MENSAJE_CONTRASENA_CREADA = 'Tu contraseña se creó. Inicia sesión para entrar a FutData.'
export const MENSAJE_ENLACE_VENCIDO = 'El enlace venció o ya se usó. Pide uno nuevo desde "Olvidé mi contraseña".'
export const MENSAJE_CONTRASENA_ACTUALIZADA = 'Tu contraseña se actualizó. Inicia sesión con la nueva.'

// Segundos con el botón "Enviar enlace" deshabilitado tras cada envío (Supabase deja un envío por minuto por usuario).
export const ESPERA_TRAS_ENVIO_S = 15
export const ESPERA_TRAS_LIMITE_S = 60
// Máximo que /restablecer espera la sesión de recuperación antes de dar el enlace por inválido.
export const ESPERA_VERIFICACION_MS = 5000

// Misma validación del correo que el inicio de sesión.
export const validarCorreoRecuperacion = (correo) => validarCorreo(correo, {
  obligatorio: MENSAJES.loginCorreoObligatorio,
  formato: MENSAJES.loginCorreoFormato,
})

// Error de supabase-js (AuthError: { status, code, name, message }) -> código propio.
export function codigoDeErrorSupabase(error) {
  const estado = error?.status ?? 0
  const codigo = error?.code
  if (codigo === 'over_email_send_rate_limit' || codigo === 'over_request_rate_limit' || estado === 429) return 'LIMITE'
  if (codigo === 'weak_password') return 'POLITICA'
  if (codigo === 'same_password' || /different from the old password/i.test(error?.message ?? '')) return 'IGUAL_ANTERIOR'
  if (
    error?.name === 'AuthSessionMissingError' || estado === 401
    || ['session_not_found', 'session_expired', 'bad_jwt', 'otp_expired', 'refresh_token_not_found', 'user_not_found'].includes(codigo)
  ) return 'ENLACE_INVALIDO'
  if (error?.name === 'AuthRetryableFetchError' || estado === 0 || estado >= 500) return 'RED'
  return 'DESCONOCIDO'
}

// Pide el enlace. Devuelve { tipo: 'errores', errores } | { tipo: 'enviado', texto } | { tipo: 'fallo', texto, limite }.
// El resultado es IGUAL exista o no la cuenta: Supabase responde bien en los dos casos y aquí no se distingue.
export async function solicitarEnlace({ correo, solicitar }) {
  const mensaje = validarCorreoRecuperacion(correo)
  if (mensaje) return { tipo: 'errores', errores: { correo: mensaje } }
  try {
    await solicitar(correo)
    return { tipo: 'enviado', texto: MENSAJE_RECUPERACION_ENVIADA }
  } catch (error) {
    if (error?.codigo === 'LIMITE') return { tipo: 'fallo', texto: MENSAJE_LIMITE, limite: true }
    return { tipo: 'fallo', texto: MENSAJE_RECUPERACION_FALLO, limite: false }
  }
}

// ---- Qué hay en la URL del enlace del correo ----
const parametros = ({ hash = '', search = '' } = {}) => {
  const unidos = new URLSearchParams(String(search).replace(/^\?/, ''))
  for (const [clave, valor] of new URLSearchParams(String(hash).replace(/^#/, ''))) unidos.set(clave, valor)
  return unidos
}
// Supabase deja el fallo en la URL: error, error_code (otp_expired), error_description (access_denied, etc.)
export const urlTraeError = (url) => {
  const p = parametros(url)
  return p.has('error') || p.has('error_code') || p.has('error_description')
}
export const urlEsDeRecuperacion = (url) => parametros(url).get('type') === 'recovery'
// Enlace de invitación (POST /api/usuarios): Supabase manda type=invite. Se trata como la recuperación: la sesión que trae es
// solo para crear la contraseña, no es un ingreso.
export const urlEsDeInvitacion = (url) => parametros(url).get('type') === 'invite'
// Cualquier enlace del correo que termina en /restablecer: recuperación o invitación.
export const urlEsDeEnlaceDeCorreo = (url) => urlEsDeRecuperacion(url) || urlEsDeInvitacion(url)
// Enlace vencido o ya usado: Supabase deja error_code=otp_expired o error=access_denied en la URL.
export const urlEsDeEnlaceVencido = (url) => {
  const p = parametros(url)
  return p.get('error_code') === 'otp_expired' || p.get('error') === 'access_denied'
}

// Enlace del correo (invitación, recuperación, vencido o usado) que aterrizó fuera de /restablecer y /recuperar -> la URL a la
// que hay que llevarlo: '/restablecer' + search + hash EXACTOS (supabase-js necesita los tokens). Si no aplica, null.
// main.jsx lo aplica con history.replaceState ANTES de montar React, una sola vez, sin pasar por el router.
export function rutaDeEnlaceDeCorreo({ pathname = '', search = '', hash = '' } = {}) {
  const url = { search, hash }
  const esEnlace = urlEsDeEnlaceDeCorreo(url) || urlEsDeEnlaceVencido(url)
  if (!esEnlace || pathname === '/restablecer' || pathname === '/recuperar') return null
  return `/restablecer${search}${hash}`
}

// 'verificando' | 'listo' | 'invalido'. Solo hay sesión de recuperación si la URL trae type=recovery o llegó el evento
// PASSWORD_RECOVERY (flujo con code): una sesión normal de Supabase NO basta para cambiar la contraseña desde /restablecer.
// `agotado`: se acabó la espera y no se resolvió -> inválido.
export function decidirRecuperacion({ url, evento = null, haySesion, agotado = false }) {
  if (urlTraeError(url)) return 'invalido'
  if (haySesion && (evento === 'PASSWORD_RECOVERY' || urlEsDeEnlaceDeCorreo(url))) return 'listo'
  return agotado ? 'invalido' : 'verificando'
}

// ¿Esta carga de la página es de una recuperación? Si sí, la sesión de Supabase NO se restaura como sesión de la app
// (no se pide el código 2FA ni se redirige al panel): el usuario cambia la contraseña y vuelve a entrar con Login + 2FA.
export const esRecuperacionEnCurso = ({ pathname, url, marcada = false }) => (
  marcada || pathname === '/restablecer' || urlEsDeEnlaceDeCorreo(url)
)

// Guarda la contraseña nueva. Devuelve { tipo: 'errores', errores } | { tipo: 'listo' } | { tipo: 'fallo', texto, enlaceInvalido }.
export async function enviarRestablecer({ valores, restablecer }) {
  const errores = validarContrasenas(valores)
  if (Object.keys(errores).length > 0) return { tipo: 'errores', errores }
  try {
    await restablecer(valores.contrasena)
    return { tipo: 'listo' }
  } catch (error) {
    switch (error?.codigo) {
      case 'POLITICA': return { tipo: 'errores', errores: { contrasena: MENSAJES.contrasenaPolitica } }
      case 'IGUAL_ANTERIOR': return { tipo: 'errores', errores: { contrasena: MENSAJE_CONTRASENA_IGUAL } }
      case 'ENLACE_INVALIDO': return { tipo: 'fallo', texto: MENSAJE_RESTABLECER_ENLACE_INVALIDO, enlaceInvalido: true }
      case 'LIMITE': return { tipo: 'fallo', texto: MENSAJE_LIMITE, enlaceInvalido: false }
      case 'RED': return { tipo: 'fallo', texto: MENSAJE_SIN_RED, enlaceInvalido: false }
      default: return { tipo: 'fallo', texto: MENSAJE_RESTABLECER_FALLO, enlaceInvalido: false }
    }
  }
}
