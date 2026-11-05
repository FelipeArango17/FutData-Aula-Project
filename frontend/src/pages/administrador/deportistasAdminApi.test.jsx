import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'

// Módulo "deportistas" del administrador con la API activa. Sin red y sin depender de .env.local: el cliente HTTP está simulado y el
// interruptor sale de vi.stubEnv (MODE distinto de "test", porque en "test" la API siempre está apagada). Los módulos se importan
// DESPUÉS de fijar el entorno, porque el interruptor se lee al cargarlos.
const api = vi.hoisted(() => ({ apiRequest: vi.fn(), apiListaPaginada: vi.fn(), subirArchivo: vi.fn() }))
vi.mock('../../services/apiClient.js', () => api)
vi.mock('../../context/useActor.js', () => ({ useActor: () => ({ rol: 'administrador', correo: 'administrador@pradofc.co' }) }))
vi.mock('../../services/supabaseClient.js', () => ({ autenticacionReal: true, supabase: null }))

const ID = '6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b'
const CAT = '0a0a0a0a-1111-4222-8333-444444444444'
// Forma real de DeportistaResponseDTO para el administrador
const dto = (extra = {}) => ({
  id: ID, nombre: 'Ana', apellido: 'Pérez', tipoDocumento: 'TI', documento: '1098765432', fechaNacimiento: '2016-08-20', edad: 10,
  telefono: '3001234567', direccion: 'Calle 10 # 20-30', posicion: 'Delantero', foto: null, categoria: 'Semillero', idCategoria: CAT,
  grupo: 'tarde', activo: true, estadoInscripcion: 'PENDIENTE', ...extra,
})
const pagina = (items, { total = items.length, tamano = 100, pagina: p = 0 } = {}) => ({ items, total, pagina: p, tamano })
const dibujar = (nodo) => renderToStaticMarkup(<MemoryRouter>{nodo}</MemoryRouter>)

async function cargar(modulos) {
  vi.resetModules()
  vi.stubEnv('MODE', 'development')
  vi.stubEnv('VITE_API_MODULOS', modulos)
  const [{ default: Inscribir }, servicio, { default: FormularioDeportista }] = await Promise.all([
    import('./Inscribir.jsx'), import('../../services/deportistasService.js'), import('../../components/deportistas/FormularioDeportista.jsx'),
  ])
  return { Inscribir, servicio, FormularioDeportista }
}

beforeEach(() => {
  for (const f of Object.values(api)) f.mockReset()
})
afterEach(() => vi.unstubAllEnvs())

describe('Inscribir con la API activa', () => {
  it('es un solo formulario con los campos del DTO y la foto; sin acudiente, documentos ni pasos, y lo dice', async () => {
    const { Inscribir } = await cargar('deportistas')
    const html = dibujar(<Inscribir />)
    for (const campo of ['id="nombre"', 'id="apellido"', 'id="tipoDocumento"', 'id="documento"', 'id="fechaNacimiento', 'id="telefono"', 'id="direccion"', 'id="posicion"']) expect(html).toContain(campo)
    expect(html).toContain('Foto del jugador (opcional)')
    expect(html).toContain('El acudiente (su cuenta y el vínculo con el jugador) y los documentos todavía no se pueden cargar desde aquí')
    for (const ausente of ['Paso 1 de', 'Nombres del acudiente', 'Parentesco', 'Foto tipo documento', 'Certificado', 'Continuar']) expect(html).not.toContain(ausente)
  })

  it('sin "deportistas" en VITE_API_MODULOS sigue el asistente de 4 pasos', async () => {
    const { Inscribir } = await cargar('entrenadores')
    const html = dibujar(<Inscribir />)
    expect(html).toContain('Paso 1 de 4')
    expect(html).not.toContain('todavía no se pueden cargar desde aquí')
  })
})

