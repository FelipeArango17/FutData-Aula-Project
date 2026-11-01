import Button from './Button.jsx'

// Control de paginación: Anterior / Siguiente, "Página X de Y" y "Mostrando 11–20 de 35".
// Recibe lo que devuelve paginar() (pagina, totalPaginas, desde, hasta, total) y onCambiar(nuevaPagina).
// No se dibuja si hay una sola página. `aria-current="page"` marca la página actual.
export default function Paginacion({ pagina, totalPaginas, desde, hasta, total, onCambiar }) {
  if (totalPaginas <= 1) return null
  return (
    <nav aria-label="Paginación" className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center sm:justify-between">
      <p className="text-small text-secondary">Mostrando {desde}–{hasta} de {total}</p>
      <div className="flex flex-wrap items-center gap-3">
        <Button variant="secondary" disabled={pagina <= 1} onClick={() => onCambiar(pagina - 1)} className="disabled:cursor-not-allowed disabled:opacity-60">
          Anterior
        </Button>
        <span aria-current="page" className="text-body-medium">Página {pagina} de {totalPaginas}</span>
        <Button variant="secondary" disabled={pagina >= totalPaginas} onClick={() => onCambiar(pagina + 1)} className="disabled:cursor-not-allowed disabled:opacity-60">
          Siguiente
        </Button>
      </div>
    </nav>
  )
}
