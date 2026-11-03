import { beforeEach, describe, expect, it, vi } from 'vitest'

// Modo API de usuarios: el cliente HTTP está simulado (nunca hay red) y el interruptor del módulo va encendido.
const api = vi.hoisted(() => ({ apiRequest: vi.fn() }))
vi.mock('./apiClient.js', () => api)
vi.mock('./usarApi.js', () => ({ usarApiDe: (modulo) => modulo === 'usuarios' }))

import * as servicio from './usuariosService.js'

const ID = '0b8e1f6a-2c3d-4e5f-9a8b-7c6d5e4f3a2b'
const ACTOR = { rol: 'administrador', correo: 'admin@pradofc.co' }
const dto = (extra = {}) => ({ id: ID, nombre: 'Ana', apellido: 'Ruiz', correo: 'ana@pradofc.co', rol: 'tesorero', activo: true, creadoEn: '2026-09-01T10:00:00Z', ...extra })
const datos = { nombre: ' Ana ', apellido: 'Ruiz', correo: 'ana@pradofc.co', rol: 'tesorero' }
const errorApi = (estado, mensaje, campos = []) => Object.assign(new Error(mensaje), { estado, mensaje, campos, codigo: null })
const fallo = (promesa) => promesa.then(() => { throw new Error('Debía rechazar') }, (e) => e)

beforeEach(() => {
  api.apiRequest.mockReset()
})

describe('con el interruptor encendido', () => {
  it('usuariosPorApi es true', () => {
    expect(servicio.usuariosPorApi()).toBe(true)
  })

  it('listar usa GET /usuarios, adapta y filtra', async () => {
    api.apiRequest.mockResolvedValue([dto(), dto({ id: 'otro', nombre: 'Luis', correo: 'luis@pradofc.co', rol: 'ACUDIENTE', activo: false })])
    const todos = await servicio.listarUsuarios({}, ACTOR)
    expect(api.apiRequest).toHaveBeenCalledWith('/usuarios', undefined)
    expect(todos.map((u) => u.rol)).toEqual(['tesorero', 'acudiente'])
    expect((await servicio.listarUsuarios({ estado: 'revocados' }, ACTOR)).map((u) => u.id)).toEqual(['otro'])
    expect((await servicio.listarUsuarios({ texto: 'ANA' }, ACTOR)).map((u) => u.id)).toEqual([ID])
  })

  it('obtenerUsuario usa GET /usuarios/{id} y no trae historial', async () => {
    api.apiRequest.mockResolvedValue(dto())
    const r = await servicio.obtenerUsuario(ID, ACTOR)
    expect(api.apiRequest).toHaveBeenCalledWith(`/usuarios/${ID}`, undefined)
    expect(r.usuario).toMatchObject({ id: ID, creadoPor: null })
    expect(r.historial).toEqual([])
  })

  it('crear usa POST con el rol en mayúsculas', async () => {
    api.apiRequest.mockResolvedValue(dto())
    const r = await servicio.crearUsuario(datos, ACTOR)
    const [ruta, opciones] = api.apiRequest.mock.calls[0]
    expect(ruta).toBe('/usuarios')
    expect(opciones.method).toBe('POST')
    expect(JSON.parse(opciones.body)).toEqual({ nombre: 'Ana', apellido: 'Ruiz', correo: 'ana@pradofc.co', rol: 'TESORERO' })
    expect(r.usuario.id).toBe(ID)
  })

  it('crear con datos inválidos no llama a la API', async () => {
    const e = await fallo(servicio.crearUsuario({ ...datos, correo: 'x' }, ACTOR))
    expect(e.codigo).toBe('VALIDACION')
    expect(e.errores.correo).toBeTruthy()
    expect(api.apiRequest).not.toHaveBeenCalled()
  })

  it('crear con correo duplicado (409) marca el campo y NO resuelve', async () => {
    api.apiRequest.mockRejectedValue(errorApi(409, 'correo: ya está registrado en otro usuario', ['correo']))
    const e = await fallo(servicio.crearUsuario(datos, ACTOR))
    expect(e.codigo).toBe('VALIDACION')
    expect(e.errores).toEqual({ correo: 'Ya existe un usuario con ese correo.' })
  })

  it('cambiarRol usa PUT /usuarios/{id}/rol', async () => {
    api.apiRequest.mockResolvedValue(dto({ rol: 'entrenador' }))
    const r = await servicio.cambiarRol(ID, 'entrenador', ACTOR)
    const [ruta, opciones] = api.apiRequest.mock.calls[0]
    expect(ruta).toBe(`/usuarios/${ID}/rol`)
    expect(opciones.method).toBe('PUT')
    expect(JSON.parse(opciones.body)).toEqual({ rol: 'ENTRENADOR' })
    expect(r.usuario.rol).toBe('entrenador')
  })

  it('revocar y restablecer usan POST sin cuerpo', async () => {
    api.apiRequest.mockResolvedValueOnce(dto({ activo: false })).mockResolvedValueOnce(dto())
    expect((await servicio.revocarAcceso(ID, ACTOR)).usuario.activo).toBe(false)
    expect((await servicio.restablecerAcceso(ID, ACTOR)).usuario.activo).toBe(true)
    expect(api.apiRequest.mock.calls[0]).toEqual([`/usuarios/${ID}/revocar`, { method: 'POST' }])
    expect(api.apiRequest.mock.calls[1]).toEqual([`/usuarios/${ID}/restablecer`, { method: 'POST' }])
  })

  it('el 409 del último administrador llega con el mensaje del backend', async () => {
    api.apiRequest.mockRejectedValue(errorApi(409, 'Debe existir al menos un administrador activo.', ['activo']))
    const e = await fallo(servicio.revocarAcceso(ID, ACTOR))
    expect(e.message).toBe('Debe existir al menos un administrador activo.')
    expect(e.estado).toBe(409)
  })

  it('un 404 es USUARIO_NO_EXISTE', async () => {
    api.apiRequest.mockRejectedValue(errorApi(404, 'No existe un usuario con ese id.'))
    expect((await fallo(servicio.obtenerUsuario(ID, ACTOR))).codigo).toBe('USUARIO_NO_EXISTE')
  })
})
