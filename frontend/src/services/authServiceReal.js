// Servicio de autenticación REAL (HU-001 y HU-002): Supabase Auth para el correo y la contraseña, y el backend
// para el segundo paso (código de 6 dígitos). Tiene el MISMO contrato que authService.js (el simulado), para que
// AuthProvider y las pantallas no cambien. Se elige en services/auth.js.
//
// Diferencias con el simulado, que vienen del backend:
// - El backend toma al usuario del JWT: /codigo y /reenviar van sin cuerpo y /verificar solo lleva { codigo }.
// - El perfil (nombre, rol, activo) NO se pide antes del código: GET /api/usuarios/yo exige el 2FA completado
//   (backend con la verificación por sesión) y respondería 403. Sale de la respuesta de POST /api/auth/2fa/verificar
//   (UsuarioResponseDTO: { id, nombre, apellido, correo, rol, activo, creadoEn }), y /usuarios/yo solo se llama con
//   la sesión ya verificada (al recargar la página).
// - Al cerrar sesión se avisa al backend (POST /api/auth/logout) antes de cerrar la de Supabase.
// - Los errores del 2FA traen { codigo, msRestantes, campos: ['codigo'] }. Se conserva el mapeo por texto para
//   compatibilidad con instalaciones anteriores del backend.
// - PENDIENTE backend: "Recordar este equipo" (cookie HttpOnly de equipo de confianza). No se envía nada.
import { apiRequest } from './apiClient.js'
import { supabase } from './supabaseClient.js'

export const VIGENCIA_CODIGO_MS = 10 * 60 * 1000
export const MAX_INTENTOS = 5
export const BLOQUEO_MS = 15 * 60 * 1000
export const REENVIO_MS = 60 * 1000
// Supabase responde 429 sin decir cuánto falta: se muestra un minuto.
const ESPERA_SUPABASE_MS = 60 * 1000

const CLAVE = 'futdata.auth.real'
const fallo = (codigo, extra = {}) => Object.assign(new Error(codigo), { codigo, ...extra })
const normalizar = (correo) => String(correo ?? '').trim().toLowerCase()
const enMinusculas = (rol) => String(rol ?? '').toLowerCase()

// ---- Solicitud de código pendiente (sessionStorage: sobrevive a recargar la pestaña) ----
// { correo, rol, nombre, correoEnmascarado, reenvioDisponibleEn, intentos, bloqueoHasta }
let memoria = null
function leer() {
  try {
    const guardada = JSON.parse(sessionStorage.getItem(CLAVE))
    if (guardada && typeof guardada === 'object') return guardada
  } catch {
    // sin almacenamiento o dato dañado
  }
  return memoria
}
function guardar(solicitud) {
  memoria = solicitud
  try {
    if (solicitud) sessionStorage.setItem(CLAVE, JSON.stringify(solicitud))
    else sessionStorage.removeItem(CLAVE)
  } catch {
    // sin almacenamiento: la solicitud dura mientras la página siga abierta
  }
}

async function cerrarSupabase(opciones) {
  try {
    await supabase.auth.signOut(opciones)
  } catch {
    // la sesión local se descarta de todos modos
  }
}

// ---- Errores de Supabase Auth ----
function errorDeSupabase(error) {
  const estado = error.status ?? 0
  if (error.name === 'AuthRetryableFetchError' || estado === 0 || estado >= 500) return fallo('RED')
  if (error.code === 'user_banned') return fallo('INACTIVA')
  if (estado === 429) return fallo('BLOQUEADO', { msRestantes: ESPERA_SUPABASE_MS })
  // Correo desconocido, contraseña incorrecta o correo sin confirmar: un mismo mensaje, no se revela cuál falló.
  return fallo('CREDENCIALES')
}

