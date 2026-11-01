import { describe, expect, it, vi } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import Entrenadores from './Entrenadores.jsx'
import { EntrenadoresContext } from '../../context/EntrenadoresContext.js'

// Render estático de la lista en cada estado: cargando, error (con y sin reintento), vacío y con activos e inactivos.
const luis = { id: 'u1', nombre: 'Luis', apellido: 'Pérez', especialidad: 'Porteros', telefono: '3001112233', categorias: ['Cachorros', 'Semillero'], activo: true }
const ana = { id: 'u2', nombre: 'Ana', apellido: 'Ríos', especialidad: 'Táctica', telefono: '3005556677', categorias: [], activo: false }

const dibujar = (contexto) => renderToStaticMarkup(
  <MemoryRouter>
    <EntrenadoresContext.Provider value={{
      entrenadores: [], estadoCarga: 'listo', errorCarga: null, reintentar: vi.fn(), aviso: null, avisar: vi.fn(),
      cerrarAviso: vi.fn(), cambiarEstado: vi.fn(), ...contexto,
    }}
    >
      <Entrenadores />
    </EntrenadoresContext.Provider>
  </MemoryRouter>,
)

describe('lista de entrenadores', () => {
  it('cargando: mensaje de estado y sin tarjetas', () => {
    const html = dibujar({ estadoCarga: 'cargando' })
    expect(html).toContain('Cargando entrenadores…')
    expect(html).not.toContain('Activos (')
  })

  it('error al cargar: alerta con botón Reintentar', () => {
    const html = dibujar({ estadoCarga: 'error', errorCarga: { estado: 500, mensaje: 'x' } })
    expect(html).toContain('No se pudo cargar la lista de entrenadores.')
    expect(html).toContain('Reintentar')
  })

  it('error de permisos (403): muestra el mensaje del backend y no ofrece reintentar', () => {
    const html = dibujar({ estadoCarga: 'error', errorCarga: { estado: 403, mensaje: 'No tienes permiso para hacer esta acción.' } })
    expect(html).toContain('No tienes permiso para hacer esta acción.')
    expect(html).not.toContain('Reintentar')
  })

  it('vacío: mensaje y enlace para registrar', () => {
    const html = dibujar({ entrenadores: [] })
    expect(html).toContain('Todavía no hay entrenadores registrados.')
    expect(html).toContain('/administrador/usuarios/entrenadores/nuevo')
  })

  it('separa activos e inactivos y marca el estado con texto', () => {
    const html = dibujar({ entrenadores: [luis, ana] })
    expect(html).toContain('Activos (1)')
    expect(html).toContain('Inactivos (1)')
    expect(html).toContain('Categorías: Cachorros, Semillero')
    expect(html).toContain('>Activo<')
    expect(html).toContain('>Inactivo<')
  })

  it('el inactivo no muestra categorías, ofrece Activar y no Desactivar', () => {
    const html = dibujar({ entrenadores: [ana] })
    expect(html).toContain('no dirige categorías')
    expect(html).toContain('aria-label="Activar a Ana Ríos"')
    expect(html).not.toContain('Desactivar a Ana Ríos')
    expect(html).not.toContain('Categorías:')
  })

  it('el activo sin categorías lo dice, y ofrece Desactivar', () => {
    const html = dibujar({ entrenadores: [{ ...luis, categorias: [] }] })
    expect(html).toContain('sin categorías asignadas')
    expect(html).toContain('aria-label="Desactivar a Luis Pérez"')
  })
})
