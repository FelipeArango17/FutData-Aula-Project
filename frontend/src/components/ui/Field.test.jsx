import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import Field from './Field.jsx'

describe('Field (campo obligatorio)', () => {
  it('con requerido muestra el asterisco oculto a lectores de pantalla y marca aria-required', () => {
    const html = renderToStaticMarkup(<Field label="Nombre" id="nombre" requerido />)
    expect(html).toMatch(/<label[^>]*for="nombre"[^>]*>Nombre<span[^>]*aria-hidden="true"[^>]*> \*<\/span><\/label>/)
    expect(html).toContain('text-danger')
    expect(html).toContain('aria-required="true"')
  })

  it('sin requerido no muestra asterisco ni aria-required', () => {
    const html = renderToStaticMarkup(<Field label="Dirección (opcional)" id="direccion" />)
    expect(html).not.toContain('*')
    expect(html).not.toContain('aria-required')
  })
})
