import { describe, expect, it } from 'vitest'
import { TAMANO_PAGINA, paginar } from './paginacion.js'

const lista = (n) => Array.from({ length: n }, (_, i) => i + 1)

describe('paginar', () => {
  it('el tamaño de página es 10', () => {
    expect(TAMANO_PAGINA).toBe(10)
  })

  it('primera página de 35: del 1 al 10, 4 páginas', () => {
    expect(paginar(lista(35), 1, 10)).toEqual({ items: lista(10), pagina: 1, totalPaginas: 4, desde: 1, hasta: 10, total: 35 })
  })

  it('segunda página: "11–20 de 35"', () => {
    const r = paginar(lista(35), 2, 10)
    expect(r.items).toEqual([11, 12, 13, 14, 15, 16, 17, 18, 19, 20])
    expect([r.desde, r.hasta, r.total]).toEqual([11, 20, 35])
  })

  it('última página incompleta', () => {
    const r = paginar(lista(35), 4, 10)
    expect(r.items).toEqual([31, 32, 33, 34, 35])
    expect([r.desde, r.hasta]).toEqual([31, 35])
  })

  it('una cantidad exacta no crea una página vacía', () => {
    expect(paginar(lista(20), 2, 10).totalPaginas).toBe(2)
    expect(paginar(lista(10), 1, 10).totalPaginas).toBe(1)
  })

  it('ajusta la página al rango válido (0, negativa, pasada de largo, texto)', () => {
    expect(paginar(lista(35), 0, 10).pagina).toBe(1)
    expect(paginar(lista(35), -3, 10).pagina).toBe(1)
    expect(paginar(lista(35), 99, 10).pagina).toBe(4)
    expect(paginar(lista(35), 'x', 10).pagina).toBe(1)
    expect(paginar(lista(35), 2.7, 10).pagina).toBe(2)
  })

  it('lista vacía: 1 página, desde 0 y hasta 0', () => {
    expect(paginar([], 1, 10)).toEqual({ items: [], pagina: 1, totalPaginas: 1, desde: 0, hasta: 0, total: 0 })
    expect(paginar([], 5, 10).pagina).toBe(1)
  })

  it('por defecto usa TAMANO_PAGINA y no modifica la lista original', () => {
    const original = lista(25)
    expect(paginar(original, 3).items).toHaveLength(5)
    expect(original).toEqual(lista(25))
  })
})
