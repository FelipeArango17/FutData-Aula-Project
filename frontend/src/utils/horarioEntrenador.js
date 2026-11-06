// Lógica pura de "Horarios" para el entrenador en modo API. Sin React ni red.
// Perfil: GET /api/usuarios/yo -> { idEntrenador, categorias: [{ idCategoria, nombre }] } (ver adaptarPerfilEntrenador), o null si la
// cuenta no tiene un entrenador vinculado. Las franjas (GET /api/horario-club) traen idCategoria, categoria, idEntrenador y entrenador.
import { NOMBRES_CATEGORIAS } from './categorias.js'

export const VISTA_MIS = 'Mis entrenamientos'
export const VISTA_CLUB = 'Todo el club'
export const TODAS = 'Todas'

// Qué ofrece la pantalla según el perfil:
//   'cargando' | 'error' (todo el club + reintento) | 'sin-categorias' (todo el club + aviso, sin interruptor) | 'ok' (interruptor)
export function estadoPerfil({ cargando = false, error = false, perfil = null } = {}) {
  if (cargando) return 'cargando'
  if (error) return 'error'
  return (perfil?.categorias?.length ?? 0) > 0 ? 'ok' : 'sin-categorias'
}

// Botones de categoría: en "Mis entrenamientos" solo las del entrenador (en el orden oficial); si no, todas las del club.
export function opcionesDeCategoria({ soloMias, perfil }) {
  if (!soloMias) return [TODAS, ...NOMBRES_CATEGORIAS]
  const nombres = (perfil?.categorias ?? []).map((c) => c.nombre)
  return [TODAS, ...NOMBRES_CATEGORIAS.filter((n) => nombres.includes(n)), ...nombres.filter((n) => !NOMBRES_CATEGORIAS.includes(n))]
}

// ¿La sesión es de una de las categorías del entrenador? Se compara por idCategoria (no por nombre).
export const esDeMisCategorias = (sesion, perfil) =>
  (perfil?.categorias ?? []).some((c) => String(c.idCategoria) === String(sesion.idCategoria))

// ¿La sesión es del entrenador? Una de sus categorías ("Tu categoría") o una franja que él dirige ("Tú").
export const esDeEntrenador = (sesion, perfil) =>
  esDeMisCategorias(sesion, perfil) || (Boolean(perfil?.idEntrenador) && String(sesion.idEntrenador ?? '') === String(perfil.idEntrenador))