describe('edición de la ficha con la API activa', () => {
  it('el formulario ofrece el Estado (activo o inactivo) solo si recibe `activo`', async () => {
    const { FormularioDeportista } = await cargar('deportistas')
    const base = { nombre: 'Ana', apellido: 'Pérez', tipoDocumento: 'Tarjeta de identidad', documento: '1', fechaNacimiento: '2016-08-20', telefono: '3001234567', direccion: 'Calle 10', posicion: '', grupoEntrenamiento: '' }
    const con = dibujar(<FormularioDeportista inicial={{ ...base, activo: 'inactivo' }} categorias={[]} onGuardar={() => {}} onCancelar={() => {}} />)
    expect(con).toMatch(/<label[^>]*for="activo"[^>]*>Estado<\/label>/)
    expect(con).toMatch(/<option value="inactivo" selected="">Inactivo<\/option>/)
    expect(dibujar(<FormularioDeportista inicial={base} categorias={[]} onGuardar={() => {}} onCancelar={() => {}} />)).not.toContain('id="activo"')
  })

  it('PUT manda todos los campos del DTO con el `activo` elegido; sin ningún cambio no llama al backend', async () => {
    const { servicio } = await cargar('deportistas')
    api.apiRequest.mockImplementation(async (ruta, opciones) => (opciones?.method === 'PUT' ? dto({ activo: false }) : dto()))
    const resultado = await servicio.actualizarDeportista(ID, { activo: false })
    const put = api.apiRequest.mock.calls.find(([, o]) => o?.method === 'PUT')
    expect(Object.keys(JSON.parse(put[1].body)).sort()).toEqual(['activo', 'apellido', 'direccion', 'documento', 'fechaNacimiento', 'grupo', 'nombre', 'posicion', 'telefono', 'tipoDocumento'])
    expect(JSON.parse(put[1].body)).toMatchObject({ activo: false, tipoDocumento: 'TI', grupo: 'tarde' })
    expect(resultado.deportista.activo).toBe(false)
    expect(resultado.historial).toEqual([])
    api.apiRequest.mockClear()
    await expect(servicio.actualizarDeportista(ID, { activo: true })).rejects.toMatchObject({ codigo: 'SIN_CAMBIOS' })
    expect(api.apiRequest.mock.calls.every(([, o]) => !o?.method)).toBe(true)
  })

  it('409 de documento repetido, 400 por campo y 403 llegan con código y mensaje en español', async () => {
    const { servicio } = await cargar('deportistas')
    const falla = (estado, mensaje, campos = []) => Object.assign(new Error(mensaje), { estado, mensaje, campos, codigo: null })
    api.apiRequest.mockImplementation(async (ruta, opciones) => {
      if (opciones?.method === 'PUT') throw falla(409, 'documento: ya está registrado en otro deportista', ['documento'])
      return dto()
    })
    await expect(servicio.actualizarDeportista(ID, { documento: '999' })).rejects.toMatchObject({ codigo: 'DOCUMENTO_DUPLICADO', errores: { documento: 'Ya está registrado en otro deportista.' } })
    api.apiRequest.mockImplementation(async (ruta, opciones) => {
      if (opciones?.method === 'PUT') throw falla(400, 'telefono: debe tener 10 dígitos', ['telefono'])
      return dto()
    })
    await expect(servicio.actualizarDeportista(ID, { telefono: '1' })).rejects.toMatchObject({ codigo: 'VALIDACION', errores: { telefono: 'Debe tener 10 dígitos.' } })
    api.apiRequest.mockRejectedValue(falla(403, 'No tienes permiso para esta acción.'))
    await expect(servicio.obtenerDeportista(ID)).rejects.toMatchObject({ codigo: 'SIN_PERMISO', mensaje: 'No tienes permiso para esta acción.' })
  })
})

describe('deportistas para "Crear cobro" con la API activa', () => {
  it('pide solo activos, de 100 en 100, y recorre todas las páginas', async () => {
    const { servicio } = await cargar('deportistas')
    const ids = (desde, hasta) => Array.from({ length: hasta - desde }, (_, i) => dto({ id: `id-${desde + i}`, nombre: `N${desde + i}` }))
    api.apiListaPaginada.mockImplementation(async (ruta, { page }) => (page === 0 ? pagina(ids(0, 100), { total: 130, pagina: 0 }) : pagina(ids(100, 130), { total: 130, pagina: 1 })))
    const lista = await servicio.listarDeportistas({ estado: 'activos' })
    expect(lista).toHaveLength(130)
    expect(lista[129]).toMatchObject({ id: 'id-129', activo: true })
    expect(api.apiListaPaginada).toHaveBeenCalledTimes(2)
    for (const [ruta, filtros] of api.apiListaPaginada.mock.calls) {
      expect(ruta).toBe('/deportistas')
      expect(filtros).toMatchObject({ size: 100, activo: true })
    }
    expect(api.apiListaPaginada.mock.calls.map(([, f]) => f.page)).toEqual([0, 1])
  })

  it('un error del backend se propaga (la pantalla muestra «Reintentar»)', async () => {
    const { servicio } = await cargar('deportistas')
    api.apiListaPaginada.mockRejectedValue(Object.assign(new Error('x'), { estado: 403, mensaje: 'Sin permiso.', campos: [], codigo: 'PERMISOS' }))
    await expect(servicio.listarDeportistas({ estado: 'activos' })).rejects.toMatchObject({ codigo: 'SIN_PERMISO', mensaje: 'Sin permiso.' })
  })
})
