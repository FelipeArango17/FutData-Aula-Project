import { beforeEach, describe, expect, it, vi } from 'vitest'

// Modo API del servicio de deportistas: el cliente HTTP está simulado (nunca hay red) y el interruptor "deportistas" va encendido.
const api = vi.hoisted(() => ({ apiRequest: vi.fn(), apiListaPaginada: vi.fn(), subirArchivo: vi.fn() }))
vi.mock('./apiClient.js', () => api)
vi.mock('./usarApi.js', () => ({ usarApiDe: (modulo) => modulo === 'deportistas' }))

import * as servicio from './deportistasService.js'

const ID = '6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b'
const CAT = '0a0a0a0a-1111-4222-8333-444444444444'
const dto = (extra = {}) => ({
  id: ID, nombre: 'Ana', apellido: 'Pérez', tipoDocumento: 'TI', documento: '1098765432', fechaNacimiento: '2016-08-20', edad: 10,
  telefono: '3001234567', direccion: 'Calle 10 # 20-30', posicion: 'Delantera', foto: null, categoria: 'Semillero', idCategoria: CAT,
  grupo: 'tarde', activo: true, estadoInscripcion: 'PENDIENTE', ...extra,
})
const pagina = (items, { total = items.length, tamano = 10, pagina: p = 0 } = {}) => ({ items, total, pagina: p, tamano })
const apiError = (estado, mensaje, campos = []) => Object.assign(new Error(mensaje), { estado, mensaje, campos, codigo: null })
const formulario = (extra = {}) => ({
  nombre: 'Ana', apellido: 'Pérez', tipoDocumento: 'Tarjeta de identidad', documento: '1098765432', fechaNacimiento: '2016-08-20',
  telefono: '3001234567', direccion: 'Calle 10 # 20-30', posicion: 'Delantera', grupoEntrenamiento: 'tarde', idCategoria: 'ignorada', ...extra,
})
const CATEGORIAS = [{ id: CAT, nombre: 'Semillero' }, { id: 'otra', nombre: 'Sub-12' }]

beforeEach(() => {
  api.apiRequest.mockReset()
  api.apiListaPaginada.mockReset()
  api.subirArchivo.mockReset()
})

describe('con el interruptor encendido', () => {
  it('deportistasPorApi es true y listarCategorias usa GET /categorias (ids UUID, todas vigentes)', async () => {
    expect(servicio.deportistasPorApi()).toBe(true)
    api.apiRequest.mockResolvedValue([{ id: CAT, nombre: 'Semillero', edadMinima: 9, edadMaxima: 10, entrenador: null }])
    expect(await servicio.listarCategorias()).toEqual([{ id: CAT, nombre: 'Semillero', activa: true }])
    expect(api.apiRequest).toHaveBeenCalledWith('/categorias')
  })
})

