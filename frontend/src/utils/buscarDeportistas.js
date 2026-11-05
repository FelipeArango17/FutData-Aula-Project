// Búsqueda de deportistas en el navegador (el backend no busca por texto). Sin React ni red, para poder probarla.
export const MAX_RESULTADOS = 30

// Sin tildes ni mayúsculas, con los espacios sobrantes quitados: "Londoño " == "londono"
const sinAcentos = (t) => String(t ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim()
// Documento comparable: sin puntos, espacios ni guiones ("1.098.765-432" == "1098765432")
const soloDocumento = (t) => String(t ?? '').replace(/[\s.-]/g, '').toLowerCase()

// Un deportista coincide si TODAS las palabras del texto están en "nombre apellido" (en cualquier orden) o si el texto,
// sin puntos, espacios ni guiones, está dentro de su documento.
function coincide(deportista, palabras, documentoBuscado) {
  const nombre = sinAcentos(`${deportista.nombre} ${deportista.apellido}`)
  if (palabras.every((p) => nombre.includes(p))) return true
  return documentoBuscado !== '' && soloDocumento(deportista.documento).includes(documentoBuscado)
}

// Qué elige Enter: la opción resaltada con las flechas (`activa` >= 0) o, sin flechas, la única que hay. Con varias y sin flecha, ninguna.
export function opcionAlPulsarEnter(resultados, activa) {
  if (activa >= 0 && resultados[activa]) return resultados[activa]
  return activa < 0 && resultados.length === 1 ? resultados[0] : null
}

// -> { resultados (máximo `limite`, en el orden de la lista), total (cuántos coincidieron en total) }
// Sin texto devuelve las primeras opciones de la lista.
export function buscarDeportistas(lista, texto, limite = MAX_RESULTADOS) {
  const todos = lista ?? []
  const buscado = sinAcentos(texto)
  if (!buscado) return { resultados: todos.slice(0, limite), total: todos.length }
  const palabras = buscado.split(/\s+/)
  const documentoBuscado = soloDocumento(buscado)
  const coincidentes = todos.filter((d) => coincide(d, palabras, documentoBuscado))
  return { resultados: coincidentes.slice(0, limite), total: coincidentes.length }
}
