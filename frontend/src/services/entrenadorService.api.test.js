import { beforeEach, describe, expect, it, vi } from 'vitest'

// El cliente HTTP está simulado (nunca hay red) y los módulos del entrenador van encendidos.
const api = vi.hoisted(() => ({ apiRequest: vi.fn(), apiListaPaginada: vi.fn() }))
vi.mock('./apiClient.js', () => api)
vi.mock('./usarApi.js', () => ({ usarApiDe: (modulo) => ['misjugadores', 'inicioentrenador'].includes(modulo) }))

import { cargarInicioEntrenador, cargarMisJugadores, inicioEntrenadorPorApi, misJugadoresPorApi } from './entrenadorService.js'

const yo = (entrenador) => ({ id: 'u1', nombre: 'Luis', rol: 'ENTRENADOR', entrenador })
const CATS = [{ idCategoria: 'c1', nombre: 'Sub-11' }, { idCategoria: 'c2', nombre: 'Semillero' }]
const dto = (id, nombre, categoria, extra = {}) => ({
  id, nombre, apellido: 'Pérez', tipoDocumento: 'TI', fechaNacimiento: '2015-03-01', edad: 11, telefono: null, direccion: null,
  posicion: 'Portero', foto: 'https://firmada/x', categoria, idCategoria: 'c', grupo: null, activo: true, ...extra,
})
const pagina = (items, total = items.length, tamano = 100) => ({ items, total, pagina: 0, tamano })
const franjaDto = (idFranja, categoria, dia) => ({ idFranja, idCategoria: 'c', categoria, dia, horaInicio: '17:00', horaFin: '18:30', sede: 'Horizontes', idEntrenador: null, entrenador: null, entrenadorActivo: null, grupo: null })

beforeEach(() => {
  api.apiRequest.mockReset()
  api.apiListaPaginada.mockReset()
})

describe('interruptores', () => {
  it('los dos módulos están encendidos', () => {
    expect(misJugadoresPorApi()).toBe(true)
    expect(inicioEntrenadorPorApi()).toBe(true)
  })
})

describe('cargarMisJugadores', () => {
  it('pide /usuarios/yo y una lista por categoría: activo=true, size 100, nombre de la categoría', async () => {
    api.apiRequest.mockResolvedValue(yo({ idEntrenador: 'e1', categorias: CATS }))
    api.apiListaPaginada.mockImplementation(async (_ruta, { categoria }) => pagina(categoria === 'Sub-11' ? [dto('1', 'Ana', 'Sub-11')] : [dto('2', 'Beto', 'Semillero', { grupo: 'mañana' })]))
    const r = await cargarMisJugadores()
    expect(api.apiRequest).toHaveBeenCalledWith('/usuarios/yo')
    expect(api.apiListaPaginada).toHaveBeenCalledWith('/deportistas', { page: 0, size: 100, categoria: 'Sub-11', activo: true })
    expect(api.apiListaPaginada).toHaveBeenCalledWith('/deportistas', { page: 0, size: 100, categoria: 'Semillero', activo: true })
    expect(r.categorias.map((c) => [c.nombre, c.jugadores.length])).toEqual([['Sub-11', 1], ['Semillero', 1]])
    expect(r.jugadores.map((x) => x.id)).toEqual(['1', '2'])
    expect(r.jugadores[1]).toMatchObject({ grupoEntrenamiento: 'mañana', posicion: 'Portero', foto: 'https://firmada/x', edad: 11 })
    expect(r.jugadores[0]).not.toHaveProperty('documento')
    expect(r.jugadores[0]).not.toHaveProperty('estadoInscripcion')
  })

  it('recorre las páginas de una categoría con más de 100 jugadores', async () => {
    api.apiRequest.mockResolvedValue(yo({ idEntrenador: 'e1', categorias: [CATS[0]] }))
    const primera = Array.from({ length: 100 }, (_, i) => dto(`a${i}`, 'N', 'Sub-11'))
    const segunda = [dto('b0', 'N', 'Sub-11')]
    api.apiListaPaginada.mockResolvedValueOnce(pagina(primera, 101)).mockResolvedValueOnce(pagina(segunda, 101))
    const r = await cargarMisJugadores()
    expect(api.apiListaPaginada).toHaveBeenCalledTimes(2)
    expect(api.apiListaPaginada.mock.calls[1][1]).toMatchObject({ page: 1 })
    expect(r.jugadores).toHaveLength(101)
  })

  it('une sin duplicados si un jugador sale en dos categorías', async () => {
    api.apiRequest.mockResolvedValue(yo({ idEntrenador: 'e1', categorias: CATS }))
    api.apiListaPaginada.mockResolvedValue(pagina([dto('1', 'Ana', 'Sub-11')]))
    expect((await cargarMisJugadores()).jugadores).toHaveLength(1)
  })

  it('sin entrenador vinculado no pide jugadores', async () => {
    api.apiRequest.mockResolvedValue(yo(null))
    expect(await cargarMisJugadores()).toEqual({ perfil: null, categorias: [], jugadores: [] })
    expect(api.apiListaPaginada).not.toHaveBeenCalled()
  })

  it('sin categorías no pide jugadores', async () => {
    api.apiRequest.mockResolvedValue(yo({ idEntrenador: 'e1', categorias: [] }))
    const r = await cargarMisJugadores()
    expect(r.categorias).toEqual([])
    expect(api.apiListaPaginada).not.toHaveBeenCalled()
  })

  it('un error de la API rechaza (la pantalla ofrece reintentar)', async () => {
    api.apiRequest.mockResolvedValue(yo({ idEntrenador: 'e1', categorias: CATS }))
    api.apiListaPaginada.mockRejectedValue(Object.assign(new Error('x'), { estado: 500 }))
    await expect(cargarMisJugadores()).rejects.toMatchObject({ estado: 500 })
  })
})

