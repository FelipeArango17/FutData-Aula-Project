// Categoría de un deportista según su AÑO de nacimiento (no la edad exacta).
// PROVISIONAL: regla del club por confirmar. edad = año de hoy − año de nacimiento.
// No se crean categorías nuevas: quien nazca fuera del rango no se puede inscribir. Se recalcula cada año con el año actual.
import { hoyLocal } from './fechas.js'

export const CATEGORIAS_POR_EDAD = [
  { nombre: 'Sub-8', edadMinima: 8, edadMaxima: 8 },
  { nombre: 'Sub-10', edadMinima: 9, edadMaxima: 10 },
  { nombre: 'Sub-12', edadMinima: 11, edadMaxima: 12 },
  { nombre: 'Sub-14', edadMinima: 13, edadMaxima: 14 },
  { nombre: 'Sub-16', edadMinima: 15, edadMaxima: 16 },
]

const anioDe = (fecha) => Number(String(fecha).slice(0, 4))

// Edad por año: año de `hoy` menos año de nacimiento ("AAAA-MM-DD"). No mira el mes ni el día.
export const edadPorAnio = (fechaNacimiento, hoy = hoyLocal()) => anioDe(hoy) - anioDe(fechaNacimiento)

// Nombre de la categoría que corresponde a la fecha, o null si la fecha no está completa o queda fuera de la tabla.
export function categoriaPorFecha(fechaNacimiento, hoy = hoyLocal()) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(String(fechaNacimiento ?? ''))) return null
  const edad = edadPorAnio(fechaNacimiento, hoy)
  return CATEGORIAS_POR_EDAD.find((c) => edad >= c.edadMinima && edad <= c.edadMaxima)?.nombre ?? null
}

// Años de nacimiento que se pueden inscribir hoy (en 2026: 2010 a 2018)
export function rangoAniosNacimiento(hoy = hoyLocal()) {
  const edades = CATEGORIAS_POR_EDAD.flatMap((c) => [c.edadMinima, c.edadMaxima])
  return { anioMinimo: anioDe(hoy) - Math.max(...edades), anioMaximo: anioDe(hoy) - Math.min(...edades) }
}
