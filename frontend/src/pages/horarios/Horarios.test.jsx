import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import Horarios from './Horarios.jsx'
import { RoleContext } from '../../context/RoleContext.js'

// Modo ejemplo (las pruebas siempre lo usan). El render estático muestra la pantalla antes de cargar la semana:
// sirve para comprobar qué controles hay según el rol.
const dibujar = (role) => renderToStaticMarkup(
  <MemoryRouter><RoleContext.Provider value={{ role }}><Horarios /></RoleContext.Provider></MemoryRouter>,
)

describe('Horarios (modo ejemplo)', () => {
  it('administrador: agregar extra arriba, navegación de semanas, filtros de categoría y sede', () => {
    const html = dibujar('administrador')
    expect(html).toContain('Agregar entrenamiento extra')
    for (const texto of ['Semana anterior', '>Hoy<', 'Semana siguiente']) expect(html).toContain(texto)
    expect(html).toContain('Semana del 19 al 24 de octubre')
    expect(html).toContain('Todas las sedes')
    expect(html).not.toContain('Mis categorías')
    // el botón principal va antes que los filtros y que la vista de la semana
    expect(html.indexOf('Agregar entrenamiento extra')).toBeLessThan(html.indexOf('aria-label="Categoría"'))
  })

  it('entrenador: puede navegar y agregar extras; filtra por defecto por "Mis categorías"', () => {
    const html = dibujar('entrenador')
    expect(html).toContain('Agregar entrenamiento extra')
    expect(html).toContain('Semana siguiente')
    expect(html).toMatch(/aria-pressed="true"[^>]*>Mis categorías</)
  })

  it('acudiente: solo la semana en curso, sin navegación ni botón de extras', () => {
    const html = dibujar('acudiente')
    expect(html).toContain('Semana en curso')
    for (const texto of ['Semana anterior', 'Semana siguiente', '>Hoy<', 'Agregar entrenamiento extra', 'Mis categorías']) expect(html).not.toContain(texto)
    expect(html).toContain('Cargando el horario')
  })

  it('tesorero: solo ve', () => {
    const html = dibujar('tesorero')
    for (const texto of ['Semana anterior', 'Agregar entrenamiento extra']) expect(html).not.toContain(texto)
  })

  it('no ofrece crear, editar ni eliminar franjas', () => {
    const html = dibujar('administrador')
    for (const texto of ['Nueva franja', 'Editar franja', 'Eliminar franja']) expect(html).not.toContain(texto)
  })
})
