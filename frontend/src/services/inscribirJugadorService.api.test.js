import { beforeEach, describe, expect, it, vi } from 'vitest'

// Con la API de deportistas el asistente guarda al deportista y su foto; el acudiente y los documentos no tienen endpoints todavía.
const api = vi.hoisted(() => ({ apiRequest: vi.fn(), apiListaPaginada: vi.fn(), subirArchivo: vi.fn() }))
vi.mock('./apiClient.js', () => api)
vi.mock('./usarApi.js', () => ({ usarApiDe: (modulo) => modulo === 'deportistas' }))
const acudientes = vi.hoisted(() => ({ guardarAcudiente: vi.fn(), vincularAcudiente: vi.fn(), ACUDIENTE_VACIO: {}, validarDatosAcudiente: () => ({}) }))
vi.mock('./acudientesService.js', () => acudientes)

import { inscribirJugador } from './inscribirJugadorService.js'

const admin = { rol: 'administrador', correo: 'administrador@pradofc.co' }
const hoy = new Date()
const jugador = { nombre: 'Lucas', apellido: 'Prueba', tipoDocumento: 'Tarjeta de identidad', documento: '1200000001', fechaNacimiento: `${hoy.getFullYear() - 9}-05-10`, telefono: '3000000099', direccion: 'Calle 50 # 10-20', grupoEntrenamiento: 'tarde', posicion: 'Portero' }
const acudiente = { nombre: 'Claudia', apellido: 'Ruiz', correo: 'claudia.ruiz@correo.com', telefono: '3001234567', parentesco: 'Madre' }

beforeEach(() => {
  for (const f of [...Object.values(api), acudientes.guardarAcudiente, acudientes.vincularAcudiente]) f.mockReset()
})

describe('inscribirJugador con la API de deportistas', () => {
  it('crea al deportista y sube la foto, y avisa que el resto no se pudo (INSCRIPCION_PARCIAL con el UUID para ir a la ficha)', async () => {
    api.apiRequest.mockImplementation(async (ruta) => (ruta === '/categorias' ? [] : { id: 'uuid-1', nombre: 'Lucas', apellido: 'Prueba', tipoDocumento: 'TI', documento: '1200000001', fechaNacimiento: jugador.fechaNacimiento, activo: true }))
    api.subirArchivo.mockResolvedValue({ foto: 'https://x/f' })
    const foto = { name: 'f.png', type: 'image/png', size: 100 }
    const fallo = await inscribirJugador({ deportista: jugador, acudiente, documentos: [], foto }, admin).catch((e) => e)
    expect(fallo).toMatchObject({ codigo: 'INSCRIPCION_PARCIAL', causa: 'NO_DISPONIBLE', deportista: { id: 'uuid-1' } })
    const post = api.apiRequest.mock.calls.find(([ruta]) => ruta === '/deportistas')
    expect(post[1].method).toBe('POST')
    expect(JSON.parse(post[1].body).tipoDocumento).toBe('TI')
    expect(api.subirArchivo).toHaveBeenCalledWith('/deportistas/uuid-1/foto', foto)
    expect(acudientes.guardarAcudiente).not.toHaveBeenCalled()
  })
  it('un 409 de documento repetido vuelve al paso 1 con el mensaje del backend', async () => {
    api.apiRequest.mockImplementation(async (ruta) => {
      if (ruta === '/categorias') return []
      throw Object.assign(new Error('x'), { estado: 409, mensaje: 'documento: ya está registrado en otro deportista', campos: ['documento'], codigo: null })
    })
    await expect(inscribirJugador({ deportista: jugador, acudiente, documentos: [], foto: null }, admin)).rejects.toMatchObject({
      codigo: 'DOCUMENTO_DUPLICADO', paso: 1, errores: { documento: 'Ya está registrado en otro deportista.' },
    })
  })
})
