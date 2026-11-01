// Decisión de acceso a las pantallas protegidas. Funciones puras (sin React) para poder probarlas.
//
// decidirAcceso({ estado, rolUsuario, rolRuta }) -> 'cargando' | 'login' | 'verificar' | 'permitir' | 'otro-rol'
//   estado: 'restaurando' (la sesión todavía no se comprobó) | 'sin-sesion' | 'pendiente' (falta el código 2FA) | 'verificada'
//   rolUsuario: rol de la sesión verificada; rolRuta: rol que pide la ruta.
// Cualquier estado desconocido se trata como "sin sesión": ante la duda, no se entra.
export function decidirAcceso({ estado, rolUsuario, rolRuta }) {
  if (estado === 'restaurando') return 'cargando'
  if (estado === 'pendiente') return 'verificar'
  if (estado !== 'verificada') return 'login'
  return rolUsuario === rolRuta ? 'permitir' : 'otro-rol'
}

// A dónde mandar según la decisión (null = no hay redirección). Sin sesión y pendiente guardan la ruta pedida en state.desde.
export function destinoDeAcceso(decision, { rolUsuario, desde }) {
  if (decision === 'login') return { to: '/login', state: { desde } }
  if (decision === 'verificar') return { to: '/verificar', state: { desde } }
  if (decision === 'otro-rol') return { to: `/${rolUsuario}`, state: undefined }
  return null
}

// Ruta a la que volver tras iniciar sesión. Solo vale una ruta interna del rol de la persona (o /perfil); cualquier otra
// cosa (otro rol, otro sitio, "//host") se descarta para no abrir un redireccionamiento hacia fuera ni a un panel ajeno.
export function rutaDeRetorno(desde, rol) {
  if (typeof desde !== 'string' || !rol) return null
  if (!desde.startsWith('/') || desde.startsWith('//') || desde.includes('\\')) return null
  const ruta = desde.split(/[?#]/)[0]
  if (ruta === '/perfil' || ruta === `/${rol}` || ruta.startsWith(`/${rol}/`)) return desde
  return null
}

// El selector de rol de desarrollo solo existe en desarrollo y con VITE_PERMITIR_CAMBIO_ROL === 'true'.
export const cambioDeRolPermitido = (env = import.meta.env) => Boolean(env?.DEV) && env?.VITE_PERMITIR_CAMBIO_ROL === 'true'
