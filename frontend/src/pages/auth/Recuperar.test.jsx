import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import Recuperar from './Recuperar.jsx'

const html = renderToStaticMarkup(<MemoryRouter><Recuperar /></MemoryRouter>)

describe('/recuperar (render)', () => {
  it('título, campo de correo con etiqueta y un solo botón "Enviar enlace" habilitado', () => {
    expect(html).toContain('Recuperar contraseña')
    expect(html).toMatch(/<label[^>]*for="correo"[^>]*>Correo electrónico/)
    expect(html).toMatch(/type="email"/)
    expect(html.match(/type="submit"/g)).toHaveLength(1)
    expect(html).toContain('Enviar enlace</button>')
    expect(html).not.toMatch(/<button[^>]*disabled/)
  })
  it('enlace para volver al inicio de sesión y sin avisos de entrada', () => {
    expect(html).toContain('href="/login"')
    expect(html).toContain('Volver al inicio de sesión')
    expect(html).not.toContain('Si el correo está registrado')
    expect(html).not.toContain('Podrás pedir otro enlace')
  })
})
