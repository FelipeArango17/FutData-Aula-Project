import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'

// Foto tipo documento en la lista de verificación. Sin red ni .env.local: el cliente HTTP está simulado y el interruptor sale de vi.stubEnv
// (en MODE "test" la API siempre está apagada). Los módulos se importan DESPUÉS de fijar el entorno.
const api = vi.hoisted(() => ({ apiRequest: vi.fn(), apiListaPaginada: vi.fn(), subirArchivo: vi.fn() }))
vi.mock('../../services/apiClient.js', () => api)
vi.mock('../../context/useActor.js', () => ({ useActor: () => ({ rol: 'administrador', correo: 'administrador@pradofc.co' }) }))
vi.mock('../../services/supabaseClient.js', () => ({ autenticacionReal: true, supabase: null }))

const ID = '6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b'
const dto = (extra = {}) => ({
  id: ID, nombre: 'Ana', apellido: 'Pérez', tipoDocumento: 'TI', documento: '1098765432', fechaNacimiento: '2016-08-20', edad: 10,
  telefono: '3001234567', direccion: 'Calle 10', posicion: null, foto: null, categoria: 'Semillero', idCategoria: null, grupo: 'tarde', activo: true, ...extra,
})
const dibujar = (nodo) => renderToStaticMarkup(<MemoryRouter>{nodo}</MemoryRouter>)

async function cargar() {
  vi.resetModules()
  vi.stubEnv('MODE', 'development')
  vi.stubEnv('VITE_API_MODULOS', 'deportistas,documentos')
  const [{ PanelDocumentos }, { default: DocumentosDelAcudiente }, servicio, { deportistaTieneFoto }] = await Promise.all([
    import('../../components/deportistas/DocumentosDeportista.jsx'), import('../../components/deportistas/DocumentosDelAcudiente.jsx'),
    import('../../services/deportistasService.js'), import('../../utils/documentosClub.js'),
  ])
  return { PanelDocumentos, DocumentosDelAcudiente, servicio, deportistaTieneFoto }
}

beforeEach(() => {
  for (const f of Object.values(api)) f.mockReset()
})
afterEach(() => vi.unstubAllEnvs())

describe('Foto en Documentación del administrador', () => {
  it('el listado trae `foto` y de ahí sale "Cargada" / "Sin cargar"', async () => {
    const { servicio, deportistaTieneFoto } = await cargar()
    api.apiListaPaginada.mockResolvedValue({ items: [dto({ foto: 'https://x/foto.jpg' }), dto({ id: 'otro', foto: null })], total: 2, pagina: 0, tamano: 100 })
    const [con, sin] = await servicio.listarDeportistas({ estado: 'activos' })
    expect(deportistaTieneFoto(con)).toBe(true)
    expect(deportistaTieneFoto(sin)).toBe(false)
    expect(deportistaTieneFoto({ id: 4, foto: null }, { 4: 'blob:x' })).toBe(true) // modo en memoria
  })

  it('sin foto: el enlace va a la ficha, ya en la tarjeta de Foto', async () => {
    const { PanelDocumentos } = await cargar()
    const html = dibujar(<PanelDocumentos documentos={[]} tieneFoto={false} rutaFoto={`/administrador/deportistas/${ID}`} onSubir={async () => {}} />)
    expect(html).toContain(`href="/administrador/deportistas/${ID}#foto-deportista"`)
    expect(html).not.toContain('href="#foto-deportista"')
    expect(html).toContain('Ir a la tarjeta de Foto')
  })

  it('con foto: dice "Cargada" y no hay enlace; la cuenta "x de 4" no cambia', async () => {
    const { PanelDocumentos } = await cargar()
    const html = dibujar(<PanelDocumentos documentos={[]} tieneFoto rutaFoto={`/administrador/deportistas/${ID}`} onSubir={async () => {}} />)
    expect(html).toContain('>Cargada<')
    expect(html).not.toContain('Ir a la tarjeta de Foto')
    expect(html).toContain('0 de 4 requisitos obligatorios completos')
  })
})

describe('Foto en Documentos del acudiente', () => {
  const jugador = (extra) => ({ id: 'j1', nombre: 'Luis Gómez', tieneFoto: false, documentos: [], estadoInscripcion: 'PENDIENTE', documentosFaltantes: [], ...extra })
  const ver = (DocumentosDelAcudiente, j) => dibujar(<DocumentosDelAcudiente jugadores={[j]} onSubir={async () => {}} />)

  it('sin enlace, con la línea informativa y el estado real', async () => {
    const { DocumentosDelAcudiente } = await cargar()
    const sin = ver(DocumentosDelAcudiente, jugador())
    expect(sin).toContain('La foto la carga el club.')
    expect(sin).not.toContain('Ir a la tarjeta de Foto')
    expect(sin).not.toContain('#foto-deportista')
    expect(sin).toContain('>Sin cargar<')
    const con = ver(DocumentosDelAcudiente, jugador({ tieneFoto: true }))
    expect(con).toContain('>Cargada<')
    expect(con).toContain('La foto la carga el club.')
  })
})
