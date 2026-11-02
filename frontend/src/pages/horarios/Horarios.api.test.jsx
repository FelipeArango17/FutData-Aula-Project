import { describe, expect, it, vi } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'

// Modo API con solo "horarioclub": sin contrato 4B no hay cancelar ni extras, y el entrenador queda en solo lectura.
vi.mock('../../services/apiClient.js', () => ({ apiRequest: vi.fn() }))
vi.mock('../../services/usarApi.js', () => ({ usarApiDe: (modulo) => modulo === 'horarioclub' }))

import Horarios from './Horarios.jsx'
import HorariosAcudiente from '../acudiente/Horarios.jsx'
import { RoleContext } from '../../context/RoleContext.js'

const dibujar = (role, Pantalla = Horarios) => renderToStaticMarkup(
  <MemoryRouter><RoleContext.Provider value={{ role }}><Pantalla /></RoleContext.Provider></MemoryRouter>,
)

describe('Horarios (API sin 4B)', () => {
  it('el administrador navega semanas pero no ve "Agregar entrenamiento extra" (oculto, no deshabilitado)', () => {
    const html = dibujar('administrador')
    expect(html).toContain('Semana siguiente')
    expect(html).not.toContain('Agregar entrenamiento extra')
  })

  it('el entrenador queda en solo lectura: sin extras y sin "Mis categorías" (no se sabe quién es)', () => {
    const html = dibujar('entrenador')
    expect(html).toContain('Semana siguiente')
    expect(html).not.toContain('Agregar entrenamiento extra')
    expect(html).not.toContain('Mis categorías')
  })

  it('el acudiente usa la pantalla única en solo lectura, sin "Tu grupo" ni datos de ejemplo', () => {
    const html = dibujar('acudiente', HorariosAcudiente)
    expect(html).toContain('Semana en curso')
    expect(html).not.toContain('Tu grupo')
    expect(html).not.toContain('Cambio de horario Cachorros')
    expect(html).not.toContain('Semana siguiente')
  })
})