describe('cargarInicioEntrenador', () => {
  const rutas = (franjas) => async (ruta) => (ruta === '/usuarios/yo' ? yo({ idEntrenador: 'e1', categorias: CATS }) : franjas())

  it('cuenta jugadores por categoría y arma hoy y la semana solo con sus categorías', async () => {
    api.apiRequest.mockImplementation(rutas(async () => [franjaDto('f1', 'Sub-11', 2), franjaDto('f2', 'Semillero', 4), franjaDto('f3', 'Sub-16', 2)]))
    api.apiListaPaginada.mockImplementation(async (_r, { categoria }) => pagina(categoria === 'Sub-11' ? [dto('1', 'A', 'Sub-11'), dto('2', 'B', 'Sub-11')] : []))
    const r = await cargarInicioEntrenador({ fecha: '2026-10-20' }) // martes
    expect(api.apiRequest).toHaveBeenCalledWith('/horario-club')
    expect(r.jugadores.categorias.map((c) => [c.nombre, c.jugadores.length])).toEqual([['Sub-11', 2], ['Semillero', 0]])
    expect(r.horario.hoy.map((s) => s.idFranja)).toEqual(['f1'])
    expect(r.horario.semana.map((s) => s.idFranja)).toEqual(['f2'])
  })

  it('si falla el horario conserva los jugadores (y al revés)', async () => {
    api.apiRequest.mockImplementation(rutas(async () => { throw Object.assign(new Error('x'), { estado: 503 }) }))
    api.apiListaPaginada.mockResolvedValue(pagina([dto('1', 'A', 'Sub-11')]))
    const r = await cargarInicioEntrenador({ fecha: '2026-10-20' })
    expect(r.horario.error).toMatchObject({ estado: 503 })
    expect(r.jugadores.jugadores).toHaveLength(1)

    api.apiRequest.mockImplementation(rutas(async () => []))
    api.apiListaPaginada.mockRejectedValue(Object.assign(new Error('x'), { estado: 500 }))
    const s = await cargarInicioEntrenador({ fecha: '2026-10-20' })
    expect(s.jugadores.error).toMatchObject({ estado: 500 })
    expect(s.horario).toEqual({ hoy: [], semana: [] })
  })

  it('sin entrenador o sin categorías no pide jugadores ni horario', async () => {
    api.apiRequest.mockResolvedValue(yo(null))
    expect((await cargarInicioEntrenador()).perfil).toBeNull()
    api.apiRequest.mockResolvedValue(yo({ idEntrenador: 'e1', categorias: [] }))
    expect((await cargarInicioEntrenador()).perfil.categorias).toEqual([])
    expect(api.apiRequest).not.toHaveBeenCalledWith('/horario-club')
    expect(api.apiListaPaginada).not.toHaveBeenCalled()
  })
})
