import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import SelectorDeportista from './SelectorDeportista.jsx'
import GestionCobros from './GestionCobros.jsx'
import { adaptarDeportista } from '../../utils/deportistasApi.js'

// Sin red y sin depender de .env.local: el cliente HTTP está simulado y el interruptor sale de vi.stubEnv. renderToStaticMarkup no
// ejecuta efectos ni eventos: aquí se comprueba lo que se dibuja; el filtrado y el teclado (lógica) están en buscarDeportistas.test.js.
vi.mock('../../services/apiClient.js', () => ({ apiRequest: vi.fn(), apiListaPaginada: vi.fn() }))
afterEach(() => vi.unstubAllEnvs())

const dto = (n, extra = {}) => ({
  id: `6f1c2a9e-3b4d-4e5f-8a7b-${String(n).padStart(12, '0')}`, nombre: `Ana${n}`, apellido: 'Pérez', tipoDocumento: 'TI', documento: `10987654${String(n).padStart(2, '0')}`,
  fechaNacimiento: '2016-08-20', edad: 10, telefono: '3001234567', direccion: 'Calle 10 # 20-30', posicion: null, foto: null, categoria: 'Semillero',
  idCategoria: '0a0a0a0a-1111-4222-8333-444444444444', grupo: null, activo: true, estadoInscripcion: 'PENDIENTE', ...extra,
})
const deportistas = [1, 2, 3].map((n) => adaptarDeportista(dto(n)))
const html = (props) => renderToStaticMarkup(<SelectorDeportista deportistas={deportistas} value="" onChange={() => {}} {...props} />)

describe('SelectorDeportista (estructura ARIA)', () => {
  it('el campo es un combobox cerrado, con etiqueta, ayuda y sin lista hasta que se enfoque', () => {
    const h = html()
    expect(h).toMatch(/<label[^>]*for="idDeportista"[^>]*>Deportista/)
    expect(h).toMatch(/<input id="idDeportista"[^>]*role="combobox"/)
    expect(h).toContain('aria-expanded="false"')
    expect(h).toContain('aria-autocomplete="list"')
    expect(h).toContain('aria-describedby="idDeportista-ayuda"')
    expect(h).toContain('Busca por nombre, apellido o número de documento.')
    expect(h).not.toContain('role="listbox"')
    expect(h).not.toContain('aria-controls')
  })

  it('con un deportista elegido muestra su nombre en el campo y la tarjeta con nombre, documento (tipo y número), categoría y «Cambiar»', () => {
    const h = html({ value: deportistas[1].id })
    expect(h).toContain('value="Ana2 Pérez"')
    expect(h).not.toContain('role="combobox"')
    expect(h).toContain('aria-label="Deportista elegido"')
    expect(h).toContain('>Tarjeta de identidad 1098765402<')
    expect(h).toContain('>Semillero<')
    expect(h).toMatch(/<button[^>]*>Cambiar<\/button>/)
  })

  it('cargando: campo deshabilitado con «Cargando deportistas…»', () => {
    const h = html({ deportistas: [], cargando: true })
    expect(h).toContain('Cargando deportistas…')
    expect(h).toMatch(/<input[^>]*disabled/)
  })

  it('sin deportistas activos lo dice; con error de carga no dice eso (el aviso con «Reintentar» lo pone la pantalla)', () => {
    expect(html({ deportistas: [] })).toContain('No hay deportistas activos')
    expect(html({ deportistas: [], errorDeCarga: true })).not.toContain('No hay deportistas activos')
  })

  it('muestra el error de validación ligado al campo', () => {
    const h = html({ error: 'Elige un deportista.' })
    expect(h).toContain('aria-invalid="true"')
    expect(h).toContain('id="idDeportista-error"')
    expect(h).toContain('Elige un deportista.')
  })
})

describe('«Crear cobro» del administrador', () => {
  it('ya no hay <select> de deportistas: el panel (cerrado) no dibuja ni campo ni lista', () => {
    vi.stubEnv('MODE', 'development')
    vi.stubEnv('VITE_API_MODULOS', 'deportistas,pagos')
    const h = renderToStaticMarkup(<MemoryRouter><GestionCobros titulo="Pagos" subtitulo="x" esAdministrador /></MemoryRouter>)
    expect(h).toContain('Crear cobro')
    expect(h).not.toContain('Selecciona el deportista')
    expect(h).not.toContain('id="idDeportista"')
  })
})
