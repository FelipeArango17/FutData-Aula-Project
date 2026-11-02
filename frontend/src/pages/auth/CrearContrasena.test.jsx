import { describe, expect, it, vi } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import CrearContrasena from './CrearContrasena.jsx'

const crearContrasena = vi.hoisted(() => vi.fn())
vi.mock('../../services/entrenadoresService.js', () => ({ crearContrasena }))

const dibujar = (url) => renderToStaticMarkup(
  <MemoryRouter initialEntries={[url]}>
    <Routes><Route path="/crear-contrasena" element={<CrearContrasena />} /></Routes>
  </MemoryRouter>,
)

describe('/crear-contrasena (render)', () => {
  it('sin token: enlace inválido de inmediato, sin formulario y sin llamar a la API', () => {
    for (const url of ['/crear-contrasena', '/crear-contrasena?token=', '/crear-contrasena?token=%20']) {
      const html = dibujar(url)
      expect(html).toContain('El enlace no es válido o venció; pide al administrador que te lo reenvíe')
      expect(html).not.toContain('<form')
    }
    expect(crearContrasena).not.toHaveBeenCalled()
  })

  it('con token: dos campos de contraseña con etiqueta, ocultos por defecto, con botones de mostrar y botón habilitado', () => {
    const html = dibujar('/crear-contrasena?token=abc')
    expect(html).toMatch(/<label[^>]*for="contrasena"[^>]*>Contraseña/)
    expect(html).toMatch(/<label[^>]*for="confirmar"[^>]*>Confirmar contraseña/)
    expect(html.match(/type="password"/g)).toHaveLength(2)
    expect(html).toContain('aria-label="Mostrar contraseña"')
    expect(html).toContain('aria-label="Mostrar confirmación de contraseña"')
    expect(html).toContain('Crear contraseña</button>')
    expect(html).not.toMatch(/<button[^>]*disabled[^>]*>Crear contraseña/)
    expect(html).not.toContain('abc') // el token no se pinta en la pantalla
    expect(crearContrasena).not.toHaveBeenCalled()
  })
})
