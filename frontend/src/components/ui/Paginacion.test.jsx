import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import Paginacion from './Paginacion.jsx'

const dibujar = (props) => renderToStaticMarkup(<Paginacion pagina={1} totalPaginas={4} desde={1} hasta={10} total={35} onCambiar={() => {}} {...props} />)

describe('Paginacion', () => {
  it('es un nav con nombre y muestra los textos de posición', () => {
    const html = dibujar({ pagina: 2, desde: 11, hasta: 20 })
    expect(html).toMatch(/<nav[^>]*aria-label="Paginación"/)
    expect(html).toContain('Página 2 de 4')
    expect(html).toContain('Mostrando 11–20 de 35')
    expect(html).toMatch(/aria-current="page"[^>]*>Página 2 de 4/)
  })

  it('usa botones reales con objetivo táctil de 44 px y foco visible', () => {
    const html = dibujar({ pagina: 2 })
    expect(html.match(/<button /g)).toHaveLength(2)
    expect(html).toContain('min-h-11')
    expect(html).toContain('focus-visible:ring-2')
    expect(html).toContain('>Anterior</button>')
    expect(html).toContain('>Siguiente</button>')
  })

  it('en la primera página "Anterior" está deshabilitado y "Siguiente" no', () => {
    const html = dibujar({ pagina: 1 })
    expect(html).toMatch(/<button[^>]*disabled=""[^>]*>Anterior<\/button>/)
    expect(html).not.toMatch(/<button[^>]*disabled=""[^>]*>Siguiente<\/button>/)
  })

  it('en la última página "Siguiente" está deshabilitado y "Anterior" no', () => {
    const html = dibujar({ pagina: 4, desde: 31, hasta: 35 })
    expect(html).toMatch(/<button[^>]*disabled=""[^>]*>Siguiente<\/button>/)
    expect(html).not.toMatch(/<button[^>]*disabled=""[^>]*>Anterior<\/button>/)
  })

  it('con una sola página no se dibuja nada', () => {
    expect(dibujar({ totalPaginas: 1, desde: 1, hasta: 5, total: 5 })).toBe('')
  })
})
