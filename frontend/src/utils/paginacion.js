// Paginación del lado del cliente. Función pura: no toca la lista original.
export const TAMANO_PAGINA = 10

// pagina empieza en 1 y se ajusta al rango válido. Una lista vacía tiene 1 página, con desde = 0 y hasta = 0.
// desde y hasta son posiciones 1-based para el texto "Mostrando 11–20 de 35".
export function paginar(items, pagina, tamano = TAMANO_PAGINA) {
  const total = items.length
  const totalPaginas = Math.max(1, Math.ceil(total / tamano))
  const actual = Math.min(Math.max(1, Math.trunc(Number(pagina)) || 1), totalPaginas)
  const inicio = (actual - 1) * tamano
  const recorte = items.slice(inicio, inicio + tamano)
  return {
    items: recorte,
    pagina: actual,
    totalPaginas,
    desde: total === 0 ? 0 : inicio + 1,
    hasta: inicio + recorte.length,
    total,
  }
}
