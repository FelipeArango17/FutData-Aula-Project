import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// El servicio guarda las fotos en memoria a nivel de módulo: cada prueba carga una copia nueva.
let servicio
beforeEach(async () => {
  vi.useFakeTimers()
  vi.resetModules()
  servicio = await import('./fotosService.js')
})
afterEach(() => vi.useRealTimers())

const resolver = async (promesa) => {
  const resultado = promesa.then((v) => ({ v }), (e) => ({ e }))
  await vi.runAllTimersAsync()
  const { v, e } = await resultado
  if (e) throw e
  return v
}

const admin = { rol: 'administrador', correo: 'administrador@pradofc.co' }
const foto = (tipo = 'image/png', bytes = 2000) => new File([new Uint8Array(bytes)], 'foto.png', { type: tipo })

describe('fotosService', () => {
  it('sin foto devuelve null', async () => {
    expect(await resolver(servicio.obtenerFoto(1))).toBeNull()
  })

  it('guarda la foto y la devuelve (el id puede ser texto)', async () => {
    const url = await resolver(servicio.guardarFoto(1, foto(), admin))
    expect(url).toMatch(/^blob:/)
    expect(await resolver(servicio.obtenerFoto('1'))).toBe(url)
    expect(await resolver(servicio.obtenerFoto(2))).toBeNull()
  })

  it('una foto nueva reemplaza a la anterior', async () => {
    const primera = await resolver(servicio.guardarFoto(1, foto(), admin))
    const segunda = await resolver(servicio.guardarFoto(1, foto('image/jpeg'), admin))
    expect(segunda).not.toBe(primera)
    expect(await resolver(servicio.obtenerFoto(1))).toBe(segunda)
  })

  it('un tipo no permitido o un archivo vacío lanzan ARCHIVO_INVALIDO y no guardan nada', async () => {
    await expect(resolver(servicio.guardarFoto(1, foto('application/pdf'), admin))).rejects.toMatchObject({
      codigo: 'ARCHIVO_INVALIDO', message: 'Solo se aceptan archivos JPG o PNG.',
    })
    await expect(resolver(servicio.guardarFoto(1, foto('image/png', 0), admin))).rejects.toMatchObject({ codigo: 'ARCHIVO_INVALIDO' })
    await expect(resolver(servicio.guardarFoto(1, null, admin))).rejects.toMatchObject({ codigo: 'ARCHIVO_INVALIDO' })
    expect(await resolver(servicio.obtenerFoto(1))).toBeNull()
  })

  it('un archivo de más de 5 MB lanza ARCHIVO_INVALIDO', async () => {
    const grande = { name: 'g.png', type: 'image/png', size: 5 * 1024 * 1024 + 1 }
    await expect(resolver(servicio.guardarFoto(1, grande, admin))).rejects.toMatchObject({ codigo: 'ARCHIVO_INVALIDO' })
  })

  it('solo el administrador sube o quita fotos', async () => {
    for (const actor of [{ rol: 'entrenador' }, { rol: 'acudiente' }, null]) {
      await expect(resolver(servicio.guardarFoto(1, foto(), actor))).rejects.toMatchObject({ codigo: 'SIN_PERMISO' })
      await expect(resolver(servicio.quitarFoto(1, actor))).rejects.toMatchObject({ codigo: 'SIN_PERMISO' })
    }
  })

  it('quitar la foto la borra; quitar una que no existe no falla', async () => {
    await resolver(servicio.guardarFoto(1, foto(), admin))
    await resolver(servicio.quitarFoto(1, admin))
    expect(await resolver(servicio.obtenerFoto(1))).toBeNull()
    await expect(resolver(servicio.quitarFoto(1, admin))).resolves.toBeUndefined()
  })
})