describe('lista', () => {
  it('listarPaginaDeportistas: la página y el filtro son del backend (página 1 = page 0; estado -> activo; categoría por nombre)', async () => {
    api.apiRequest.mockResolvedValue(CATEGORIAS)
    api.apiListaPaginada.mockResolvedValue(pagina([dto()], { total: 23, tamano: 10, pagina: 1 }))
    const r = await servicio.listarPaginaDeportistas({ pagina: 2, estado: 'activos', idCategoria: CAT })
    expect(api.apiListaPaginada).toHaveBeenCalledWith('/deportistas', { page: 1, size: 10, sort: undefined, activo: true, categoria: 'Semillero' })
    expect(r).toMatchObject({ pagina: 2, totalPaginas: 3, desde: 11, hasta: 11, total: 23 })
    expect(r.items[0]).toMatchObject({ id: ID, tipoDocumento: 'Tarjeta de identidad', idCategoria: CAT, grupoEntrenamiento: 'tarde' })
  })
  it('una página después de la última muestra la última', async () => {
    api.apiListaPaginada
      .mockResolvedValueOnce(pagina([], { total: 23, tamano: 10, pagina: 5 }))
      .mockResolvedValueOnce(pagina([dto()], { total: 23, tamano: 10, pagina: 2 }))
    const r = await servicio.listarPaginaDeportistas({ pagina: 6 })
    expect(api.apiListaPaginada.mock.calls[1][1].page).toBe(2)
    expect(r.pagina).toBe(3)
  })
  it('con texto recorre las páginas (100 en 100) y filtra aquí: el backend no busca por texto', async () => {
    api.apiListaPaginada
      .mockResolvedValueOnce(pagina([dto({ nombre: 'Ana' })], { total: 150, tamano: 100, pagina: 0 }))
      .mockResolvedValueOnce(pagina([dto({ id: 'b', nombre: 'Luis', documento: '222' })], { total: 150, tamano: 100, pagina: 1 }))
    const r = await servicio.listarPaginaDeportistas({ texto: 'luis' })
    expect(api.apiListaPaginada).toHaveBeenCalledTimes(2)
    expect(api.apiListaPaginada.mock.calls[0][1]).toMatchObject({ page: 0, size: 100 })
    expect(r.items.map((d) => d.id)).toEqual(['b'])
    expect(r.total).toBe(1)
  })
  it('«sin asignar» se filtra aquí (idCategoria null) y no manda categoría', async () => {
    api.apiListaPaginada.mockResolvedValue(pagina([dto(), dto({ id: 'c', idCategoria: null, categoria: null })]))
    const lista = await servicio.listarDeportistas({ idCategoria: servicio.SIN_CATEGORIA })
    expect(lista.map((d) => d.id)).toEqual(['c'])
    expect(api.apiListaPaginada.mock.calls[0][1].categoria).toBeUndefined()
  })
  it('listarDeportistas por categoría manda su NOMBRE; una categoría que no existe lanza CATEGORIA_NO_EXISTE', async () => {
    api.apiRequest.mockResolvedValue(CATEGORIAS)
    api.apiListaPaginada.mockResolvedValue(pagina([dto()]))
    await servicio.listarDeportistas({ idCategoria: 'otra' })
    expect(api.apiListaPaginada.mock.calls[0][1].categoria).toBe('Sub-12')
    await expect(servicio.listarDeportistas({ idCategoria: 'no-existe' })).rejects.toMatchObject({ codigo: 'CATEGORIA_NO_EXISTE' })
  })
  it('contarDeportistas: dos consultas de una fila, lee el total', async () => {
    api.apiListaPaginada.mockImplementation(async (_ruta, { activo }) => pagina([], { total: activo ? 18 : 23, tamano: 1 }))
    expect(await servicio.contarDeportistas()).toEqual({ total: 23, activos: 18 })
    expect(api.apiListaPaginada.mock.calls.every(([, p]) => p.size === 1)).toBe(true)
  })
  it('un 403 de la lista sube con SIN_PERMISO y el mensaje del backend', async () => {
    api.apiListaPaginada.mockRejectedValue(apiError(403, 'No tienes permiso para hacer esta acción.'))
    await expect(servicio.listarDeportistas()).rejects.toMatchObject({ codigo: 'SIN_PERMISO', mensaje: 'No tienes permiso para hacer esta acción.' })
  })
  it('un 400 (size fuera de rango) sube con el mensaje del backend', async () => {
    api.apiListaPaginada.mockRejectedValue(apiError(400, 'size: debe estar entre 1 y 100', ['size']))
    await expect(servicio.listarDeportistas()).rejects.toMatchObject({ estado: 400, mensaje: 'size: debe estar entre 1 y 100' })
  })
})

