// Sesión de la persona en sessionStorage (se borra al cerrar la pestaña). Funciones puras de almacenamiento, sin React,
// para poder probarlas. PENDIENTE: con Supabase Auth la sesión la gestiona su SDK; este estado local desaparece.
import * as servicio from '../services/authService.js'

export const CLAVE_SESION = 'futdata.sesion'
export const ESTADOS = ['sin-sesion', 'pendiente', 'verificada']
export const VACIA = { estado: 'sin-sesion', usuario: null }

export function leerSesion() {
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

// Cierra la sesión de ESTE equipo: borra la clave y devuelve el estado vacío. No toca la marca de equipo de confianza
// (futdata.equipoConfiable) ni el rol de desarrollo (futdata.rol).
export function limpiarSesion() {
  try {
    sessionStorage.removeItem(CLAVE_SESION)
  } catch {
    // sin almacenamiento
  }
  return VACIA
}