// ---- Errores del 2FA del backend ----
const quitarAcentos = (texto) => String(texto ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase()
// Solución provisoria: el backend solo manda el mensaje. Cada patrón es un trozo del mensaje sin acentos.
const CODIGOS_POR_MENSAJE = [
  ['no hay un codigo pendiente', 'SIN_SOLICITUD'],
  ['codigo expiro', 'VENCIDO'],
  ['maximo de intentos', 'BLOQUEADO'],
  ['bloqueada temporalmente', 'BLOQUEADO'],
  ['debes esperar', 'DEMASIADO_PRONTO'],
  ['codigo no es correcto', 'INCORRECTO'],
]
const CODIGOS_CONOCIDOS = ['SIN_SOLICITUD', 'VENCIDO', 'USADO', 'BLOQUEADO', 'DEMASIADO_PRONTO', 'INCORRECTO', 'FORMATO']

function codigoDelError(error) {
  if (CODIGOS_CONOCIDOS.includes(error.codigo)) return error.codigo
  if (error.estado === 400 && error.campos?.includes('codigo')) return 'FORMATO'
  const texto = quitarAcentos(error.mensaje)
  return CODIGOS_POR_MENSAJE.find(([trozo]) => texto.includes(trozo))?.[1] ?? null
}

// Anota en la solicitud lo que el backend no dice: cuándo termina el bloqueo y cuántos intentos van.
function conBloqueo(solicitud, msRestantes) {
  const ms = Number.isFinite(msRestantes) ? msRestantes : (solicitud?.bloqueoHasta ?? 0) - Date.now()
  const restante = ms > 0 ? ms : BLOQUEO_MS
  if (solicitud) guardar({ ...solicitud, intentos: MAX_INTENTOS, bloqueoHasta: Date.now() + restante })
  return restante
}

function traducirError2FA(error) {
  const solicitud = leer()
  switch (codigoDelError(error)) {
    case 'INCORRECTO': {
      const intentos = Math.min(MAX_INTENTOS, (solicitud?.intentos ?? 0) + 1)
      if (solicitud) guardar({ ...solicitud, intentos })
      return fallo('INCORRECTO', { intentosRestantes: MAX_INTENTOS - intentos })
    }
    case 'BLOQUEADO':
      return fallo('BLOQUEADO', { msRestantes: conBloqueo(solicitud, error.msRestantes) })
    case 'DEMASIADO_PRONTO': {
      const faltan = Number.isFinite(error.msRestantes) ? error.msRestantes : (solicitud?.reenvioDisponibleEn ?? 0) - Date.now()
      return fallo('DEMASIADO_PRONTO', { msRestantes: faltan > 0 ? faltan : REENVIO_MS })
    }
    case 'VENCIDO':
      return fallo('VENCIDO')
    case 'USADO':
      return fallo('USADO')
    case 'SIN_SOLICITUD':
      guardar(null)
      return fallo('SIN_SOLICITUD')
    case 'FORMATO':
      return fallo('FORMATO')
    default:
      return error
  }
}

// ---- Errores de acceso del backend (401 y 403 que no son del 2FA) ----
// 401: el token no sirve (backend nuevo: no trae session_id). 403: la cuenta no tiene rol en FutData o le revocaron
// el acceso. 404 (backend anterior): la cuenta no está registrada o no está activa. En todos los casos no hay
// sesión posible: se cierra la de Supabase y se vuelve al login con el motivo (ver MENSAJES en Login.jsx).
function errorDeAcceso(error) {
  if (error.estado === 401) return fallo('SESION_INVALIDA')
  if (error.estado === 403 && error.codigo !== '2FA_REQUERIDA') return fallo('SIN_ROL')
  if (error.estado === 404) return fallo(quitarAcentos(error.mensaje).includes('no esta activa') ? 'INACTIVA' : 'PERFIL')
  return null
}

async function cerrarPorAcceso(errorTraducido) {
  guardar(null)
  await cerrarSupabase({ scope: 'local' })
  return errorTraducido
}

// POST /api/auth/2fa/codigo: el backend no repite el envío si hay un código vigente de hace menos de un minuto.
// Si el código no se pudo enviar (503) o el backend no responde, el inicio de sesión falla como RED.
async function pedirCodigo(usuario) {
  let respuesta
  try {
    respuesta = await apiRequest('/auth/2fa/codigo', { method: 'POST' })
  } catch (error) {
    if (error.estado === 409) throw traducirError2FA(error)
    if (error.codigo === 'RED' || error.estado >= 500) throw fallo('RED')
    throw errorDeAcceso(error) ?? error
  }
  const previa = leer()
  const mismaCuenta = previa?.correo === usuario.correo
  const solicitud = {
    ...usuario,
    correoEnmascarado: respuesta.correoEnmascarado,
    reenvioDisponibleEn: Date.now() + respuesta.reenvioDisponibleEn,
    // Un código nuevo reinicia los intentos; si el backend devolvió el mismo, se conservan.
    intentos: mismaCuenta && previa.reenvioDisponibleEn > Date.now() ? previa.intentos : 0,
    bloqueoHasta: mismaCuenta ? previa.bloqueoHasta ?? null : null,
  }
  guardar(solicitud)
  return solicitud
}

// Paso 1: correo y contraseña en Supabase y, enseguida, el código por correo. El nombre y el rol todavía no se
// conocen (llegan al verificar el código): el usuario pendiente lleva rol null y nombre vacío.
// Errores (error.codigo): RED, CREDENCIALES, INACTIVA, BLOQUEADO, SESION_INVALIDA (401), SIN_ROL (403) y
// PERFIL (404: cuenta sin perfil en FutData, backend anterior).
export async function iniciarSesion(correo, contrasena) {
  let resultado
  try {
    resultado = await supabase.auth.signInWithPassword({ email: String(correo ?? '').trim(), password: contrasena })
  } catch {
    throw fallo('RED')
  }
  if (resultado.error) throw errorDeSupabase(resultado.error)

  const usuario = { correo: normalizar(resultado.data?.user?.email ?? correo), rol: null, nombre: '' }
  try {
    await pedirCodigo(usuario)
    return { ...usuario, verificada: false }
  } catch (error) {
    // Sin 2FA no hay sesión: no se deja la de Supabase abierta a medias.
    throw await cerrarPorAcceso(error)
  }
}

// Datos de la solicitud pendiente para pintar la pantalla del código (o null si no hay).
export function estadoCodigo() {
  const p = leer()
  if (!p) return null
  return {
    correo: p.correo,
    correoEnmascarado: p.correoEnmascarado,
    rol: p.rol,
    nombre: p.nombre,
    intentosRestantes: Math.max(0, MAX_INTENTOS - (p.intentos ?? 0)),
    reenvioDisponibleEn: p.reenvioDisponibleEn,
    bloqueoHasta: p.bloqueoHasta && p.bloqueoHasta > Date.now() ? p.bloqueoHasta : null,
  }
}

// El perfil que devuelven /2fa/verificar y /usuarios/yo (UsuarioResponseDTO) en el formato de la sesión.
const desdePerfil = (perfil) => ({ correo: normalizar(perfil.correo), rol: enMinusculas(perfil.rol), nombre: perfil.nombre })

// 401 o 403 durante el paso del código: se cierra todo y la pantalla vuelve al login con el motivo.
async function traducirErrorDelPaso2(error) {
  const acceso = errorDeAcceso(error)
  return acceso ? cerrarPorAcceso(acceso) : traducirError2FA(error)
}

// Paso 2: código de 6 dígitos. Errores: SIN_SOLICITUD, BLOQUEADO (msRestantes), FORMATO, VENCIDO, INCORRECTO
// (intentosRestantes, contados aquí), INACTIVA, SESION_INVALIDA y SIN_ROL. El perfil sale de la respuesta.
// El segundo parámetro del contrato ({ recordar }) se ignora a propósito: PENDIENTE backend (equipo de
// confianza), no se envía nada.
export async function verificarCodigo(codigo) {
  if (!/^\d{6}$/.test(codigo)) throw fallo('FORMATO')
  let perfil
  try {
    perfil = await apiRequest('/auth/2fa/verificar', { method: 'POST', body: JSON.stringify({ codigo }) })
  } catch (error) {
    throw await traducirErrorDelPaso2(error)
  }
  if (perfil.activo === false) throw await cerrarPorAcceso(fallo('INACTIVA'))
  guardar(null)
  return desdePerfil(perfil)
}

// Reenvío del código (uno por minuto). Errores: BLOQUEADO, DEMASIADO_PRONTO (msRestantes).
export async function reenviarCodigo() {
  let respuesta
  try {
    respuesta = await apiRequest('/auth/2fa/reenviar', { method: 'POST' })
  } catch (error) {
    throw await traducirErrorDelPaso2(error)
  }
  const reenvioDisponibleEn = Date.now() + respuesta.reenvioDisponibleEn
  const solicitud = leer()
  if (solicitud) guardar({ ...solicitud, reenvioDisponibleEn, intentos: 0, bloqueoHasta: null })
  return { reenvioDisponibleEn }
}

// "Volver al inicio de sesión" (sin código verificado): descarta la solicitud y cierra la sesión de Supabase.
export function cancelarSolicitud() {
  guardar(null)
  return cerrarSupabase({ scope: 'local' })
}

// POST /api/auth/logout (204): el backend deja de considerar verificada la sesión de este token. Va ANTES de cerrar
// Supabase, porque necesita el token. Cualquier fallo se ignora (red, 401, 403 sin 2FA o 404 de un backend que
// todavía no tiene el endpoint): la sesión de Supabase se cierra igual.
async function avisarLogout() {
  try {
    const { data } = await supabase.auth.getSession()
    if (!data?.session?.access_token) return
    await apiRequest('/auth/logout', { method: 'POST' })
  } catch {
    // se cierra de todos modos
  }
}

// Cierre de sesión de este equipo: avisa al backend y luego cierra la sesión de Supabase.
export async function cerrarSesion() {
  guardar(null)
  await avisarLogout()
  await cerrarSupabase({ scope: 'local' })
}

// Cierre en todos los equipos: avisa al backend por esta sesión e invalida los refresh tokens de la cuenta en Supabase.
export async function cerrarSesionGlobal() {
  guardar(null)
  await avisarLogout()
  await cerrarSupabase({ scope: 'global' })
}

// PENDIENTE backend: equipo de confianza ("Recordar este equipo"). Todavía no existe.
export const estadoEquipo = () => null
export const dejarDeRecordarEquipo = () => {}

// Con la sesión verificada en esta pestaña: GET /api/usuarios/yo actualiza el perfil (un cambio de rol surte efecto
// al recargar). Si el backend dice que la sesión ya no está verificada (403 2FA_REQUERIDA: venció o se cerró en el
// servidor), se vuelve a pedir el código. Sin red se conserva la sesión tal como está.
async function perfilVerificado(sesion) {
  try {
    const perfil = await apiRequest('/usuarios/yo')
    if (perfil.activo === false) return cerrarPorAcceso(null)
    return { estado: 'verificada', usuario: desdePerfil(perfil) }
  } catch (error) {
    if (error.estado === 403 && error.codigo === '2FA_REQUERIDA') return pendienteDeCodigo(sesion)
    if (error.estado === 401 || errorDeAcceso(error)) return cerrarPorAcceso(null)
    return { estado: 'verificada' }
  }
}

// Al cargar la página: ¿hay sesión de Supabase? Devuelve null si no. Si la hay y esta pestaña no completó el 2FA
// (`verificada` es false), deja lista la solicitud del código y devuelve el usuario con estado 'pendiente'.
export async function restaurarSesion({ verificada }) {
  const { data } = await supabase.auth.getSession()
  const sesion = data?.session
  if (!sesion) {
    guardar(null)
    return null
  }
  if (verificada) return perfilVerificado(sesion)
  return pendienteDeCodigo(sesion)
}

async function pendienteDeCodigo(sesion) {
  const previa = leer()
  const correo = normalizar(sesion.user.email)
  if (previa?.correo === correo) return { estado: 'pendiente', usuario: { correo, rol: previa.rol, nombre: previa.nombre } }
  try {
    await pedirCodigo({ correo, rol: null, nombre: '' })
  } catch {
    await cerrarSupabase({ scope: 'local' })
    return null
  }
  return { estado: 'pendiente', usuario: { correo, rol: null, nombre: '' } }
}

// Avisa cuando la sesión de Supabase se cierra por fuera (otra pestaña, refresh token vencido). Devuelve la baja.
export function alCerrarSesionExterna(funcion) {
  const { data } = supabase.auth.onAuthStateChange((evento) => {
    if (evento === 'SIGNED_OUT') funcion()
  })
  return () => data.subscription.unsubscribe()
}
