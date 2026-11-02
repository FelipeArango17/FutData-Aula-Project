import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({ apiRequest: vi.fn(), subirArchivo: vi.fn() }))
vi.mock('./apiClient.js', () => api)
vi.mock('./usarApi.js', () => ({ usarApiDe: (modulo) => modulo === 'deportistas' }))

import { guardarFoto, obtenerFoto, obtenerFotos, quitarFoto } from './fotosService.js'

const ID = '6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b'
const archivo = { name: 'foto.png', type: 'image/png', size: 1000 }
const error = (estado, mensaje, campos = []) => Object.assign(new Error(mensaje), { estado, mensaje, campos, codigo: null })

beforeEach(() => {
  api.apiRequest.mockReset()
  api.subirArchivo.mockReset()
})

describe('fotos con la API de deportistas', () => {
  it('guardarFoto: POST multipart a /deportistas/{id}/foto (parte "archivo" de subirArchivo) y devuelve la URL firmada', async () => {
    api.subirArchivo.mockResolvedValue({ id: ID, foto: 'https://x/firmada?token=1' })
    expect(await guardarFoto(ID, archivo, { rol: 'administrador' })).toBe('https://x/firmada?token=1')
    expect(api.subirArchivo).toHaveBeenCalledWith(`/deportistas/${ID}/foto`, archivo)
  })
  it('guardarFoto: el 400 de archivo (tamaño o tipo) y el 503 de Storage llegan con el mensaje del backend', async () => {
    api.subirArchivo.mockRejectedValueOnce(error(400, 'archivo: la foto no puede pesar más de 5 MB', ['archivo']))
    await expect(guardarFoto(ID, archivo)).rejects.toMatchObject({ mensaje: 'archivo: la foto no puede pesar más de 5 MB' })
    api.subirArchivo.mockRejectedValueOnce(error(503, 'El almacenamiento no responde. Intenta de nuevo.'))
    await expect(guardarFoto(ID, archivo)).rejects.toMatchObject({ estado: 503, message: 'El almacenamiento no responde. Intenta de nuevo.' })
  })
  it('quitarFoto: DELETE /deportistas/{id}/foto', async () => {
    api.apiRequest.mockResolvedValue({ id: ID, foto: null })
    await quitarFoto(ID)
    expect(api.apiRequest).toHaveBeenCalledWith(`/deportistas/${ID}/foto`, { method: 'DELETE' })
  })
  it('obtenerFoto lee la URL del deportista; obtenerFotos no llama a la API (la lista ya trae las fotos)', async () => {
    api.apiRequest.mockResolvedValue({ id: ID, foto: 'https://x/f' })
    expect(await obtenerFoto(ID)).toBe('https://x/f')
    api.apiRequest.mockClear()
    expect(await obtenerFotos()).toEqual({})
    expect(api.apiRequest).not.toHaveBeenCalled()
  })
})
