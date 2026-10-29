import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// Un usuario revocado en usuariosService no puede iniciar sesión (mismo error INACTIVA que inactivo@pradofc.co).
// Archivo aparte para no tocar authService.test.js.
let auth
let usuarios
const ADMIN = { rol: 'administrador', correo: 'otro.admin@pradofc.co' }

beforeEach(async () => {
  globalThis.sessionStorage?.clear()
  vi.useFakeTimers()
  vi.resetModules()
  auth = await import('./authService.js')
  usuarios = await import('./usuariosService.js')
})
afterEach(() => vi.useRealTimers())

async function correr(promesa) {
  const resultado = promesa.then((valor) => ({ valor }), (error) => ({ error }))
  await vi.runAllTimersAsync()
  return resultado
}

describe('iniciarSesion con usuarios revocados', () => {
  it('revocar el acceso a una cuenta activa le impide iniciar sesión (INACTIVA)', async () => {
    expect((await correr(auth.iniciarSesion('tesorero@pradofc.co', 'Demo2026!'))).valor.rol).toBe('tesorero')
    await correr(usuarios.revocarAcceso(2, ADMIN))
    expect((await correr(auth.iniciarSesion('tesorero@pradofc.co', 'Demo2026!'))).error.codigo).toBe('INACTIVA')
  })

  it('con contraseña incorrecta no revela que la cuenta está revocada', async () => {
    await correr(usuarios.revocarAcceso(2, ADMIN))
    expect((await correr(auth.iniciarSesion('tesorero@pradofc.co', 'otra'))).error.codigo).toBe('CREDENCIALES')
  })

  it('restablecer el acceso permite volver a entrar, incluso en inactivo@pradofc.co', async () => {
    await correr(usuarios.restablecerAcceso(6, ADMIN))
    expect((await correr(auth.iniciarSesion('inactivo@pradofc.co', 'Demo2026!'))).valor.rol).toBe('acudiente')
  })
})
