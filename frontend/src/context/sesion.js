// Sesión de la persona en sessionStorage (se borra al cerrar la pestaña). Funciones puras de almacenamiento, sin React,
// para poder probarlas. Con Supabase Auth (servicio real) el SDK guarda los tokens; este estado solo recuerda si ESTA pestaña completó el 2FA.
import { servicio, sinConfiguracion } from '../services/auth.js'

export const CLAVE_SESION = 'futdata.sesion'
export const ESTADOS = ['sin-sesion', 'pendiente', 'verificada']
export const VACIA = { estado: 'sin-sesion', usuario: null }

export function leerSesion() {
  if (sinConfiguracion()) return VACIA // sin configuración de producción no vale ninguna sesión guardada (podría ser una demo)
  try {
    const guardada = JSON.parse(sessionStorage.getItem(CLAVE_SESION))
    // Un estado "pendiente" solo vale si el servicio todavía tiene la solicitud de código
    if (guardada && ESTADOS.includes(guardada.estado) && guardada.usuario) {
      if (guardada.estado === 'pendiente' && !servicio.estadoCodigo()) return VACIA
      return guardada
    }
  } catch {
    // sin almacenamiento o dato dañado
  }
  return VACIA
}

export function guardarSesion(sesion) {
  try {
    sessionStorage.setItem(CLAVE_SESION, JSON.stringify(sesion))
  } catch {
    // sin almacenamiento: la sesión dura mientras la página siga abierta
  }
}

// Cierra la sesión de ESTE equipo: borra la clave y el rol de desarrollo (futdata.rol) y devuelve el estado vacío. No toca
// la marca de equipo de confianza (futdata.equipoConfiable).
export function limpiarSesion() {
  try {
    sessionStorage.removeItem(CLAVE_SESION)
  } catch {
    // sin almacenamiento
  }
  try {
    localStorage.removeItem('futdata.rol')
  } catch {
    // sin almacenamiento
  }
  return VACIA
}
