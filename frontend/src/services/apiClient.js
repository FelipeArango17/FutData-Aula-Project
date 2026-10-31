// Cliente HTTP único del backend. Añade el access_token de la sesión de Supabase como `Authorization: Bearer`,
// envía cookies (`credentials: 'include'`, para la cookie de "Recordar este equipo" cuando exista) y convierte
// cualquier fallo en un Error con { estado, mensaje, campos, codigo }:
//   estado   código HTTP (0 si no hubo respuesta)
//   mensaje  texto en español para mostrar (el campo `mensaje` del cuerpo de error de la API)
//   campos   campos con error o en conflicto (lista, vacía si no aplica)
//   codigo   'RED' (sin respuesta), 'SESION' (401), 'PERMISOS' (403) o el `codigo` que traiga el cuerpo de la API
import { supabase } from './supabaseClient.js'

// Misma lógica que tenía entrenadoresService: VITE_API_URL puede traer o no el sufijo /api.
export const API_BASE = (import.meta.env.VITE_API_URL ?? '').replace(/\/api\/?$/, '')

const MENSAJE_GENERICO = 'No se pudo completar la operación.'

// Qué hacer ante un 401 (sesión vencida o inválida). AuthProvider registra el suyo: limpia el estado de React y las
// rutas protegidas llevan a /login sin recargar la página. Sin ninguno registrado se navega a /login.
let alNoAutorizado = null
export function configurarAlNoAutorizado(funcion) {
  alNoAutorizado = funcion ?? null
}

async function tokenActual() {
  if (!supabase) return null
  try {
    const { data } = await supabase.auth.getSession()
    return data?.session?.access_token ?? null
  } catch {
    return null
  }
}

async function sesionVencida() {
  try {
    await supabase?.auth.signOut({ scope: 'local' })
  } catch {
    // la sesión local se descarta de todos modos
  }
  if (alNoAutorizado) alNoAutorizado()
  else if (typeof window !== 'undefined' && window.location.pathname !== '/login') window.location.assign('/login')
}

function crearError(estado, mensaje, campos = [], codigo = null, extra = {}) {
  return Object.assign(new Error(mensaje), { estado, mensaje, campos, codigo, ...extra })
}

export async function apiRequest(ruta, opciones = {}) {
  const { headers: extra, ...resto } = opciones
  const headers = { ...(resto.body === undefined ? {} : { 'Content-Type': 'application/json' }), ...extra }
  const token = await tokenActual()
  if (token) headers.Authorization = `Bearer ${token}`

  let respuesta
  try {
    respuesta = await fetch(`${API_BASE}/api${ruta}`, { ...resto, headers, credentials: 'include' })
  } catch {
    throw crearError(0, 'No pudimos conectar con el servidor. Intenta de nuevo.', [], 'RED')
  }

  if (!respuesta.ok) {
    const cuerpo = await respuesta.json().catch(() => ({}))
    const mensaje = cuerpo.mensaje ?? MENSAJE_GENERICO
    const campos = Array.isArray(cuerpo.campos) ? cuerpo.campos : []
    if (respuesta.status === 401) {
      await sesionVencida()
      throw crearError(401, mensaje, campos, cuerpo.codigo ?? 'SESION')
    }
    if (respuesta.status === 403) throw crearError(403, mensaje, campos, cuerpo.codigo ?? 'PERMISOS')
    const extras = cuerpo.msRestantes === undefined ? {} : { msRestantes: cuerpo.msRestantes }
    throw crearError(respuesta.status, mensaje, campos, cuerpo.codigo ?? null, extras)
  }
  return respuesta.status === 204 ? null : respuesta.json()
}
