import { beforeEach, describe, expect, it, vi } from 'vitest'

// Modo API del horario del club: el cliente HTTP está simulado (nunca hay red) y el interruptor del módulo va encendido.
const api = vi.hoisted(() => ({ apiRequest: vi.fn() }))
vi.mock('./apiClient.js', () => api)
vi.mock('./usarApi.js', () => ({ usarApiDe: (modulo) => modulo === 'horarioclub' }))

import * as servicio from './horarioClubService.js'

const ID = '0b8e1f6a-2c3d-4e5f-9a8b-7c6d5e4f3a2b'
const CAT = '6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b'
const ENT = 'e0a1e0a1-0000-4000-8000-000000000001'
const dto = (extra = {}) => ({
  idFranja: ID, idCategoria: CAT, categoria: 'Sub-12', dia: 1, horaInicio: '19:00', horaFin: '20:00', sede: 'Horizontes',
  idEntrenador: ENT, entrenador: 'Luis Pérez', entrenadorActivo: true, grupo: null, ...extra,
})
const datos = { idCategoria: CAT, dia: '1', horaInicio: '19:00', horaFin: '20:00', sede: 'Horizontes', idEntrenador: ENT, grupo: '' }
const errorApi = (estado, mensaje, campos = []) => Object.assign(new Error(mensaje), { estado, mensaje, campos, codigo: null })

// Lo que trae el error rechazado, como objeto simple (toMatchObject sobre un Error compara distinto)
const fallo = (promesa) => promesa.then(() => { throw new Error('Debía rechazar') }, (e) => ({ ...e, message: e.message }))

// Con llaves: si beforeEach devolviera el mock, Vitest lo llamaría al terminar cada prueba como limpieza
beforeEach(() => {
  api.apiRequest.mockReset()
})

describe('con el interruptor encendido', () => {
  it('horarioClubPorApi es true', () => {
    expect(servicio.horarioClubPorApi()).toBe(true)
  })

  it('listar usa GET /horario-club y adapta las franjas', async () => {
    api.apiRequest.mockResolvedValue([dto(), dto({ idFranja: 'otra', idEntrenador: null, entrenador: null, entrenadorActivo: null })])
    const lista = await servicio.listar()
    expect(api.apiRequest).toHaveBeenCalledWith('/horario-club')
    expect(lista[0]).toMatchObject({ id: ID, categoria: 'Sub-12', entrenador: 'Luis Pérez' })
    expect(lista[1]).toMatchObject({ id: 'otra', idEntrenador: null })
  })

  it('listar pasa los filtros categoria, entrenador y sede', async () => {
    api.apiRequest.mockResolvedValue([])
    await servicio.listar({ categoria: 'Sub-12', entrenador: ENT, sede: 'Horizontes' })
    expect(api.apiRequest).toHaveBeenCalledWith(`/horario-club?categoria=Sub-12&entrenador=${ENT}&sede=Horizontes`)
  })

  it('crear usa POST con los siete campos y devuelve la franja adaptada', async () => {
    api.apiRequest.mockResolvedValue(dto())
    const creada = await servicio.crear(datos)
    const [ruta, opciones] = api.apiRequest.mock.calls[0]
    expect(ruta).toBe('/horario-club')
    expect(opciones.method).toBe('POST')
    expect(JSON.parse(opciones.body)).toEqual({ idCategoria: CAT, dia: 1, horaInicio: '19:00', horaFin: '20:00', sede: 'Horizontes', idEntrenador: ENT, grupo: null })
    expect(creada.id).toBe(ID)
  })

  it('editar usa PUT en /horario-club/{id} y manda todos los campos', async () => {
    api.apiRequest.mockResolvedValue(dto({ horaFin: '20:30' }))
    const editada = await servicio.editar(ID, { ...datos, horaFin: '20:30', idEntrenador: '' })
    const [ruta, opciones] = api.apiRequest.mock.calls[0]
    expect(ruta).toBe(`/horario-club/${ID}`)
    expect(opciones.method).toBe('PUT')
    expect(JSON.parse(opciones.body)).toEqual({ idCategoria: CAT, dia: 1, horaInicio: '19:00', horaFin: '20:30', sede: 'Horizontes', idEntrenador: null, grupo: null })
    expect(editada.horaFin).toBe('20:30')
  })

  it('eliminar usa DELETE y acepta el 204 sin cuerpo', async () => {
    api.apiRequest.mockResolvedValue(null)
    await expect(servicio.eliminar(ID)).resolves.toEqual({ id: ID })
    expect(api.apiRequest).toHaveBeenCalledWith(`/horario-club/${ID}`, { method: 'DELETE' })
  })
})

describe('errores de la API', () => {
  it('el 409 de cruce llega con el mensaje del backend, marcado en el campo idEntrenador', async () => {
    const mensaje = 'Ese entrenador ya tiene otra franja que se cruza con este horario'
    api.apiRequest.mockRejectedValue(errorApi(409, mensaje, ['idEntrenador']))
    for (const llamar of [() => servicio.crear(datos), () => servicio.editar(ID, datos)]) {
      expect(await fallo(llamar())).toMatchObject({ estado: 409, mensaje, errores: { idEntrenador: mensaje } })
    }
  })

  it('el 400 marca cada campo con su texto', async () => {
    api.apiRequest.mockRejectedValue(errorApi(400, 'horaInicio: debe tener el formato HH:mm de 24 horas, por ejemplo 07:30 o 19:00; sede: es obligatoria', ['horaInicio', 'sede']))
    expect(await fallo(servicio.crear(datos))).toMatchObject({
      estado: 400,
      errores: { horaInicio: 'Debe tener el formato HH:mm de 24 horas, por ejemplo 07:30 o 19:00.', sede: 'Es obligatoria.' },
    })
  })

  it('403 y 404 no traen campos: se propagan con su mensaje, sin errores por campo', async () => {
    api.apiRequest.mockRejectedValue(errorApi(403, 'No tienes permiso para esta acción.'))
    expect(await fallo(servicio.crear(datos))).toMatchObject({ estado: 403, mensaje: 'No tienes permiso para esta acción.' })
    expect(await fallo(servicio.crear(datos))).not.toHaveProperty('errores')
    api.apiRequest.mockRejectedValue(errorApi(404, 'La franja no existe.'))
    expect(await fallo(servicio.editar(ID, datos))).toMatchObject({ estado: 404, mensaje: 'La franja no existe.' })
    expect(await fallo(servicio.eliminar(ID))).toMatchObject({ estado: 404, mensaje: 'La franja no existe.' })
  })

  it('listar propaga el error de la API', async () => {
    api.apiRequest.mockRejectedValue(errorApi(403, 'No tienes permiso.'))
    expect(await fallo(servicio.listar())).toMatchObject({ estado: 403 })
  })
})
