import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import Restablecer, { VistaRestablecer } from './Restablecer.jsx'

const dibujar = (elemento) => renderToStaticMarkup(<MemoryRouter>{elemento}</MemoryRouter>)
const vista = (fase) => dibujar(<VistaRestablecer fase={fase} restablecer={async () => {}} onListo={() => {}} />)

describe('/restablecer (render)', () => {
  it('al llegar espera la sesión de recuperación: "Verificando enlace…", sin formulario', () => {
    const html = dibujar(<Restablecer />)
    expect(html).toContain('Verificando enlace…')
    expect(html).not.toContain('<form')
  })
  it('sin sesión o con error en la URL (fase inválida): mensaje y enlace a /recuperar', () => {
    const html = vista('invalido')
    expect(html).toContain('El enlace no es válido o venció. Pide uno nuevo.')
    expect(html).toContain('href="/recuperar"')
    expect(html).not.toContain('<form')
  })
  it('con sesión de recuperación: dos campos con etiqueta, mostrar/ocultar, política y botón habilitado', () => {
    const html = vista('listo')
    expect(html).toMatch(/<label[^>]*for="contrasena"[^>]*>Contraseña/)
    expect(html).toMatch(/<label[^>]*for="confirmar"[^>]*>Confirmar contraseña/)
    expect(html.match(/type="password"/g)).toHaveLength(2)
    expect(html).toContain('aria-label="Mostrar contraseña"')
    expect(html).toContain('aria-label="Mostrar confirmación de contraseña"')
    expect(html).toContain('La contraseña debe tener entre 8 y 72 caracteres, con al menos una letra y un número.')
    expect(html).toContain('Guardar contraseña</button>')
    expect(html).not.toMatch(/<button[^>]*disabled/)
  })
})
