// Lógica pura de las pantallas del entrenador con datos reales ("Inicio" y "Mis jugadores"). Sin React ni red.
// Fuentes: GET /api/usuarios/yo (entrenador: { idEntrenador, categorias: [{ idCategoria, nombre }] }), GET /api/deportistas?categoria=&activo=true
// y GET /api/horario-club. El entrenador no recibe documento, teléfono, dirección ni estado de inscripción: aquí no se usan.
import { semanaEnCurso, sesionesDeSemana } from './semana.js'

// El perfil de /usuarios/yo -> { idEntrenador, categorias: [{ idCategoria, nombre }] } (CategoriaYoDTO), o null si la cuenta no tiene un entrenador vinculado.
export function adaptarPerfilEntrenador(perfil) {
  const entrenador = perfil?.entrenador
  if (!entrenador?.idEntrenador) return null
  return {
    idEntrenador: entrenador.idEntrenador,
    categorias: (entrenador.categorias ?? []).map((c) => ({ idCategoria: c.idCategoria ?? c.id, nombre: c.nombre })),
  }
}

// Une varias listas de jugadores sin repetir ids (se queda con la primera aparición).
export function unirSinDuplicados(listas) {
  const vistos = new Set()
  const unidos = []
  for (const jugador of listas.flat()) {
    if (vistos.has(jugador.id)) continue
    vistos.add(jugador.id)
    unidos.push(jugador)
  }
  return unidos
}

// Categorías del entrenador con sus jugadores (sin repetidos) y el total de jugadores únicos.
// listas[i] son los jugadores que devolvió la API para categorias[i].
export function agruparPorCategoria(categorias, listas) {
  const conJugadores = categorias.map((categoria, i) => ({ ...categoria, jugadores: unirSinDuplicados([listas[i] ?? []]) }))
  return { categorias: conJugadores, jugadores: unirSinDuplicados(conJugadores.map((c) => c.jugadores)) }
}

export const TODAS_LAS_CATEGORIAS = 'Todas'

export const ORDENES_JUGADORES = [
  { valor: 'apellido', etiqueta: 'Apellido (A a Z)' },
  { valor: 'nombre', etiqueta: 'Nombre (A a Z)' },
  { valor: 'edad-asc', etiqueta: 'Edad (menor a mayor)' },
  { valor: 'edad-desc', etiqueta: 'Edad (mayor a menor)' },
  { valor: 'categoria', etiqueta: 'Categoría' },
]

const texto = (a, b) => String(a ?? '').localeCompare(String(b ?? ''), 'es', { sensitivity: 'base' })
const porApellido = (a, b) => texto(a.apellido, b.apellido) || texto(a.nombre, b.nombre)

// Orden local (no muta la lista). `categorias` = nombres en el orden oficial del entrenador, para el orden "Categoría".
export function ordenarJugadores(lista, orden = 'apellido', categorias = []) {
  const posicion = (j) => {
    const i = categorias.indexOf(j.categoria)
    return i === -1 ? categorias.length : i
  }
  const comparar = {
    apellido: porApellido,
    nombre: (a, b) => texto(a.nombre, b.nombre) || texto(a.apellido, b.apellido),
    'edad-asc': (a, b) => (a.edad ?? 0) - (b.edad ?? 0) || porApellido(a, b),
    'edad-desc': (a, b) => (b.edad ?? 0) - (a.edad ?? 0) || porApellido(a, b),
    categoria: (a, b) => posicion(a) - posicion(b) || porApellido(a, b),
  }[orden] ?? porApellido
  return [...lista].sort(comparar)
}

export const filtrarPorCategoria = (lista, nombre) =>
  nombre === TODAS_LAS_CATEGORIAS ? lista : lista.filter((j) => j.categoria === nombre)

export const TEXTO_SIN_POSICION = 'Sin posición definida'
export const textoEdad = (edad) => `${edad} ${edad === 1 ? 'año' : 'años'}`
export const textoJugadores = (n) => `${n} ${n === 1 ? 'jugador' : 'jugadores'}`
export const textoEntrenamientos = (n) => `${n} ${n === 1 ? 'entrenamiento' : 'entrenamientos'}`

// Entrenamientos de HOY y del resto de la semana en curso (lunes a sábado; el domingo ya cuenta la semana que viene), solo de las
// categorías del entrenador. `franjas` ya vienen adaptadas (adaptarFranja). Las cancelaciones y los extras aún no existen en la API.
export function sesionesDelEntrenador(franjas, categorias, hoy) {
  const nombres = new Set(categorias.map((c) => c.nombre))
  const sesiones = sesionesDeSemana(franjas.filter((f) => nombres.has(f.categoria)), [], semanaEnCurso(hoy))
    .filter((s) => s.estado !== 'CANCELADO' && s.fecha >= hoy)
  return {
    hoy: sesiones.filter((s) => s.fecha === hoy),
    semana: sesiones.filter((s) => s.fecha > hoy),
  }
}

// Mensaje para el usuario según el error de apiClient (estado HTTP; 0 = sin conexión).
export function mensajeDeErrorEntrenador(error, porDefecto) {
  const estado = error?.estado
  if (estado === 0 || error?.codigo === 'RED') return 'No pudimos conectar con el servidor. Revisa tu conexión e intenta de nuevo.'
  if (estado === 401) return 'Tu sesión venció. Inicia sesión de nuevo.'
  if (estado === 403) return 'No tienes permiso para ver esta información.'
  if (estado === 503) return 'El servicio no está disponible en este momento. Intenta de nuevo más tarde.'
  return porDefecto
}
