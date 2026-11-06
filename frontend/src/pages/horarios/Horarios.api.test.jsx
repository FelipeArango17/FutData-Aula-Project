import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'

// Modo API: "horarioclub" sale de vi.stubEnv (no depende de .env.local) y el cliente HTTP está simulado (sin red).
vi.mock('../../services/apiClient.js', () => ({ apiRequest: vi.fn(() => new Promise(() => {})), apiListaPaginada: vi.fn() }))

let Horarios
let HorariosAcudiente
let RoleContext

beforeEach(async () => {
  vi.stubEnv('MODE', 'development')
  vi.stubEnv('VITE_API_MODULOS', 'horarioclub')
  vi.resetModules()
  Horarios = (await import('./Horarios.jsx')).default
  HorariosAcudiente = (await import('../acudiente/Horarios.jsx')).default
  RoleContext = (await import('../../context/RoleContext.js')).RoleContext
})

afterEach(() => {
  vi.unstubAllEnvs()
})

const dibujar = (role, Pantalla = Horarios) => renderToStaticMarkup(
  <MemoryRouter><RoleContext.Provider value={{ role }}><Pantalla /></RoleContext.Provider></MemoryRouter>,
)

describe('Horarios (API, antes de cargar la semana)', () => {
  it('el administrador navega semanas y puede agregar extras (cancelar y eliminar salen de puedeGestionar)', () => {
    const html = dibujar('administrador')
    expect(html).toContain('Semana siguiente')
    expect(html).toContain('Agregar entrenamiento extra')
    expect(html).toContain('Cargando el horario')
  })

  it('el entrenador navega semanas; sus categorías y el botón de extras llegan con su perfil (hasta entonces, solo lectura)', () => {
    const html = dibujar('entrenador')
    expect(html).toContain('Semana siguiente')
    expect(html).not.toContain('Agregar entrenamiento extra')
    expect(html).not.toContain('Mis categorías')
  })

  it('el acudiente ve solo la semana en curso: sin semanas anterior y siguiente, sin extras y sin datos de ejemplo', () => {
    const html = dibujar('acudiente', HorariosAcudiente)
    expect(html).toContain('Semana en curso')
    for (const texto of ['Semana anterior', 'Semana siguiente', '>Hoy<', 'Agregar entrenamiento extra', 'Tu grupo', 'Cambio de horario Cachorros']) expect(html).not.toContain(texto)
  })

  it('el tesorero solo lee: sin navegación ni botones', () => {
    const html = dibujar('tesorero')
    for (const texto of ['Semana siguiente', 'Agregar entrenamiento extra']) expect(html).not.toContain(texto)
  })
})