describe('ficha, alta y edición', () => {
  it('obtenerFichaDeportista: GET /deportistas/{id} con el UUID como texto, sin historial', async () => {
    api.apiRequest.mockResolvedValue(dto())
    const ficha = await servicio.obtenerFichaDeportista(ID, { rol: 'administrador' })
    expect(api.apiRequest).toHaveBeenCalledWith(`/deportistas/${ID}`)
    expect(ficha.deportista.id).toBe(ID)
    expect(ficha.historial).toEqual([])
  })
  it('404 -> DEPORTISTA_NO_EXISTE con el mensaje del backend', async () => {
    api.apiRequest.mockRejectedValue(apiError(404, 'No existe un deportista con ese id.'))
    await expect(servicio.obtenerFichaDeportista(ID)).rejects.toMatchObject({ codigo: 'DEPORTISTA_NO_EXISTE', mensaje: 'No existe un deportista con ese id.' })
  })
  it('crearDeportista: POST con el código del tipo, sin categoría ni activo', async () => {
    api.apiRequest.mockResolvedValue(dto({ tipoDocumento: 'RC' }))
    const creado = await servicio.crearDeportista(formulario({ tipoDocumento: 'Registro civil' }), { rol: 'administrador' })
    const [ruta, opciones] = api.apiRequest.mock.calls[0]
    expect([ruta, opciones.method]).toEqual(['/deportistas', 'POST'])
    expect(JSON.parse(opciones.body)).toEqual({
      nombre: 'Ana', apellido: 'Pérez', tipoDocumento: 'RC', documento: '1098765432', fechaNacimiento: '2016-08-20', telefono: '3001234567',
      direccion: 'Calle 10 # 20-30', posicion: 'Delantera', grupo: 'tarde',
    })
    expect(creado.id).toBe(ID)
  })
  it('crearDeportista: 409 de documento repetido y 400 de fecha, con el mensaje del backend', async () => {
    api.apiRequest.mockRejectedValueOnce(apiError(409, 'documento: ya está registrado en otro deportista', ['documento']))
    await expect(servicio.crearDeportista(formulario())).rejects.toMatchObject({ codigo: 'DOCUMENTO_DUPLICADO', message: 'Ya está registrado en otro deportista.' })
    api.apiRequest.mockRejectedValueOnce(apiError(400, 'fechaNacimiento: el club solo inscribe de 4 a 16 años', ['fechaNacimiento']))
    await expect(servicio.crearDeportista(formulario())).rejects.toMatchObject({ codigo: 'VALIDACION', errores: { fechaNacimiento: 'El club solo inscribe de 4 a 16 años.' } })
  })
  it('actualizarDeportista: lee el actual, hace PUT con TODOS los campos y el `activo` actual', async () => {
    api.apiRequest.mockResolvedValueOnce(dto({ activo: false })).mockResolvedValueOnce(dto({ nombre: 'Ana María', activo: false }))
    const r = await servicio.actualizarDeportista(ID, formulario({ nombre: 'Ana María' }), { rol: 'administrador' })
    const [ruta, opciones] = api.apiRequest.mock.calls[1]
    expect([ruta, opciones.method]).toEqual([`/deportistas/${ID}`, 'PUT'])
    expect(JSON.parse(opciones.body)).toMatchObject({ nombre: 'Ana María', activo: false, tipoDocumento: 'TI', grupo: 'tarde' })
    expect(r.deportista.nombre).toBe('Ana María')
  })
  it('actualizarDeportista sin cambios: SIN_CAMBIOS y no llama al PUT', async () => {
    api.apiRequest.mockResolvedValueOnce(dto())
    await expect(servicio.actualizarDeportista(ID, formulario())).rejects.toMatchObject({ codigo: 'SIN_CAMBIOS' })
    expect(api.apiRequest).toHaveBeenCalledTimes(1)
  })
  it('la edición NO manda la categoría (la calcula el backend)', async () => {
    api.apiRequest.mockResolvedValueOnce(dto()).mockResolvedValueOnce(dto())
    await servicio.actualizarDeportista(ID, formulario({ telefono: '3009999999' }))
    expect(JSON.parse(api.apiRequest.mock.calls[1][1].body)).not.toHaveProperty('idCategoria')
  })
})

describe('lo que no existe en el backend', () => {
  it('asignarCategoria y vincularAcudiente avisan NO_DISPONIBLE sin llamar a la API', async () => {
    await expect(servicio.asignarCategoria(ID, CAT)).rejects.toMatchObject({ codigo: 'NO_DISPONIBLE' })
    await expect(servicio.vincularAcudiente(ID, 'a@b.co', {})).rejects.toMatchObject({ codigo: 'NO_DISPONIBLE' })
    expect(api.apiRequest).not.toHaveBeenCalled()
  })
  it('obtenerHistorial: vacío (el backend no guarda historial)', async () => {
    expect(await servicio.obtenerHistorial(ID)).toEqual([])
  })
})
