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
  // respuesta: 'blob' devuelve el cuerpo binario (por ejemplo un PDF); por defecto se lee JSON
  const { headers: extra, respuesta: tipoRespuesta, ...resto } = opciones
  // FormData: el navegador pone Content-Type con el boundary; JSON: se declara aquí. Quien llama pasa el body ya armado.
  const esFormData = typeof FormData !== 'undefined' && resto.body instanceof FormData
  const headers = { ...(resto.body === undefined || esFormData ? {} : { 'Content-Type': 'application/json' }), ...extra }
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
  if (respuesta.status === 204) return null
  return tipoRespuesta === 'blob' ? respuesta.blob() : respuesta.json()
}

// Sube un archivo como multipart/form-data. `campos` son los demás campos del formulario (los null/undefined se omiten).
// El nombre del campo del archivo es "archivo" (PENDIENTE: confirmar con el backend).
export function subirArchivo(ruta, archivo, campos = {}, { metodo = 'POST', nombreCampo = 'archivo' } = {}) {
  const datos = new FormData()
  datos.append(nombreCampo, archivo)
  for (const [clave, valor] of Object.entries(campos)) if (valor !== undefined && valor !== null) datos.append(clave, valor)
  return apiRequest(ruta, { method: metodo, body: datos })
}

// Lista paginada: GET ruta?page=&size=&sort=&...filtros. Devuelve { items, total, pagina, tamano } aceptando la forma de
// Spring Page ({ content, totalElements, number, size }) y la forma { items, total }. Si el contrato final es otro, solo cambia
// esta función. `page` empieza en 0 (como Spring); `pagina` devuelve el mismo número.
export function normalizarPagina(cuerpo, { page = 0, size } = {}) {
  const items = cuerpo?.content ?? cuerpo?.items ?? (Array.isArray(cuerpo) ? cuerpo : [])
  return {
    items,
    total: cuerpo?.totalElements ?? cuerpo?.total ?? items.length,
    pagina: cuerpo?.number ?? cuerpo?.page ?? cuerpo?.pagina ?? page,
    tamano: cuerpo?.size ?? cuerpo?.tamano ?? size ?? items.length,
  }
}

export async function apiListaPaginada(ruta, { page = 0, size, sort, ...filtros } = {}) {
  const query = new URLSearchParams()
  query.set('page', String(page))
  if (size !== undefined) query.set('size', String(size))
  if (sort) query.set('sort', sort)
  for (const [clave, valor] of Object.entries(filtros)) if (valor !== undefined && valor !== null && valor !== '') query.set(clave, String(valor))
  const cuerpo = await apiRequest(`${ruta}${ruta.includes('?') ? '&' : '?'}${query}`)
  return normalizarPagina(cuerpo, { page, size })
}
