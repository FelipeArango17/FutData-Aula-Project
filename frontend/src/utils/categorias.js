// Categoría de un deportista según su AÑO de nacimiento (no la edad exacta): categorías reales del club, horario 2026.
// edad = año de hoy − año de nacimiento. Párvulos 4 a 6, Cachorros 7 a 8, Semillero 9 a 10 y Sub-N (N = 11 a 16) cuando la edad es N.
// Solo se inscriben nacidos entre año(hoy) − 16 y año(hoy) − 4; no hay más categorías. Se recalcula cada año con el año actual.
import { hoyLocal } from './fechas.js'

export const CATEGORIAS_POR_EDAD = [
  { nombre: 'Párvulos', edadMinima: 4, edadMaxima: 6 },
  { nombre: 'Cachorros', edadMinima: 7, edadMaxima: 8 },
  { nombre: 'Semillero', edadMinima: 9, edadMaxima: 10 },
  ...Array.from({ length: 6 }, (_, i) => ({ nombre: `Sub-${11 + i}`, edadMinima: 11 + i, edadMaxima: 11 + i })),
]

// Nombres en el orden oficial (de menor a mayor). Todas las listas, filtros y selectores de categorías salen de aquí.
export const NOMBRES_CATEGORIAS = CATEGORIAS_POR_EDAD.map((c) => c.nombre)

const anioDe = (fecha) => Number(String(fecha).slice(0, 4))

// Edad por año: año de `hoy` menos año de nacimiento ("AAAA-MM-DD"). No mira el mes ni el día.
export const edadPorAnio = (fechaNacimiento, hoy = hoyLocal()) => anioDe(hoy) - anioDe(fechaNacimiento)

// Nombre de la categoría que corresponde a la fecha, o null si la fecha no está completa o queda fuera de la tabla.
export function categoriaPorFecha(fechaNacimiento, hoy = hoyLocal()) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(String(fechaNacimiento ?? ''))) return null
  const edad = edadPorAnio(fechaNacimiento, hoy)
  return CATEGORIAS_POR_EDAD.find((c) => edad >= c.edadMinima && edad <= c.edadMaxima)?.nombre ?? null
}

// Años de nacimiento que se pueden inscribir hoy (en 2026: 2010 a 2022)
export function rangoAniosNacimiento(hoy = hoyLocal()) {
  const edades = CATEGORIAS_POR_EDAD.flatMap((c) => [c.edadMinima, c.edadMaxima])
  return { anioMinimo: anioDe(hoy) - Math.max(...edades), anioMaximo: anioDe(hoy) - Math.min(...edades) }
}

// idCategoria (texto, '' = sin asignar) con el que se guarda el formulario del deportista. Al editar, si la fecha de nacimiento
// no cambió se conserva la categoría que ya tenía; si cambió (o es un deportista nuevo) se busca por nombre, entre
// `categorias` ([{ id, nombre }]), la que da la fecha. Si no hay una, queda sin asignar.
export function idCategoriaAlGuardar(fechaNacimiento, inicial, categorias, hoy = hoyLocal()) {
  if (inicial?.fechaNacimiento && fechaNacimiento === inicial.fechaNacimiento) return String(inicial.idCategoria ?? '')
  const categoria = categorias.find((c) => c.nombre === categoriaPorFecha(fechaNacimiento, hoy))
  return categoria ? String(categoria.id) : ''
}

// Aviso corto para las listas: la edad que se ve es la cumplida, la categoría sale del año
export const NOTA_CATEGORIA_POR_ANIO = 'La categoría se define por año de nacimiento, no por edad cumplida.'

// Grupo de entrenamiento: solo Cachorros y Semillero lo tienen (las dos sesiones alternativas de martes y jueves: mañana o tarde,
// según el horario del colegio). El club decidió que el grupo no se guarda en la base de datos: vive solo en el frontend de ejemplo.
export const CATEGORIAS_CON_GRUPO = ['Cachorros', 'Semillero']
export const GRUPOS_ENTRENAMIENTO = ['mañana', 'tarde']
export const ETIQUETA_GRUPO = 'Grupo de entrenamiento'

// ¿Esta categoría (por nombre) exige grupo?
export const categoriaRequiereGrupo = (nombreCategoria) => CATEGORIAS_CON_GRUPO.includes(nombreCategoria)

// El grupo que se conserva al cambiar de categoría: el mismo si la nueva categoría lo exige, '' (vacío) si no
export const grupoSegunCategoria = (grupo, nombreCategoria) => (categoriaRequiereGrupo(nombreCategoria) ? grupo ?? '' : '')

// Texto para listas y fichas: "Semillero · mañana", o solo el nombre ("Sub-12") si no hay grupo; "Sin asignar" sin categoría
export function textoCategoriaConGrupo(nombreCategoria, grupo) {
  if (!nombreCategoria) return 'Sin asignar'
  return categoriaRequiereGrupo(nombreCategoria) && grupo ? `${nombreCategoria} · ${grupo}` : nombreCategoria
}
