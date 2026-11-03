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

describe('/restablecer en modo invitación (render)', () => {
  const invitada = (fase, extra = {}) => dibujar(<VistaRestablecer fase={fase} invitacion restablecer={async () => {}} onListo={() => {}} {...extra} />)
  it('"Crea tu contraseña", texto de ayuda y botón "Crear contraseña" con los mismos campos', () => {
    const html = invitada('listo')
    expect(html).toContain('Crea tu contraseña')
    expect(html).toContain('Te invitaron a FutData')
    expect(html).toContain('Crear contraseña</button>')
    expect(html.match(/type="password"/g)).toHaveLength(2)
    expect(html).not.toContain('Guardar contraseña')
  })
  it('enlace vencido o usado: mensaje claro y salida "Olvidé mi contraseña"', () => {
    const html = invitada('invalido', { vencido: true })
    expect(html).toContain('El enlace venció o ya se usó.')
    expect(html).toContain('Olvidé mi contraseña')
    expect(html).toContain('href="/recuperar"')
    expect(html).not.toContain('<form')
  })
})
