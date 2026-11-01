import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

let servicio
beforeEach(async () => {
  vi.useFakeTimers()
  vi.resetModules()
  servicio = await import('./acudientesService.js')
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
const datos = (extra = {}) => ({ nombre: 'Claudia', apellido: 'Ruiz', correo: 'claudia@correo.com', telefono: '3001234567', parentesco: 'Madre', ...extra })

describe('acudientesService', () => {
  it('los parentescos son Madre, Padre, Abuelo(a), Tío(a) y Otro', () => {
    expect(servicio.PARENTESCOS).toEqual(['Madre', 'Padre', 'Abuelo(a)', 'Tío(a)', 'Otro'])
  })

  it('la cuenta demo tiene a Santiago y Valeria vinculados', async () => {
    expect(await resolver(servicio.obtenerAcudienteDe(1))).toMatchObject({ correo: 'acudiente@pradofc.co', parentesco: 'Madre' })
    expect(await resolver(servicio.obtenerAcudienteDe(2))).toMatchObject({ correo: 'acudiente@pradofc.co' })
    expect(await resolver(servicio.obtenerAcudienteDe(3))).toBeNull()
  })

  it('guardar crea uno nuevo, y con el mismo correo (sin importar mayúsculas) reutiliza el existente', async () => {
    const a = await resolver(servicio.guardarAcudiente(datos(), admin))
    expect(a).toMatchObject({ creado: true, acudiente: { id: 2, nombre: 'Claudia' } })
    const b = await resolver(servicio.guardarAcudiente(datos({ nombre: 'Otra', correo: 'CLAUDIA@correo.com' }), admin))
    expect(b).toMatchObject({ creado: false, acudiente: { id: 2, nombre: 'Claudia' } })
  })

  it('valida con los validadores compartidos y exige administrador', async () => {
    await expect(resolver(servicio.guardarAcudiente(datos({ nombre: '', correo: 'x', telefono: '1', parentesco: 'Vecino' }), admin)))
      .rejects.toMatchObject({ codigo: 'VALIDACION', errores: { nombre: expect.any(String), correo: expect.any(String), telefono: expect.any(String), parentesco: expect.any(String) } })
    await expect(resolver(servicio.guardarAcudiente(datos(), { rol: 'acudiente' }))).rejects.toMatchObject({ codigo: 'SIN_PERMISO' })
  })

  it('un deportista tiene un solo acudiente: vincular de nuevo reemplaza el vínculo', async () => {
    const { acudiente } = await resolver(servicio.guardarAcudiente(datos(), admin))
    await resolver(servicio.vincularAcudiente(1, acudiente.id, 'Tío(a)', admin))
    expect(await resolver(servicio.obtenerAcudienteDe(1))).toMatchObject({ correo: 'claudia@correo.com', parentesco: 'Tío(a)' })
    await expect(resolver(servicio.vincularAcudiente(1, 1, 'Vecino', admin))).rejects.toMatchObject({ codigo: 'VALIDACION' })
  })
})
