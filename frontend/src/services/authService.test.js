import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// El servicio guarda su estado a nivel de módulo: cada prueba carga una copia nueva.
// Se usan relojes simulados: el retardo y el paso del tiempo no esperan de verdad.
let auth
const CLAVE = 'Demo2026!'
const CODIGO = '123456'

beforeEach(async () => {
  // Algunas versiones de Node traen sessionStorage propio y el estado del servicio se guarda ahí: se limpia entre pruebas
  globalThis.sessionStorage?.clear()
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2026-10-22T10:00:00Z'))
  vi.resetModules()
  auth = await import('./authService.js')
})
afterEach(() => vi.useRealTimers())

// Ejecuta una llamada del servicio dejando pasar su retardo simulado; devuelve { valor } o { error }
async function correr(promesa) {
  const resultado = promesa.then((valor) => ({ valor }), (error) => ({ error }))
  await vi.advanceTimersByTimeAsync(auth.RETARDO_MS)
  return resultado
}
const entrar = (correo = 'acudiente@pradofc.co', clave = CLAVE) => correr(auth.iniciarSesion(correo, clave))
const verificar = (codigo, opciones) => correr(auth.verificarCodigo(codigo, opciones))
const minutos = (n) => vi.advanceTimersByTimeAsync(n * 60 * 1000)

describe('iniciarSesion', () => {
  it('con credenciales válidas devuelve el rol de la cuenta, para cada rol', async () => {
    for (const rol of ['acudiente', 'entrenador', 'tesorero', 'administrador']) {
      const { valor } = await entrar(`${rol}@pradofc.co`)
      expect(valor).toMatchObject({ rol, correo: `${rol}@pradofc.co` })
    }
  })

  it('ignora mayúsculas y espacios del correo', async () => {
    const { valor } = await entrar('  Entrenador@PradoFC.co ')
    expect(valor.rol).toBe('entrenador')
  })

  it('contraseña incorrecta y correo desconocido dan el MISMO error genérico', async () => {
    const mala = await entrar('acudiente@pradofc.co', 'otra')
    const desconocido = await entrar('nadie@pradofc.co', CLAVE)
    expect(mala.error.codigo).toBe('CREDENCIALES')
    expect(desconocido.error.codigo).toBe('CREDENCIALES')
    expect(mala.error.message).toBe(desconocido.error.message)
  })

  it('cuenta inactiva con contraseña correcta da INACTIVA; con contraseña mala no revela que existe', async () => {
    expect((await entrar('inactivo@pradofc.co')).error.codigo).toBe('INACTIVA')
    expect((await entrar('inactivo@pradofc.co', 'otra')).error.codigo).toBe('CREDENCIALES')
  })

  it('simula una falla de red solo en el primer intento de red@pradofc.co', async () => {
    expect((await entrar('red@pradofc.co')).error.codigo).toBe('RED')
    expect((await entrar('red@pradofc.co')).valor.rol).toBe('tesorero')
  })

  it('espera el retardo simulado antes de responder', async () => {
    let listo = false
    auth.iniciarSesion('acudiente@pradofc.co', CLAVE).then(() => { listo = true })
    await vi.advanceTimersByTimeAsync(auth.RETARDO_MS - 1)
    expect(listo).toBe(false)
    await vi.advanceTimersByTimeAsync(1)
    expect(listo).toBe(true)
  })

  it('deja una solicitud de código pendiente con el correo enmascarado', async () => {
    expect(auth.estadoCodigo()).toBeNull()
    await entrar('administrador@pradofc.co')
    expect(auth.estadoCodigo()).toMatchObject({
      correo: 'administrador@pradofc.co',
      correoEnmascarado: 'a***@pradofc.co',
      intentosRestantes: 5,
      bloqueoHasta: null,
    })
  })

  it('un inicio de sesión fallido no crea solicitud de código', async () => {
    await entrar('acudiente@pradofc.co', 'mala')
    expect(auth.estadoCodigo()).toBeNull()
  })
})

describe('verificarCodigo', () => {
  beforeEach(() => entrar())

  it('con el código correcto devuelve la sesión y cierra la solicitud', async () => {
    const { valor } = await verificar(CODIGO)
    expect(valor).toEqual({ correo: 'acudiente@pradofc.co', rol: 'acudiente', nombre: 'María Torres' })
    expect(auth.estadoCodigo()).toBeNull()
    expect((await verificar(CODIGO)).error.codigo).toBe('SIN_SOLICITUD')
  })

  it('con menos de 6 dígitos o caracteres que no son números da FORMATO y no gasta intentos', async () => {
    for (const malo of ['', '12345', '1234567', '12345a', '12 456']) {
      expect((await verificar(malo)).error.codigo).toBe('FORMATO')
    }
    expect(auth.estadoCodigo().intentosRestantes).toBe(5)
  })

  it('un código incorrecto cuenta el intento y dice cuántos quedan', async () => {
    const restantes = []
    for (let i = 0; i < 4; i++) {
      const { error } = await verificar('000000')
      expect(error.codigo).toBe('INCORRECTO')
      restantes.push(error.intentosRestantes)
    }
    expect(restantes).toEqual([4, 3, 2, 1])
    expect(auth.estadoCodigo().intentosRestantes).toBe(1)
  })

  it('el código correcto después de fallos todavía entra', async () => {
    await verificar('000000')
    await verificar('111111')
    expect((await verificar(CODIGO)).valor.rol).toBe('acudiente')
  })

  it('el 5.º intento fallido bloquea 15 minutos, incluso con el código correcto', async () => {
    for (let i = 0; i < 4; i++) await verificar('000000')
    const quinto = await verificar('000000')
    expect(quinto.error.codigo).toBe('BLOQUEADO')
    expect(quinto.error.msRestantes).toBe(auth.BLOQUEO_MS)
    expect(auth.estadoCodigo().bloqueoHasta).not.toBeNull()

    const conCorrecto = await verificar(CODIGO)
    expect(conCorrecto.error.codigo).toBe('BLOQUEADO')
    expect(conCorrecto.error.msRestantes).toBeLessThanOrEqual(auth.BLOQUEO_MS)
  })

  it('el bloqueo también impide iniciar sesión con esa cuenta y termina a los 15 minutos', async () => {
    for (let i = 0; i < 5; i++) await verificar('000000')
    expect((await entrar()).error.codigo).toBe('BLOQUEADO')
    await minutos(14)
    expect((await entrar()).error.codigo).toBe('BLOQUEADO')
    await minutos(1)
    expect((await entrar()).valor.rol).toBe('acudiente')
  })

  it('el bloqueo de una cuenta no afecta a otra', async () => {
    for (let i = 0; i < 5; i++) await verificar('000000')
    expect((await entrar('tesorero@pradofc.co')).valor.rol).toBe('tesorero')
  })

  it('el código vence a los 10 minutos', async () => {
    await minutos(9)
    await vi.advanceTimersByTimeAsync(59 * 1000)
    // aún vigente (9 min 59 s + retardo no pasa de 10 min)
    expect((await verificar('000000')).error.codigo).toBe('INCORRECTO')
    await vi.advanceTimersByTimeAsync(2 * 1000)
    const vencido = await verificar(CODIGO)
    expect(vencido.error.codigo).toBe('VENCIDO')
  })

  it('un código vencido no gasta intentos', async () => {
    await minutos(11)
    await verificar('000000')
    expect(auth.estadoCodigo().intentosRestantes).toBe(5)
  })

  it('recordar el equipo solo deja un indicador, no cambia el resultado', async () => {
    expect(auth.equipoRecordado('acudiente@pradofc.co')).toBe(false)
    expect((await verificar(CODIGO, { recordar: true })).valor.rol).toBe('acudiente')
    expect(auth.equipoRecordado('Acudiente@pradofc.co')).toBe(true)
  })
})

describe('reenviarCodigo', () => {
  beforeEach(() => entrar())

  it('no deja reenviar antes de un minuto y dice cuánto falta', async () => {
    const { error } = await correr(auth.reenviarCodigo())
    expect(error.codigo).toBe('DEMASIADO_PRONTO')
    expect(error.msRestantes).toBeGreaterThan(0)
    expect(error.msRestantes).toBeLessThanOrEqual(auth.REENVIO_MS)
  })

  it('deja reenviar pasado un minuto, y luego vuelve a limitar a uno por minuto', async () => {
    await minutos(1)
    expect((await correr(auth.reenviarCodigo())).valor.reenvioDisponibleEn).toBeGreaterThan(Date.now())
    expect((await correr(auth.reenviarCodigo())).error.codigo).toBe('DEMASIADO_PRONTO')
    await minutos(1)
    expect((await correr(auth.reenviarCodigo())).error).toBeUndefined()
  })

  it('reinicia los intentos y la vigencia del código', async () => {
    await verificar('000000')
    await verificar('000000')
    expect(auth.estadoCodigo().intentosRestantes).toBe(3)
    await minutos(9)
    await correr(auth.reenviarCodigo())
    expect(auth.estadoCodigo().intentosRestantes).toBe(5)
    await minutos(9) // más de 10 minutos desde el primer código, pero el nuevo sigue vigente
    expect((await verificar(CODIGO)).valor.rol).toBe('acudiente')
  })

  it('no reenvía mientras hay bloqueo', async () => {
    for (let i = 0; i < 5; i++) await verificar('000000')
    await minutos(2)
    expect((await correr(auth.reenviarCodigo())).error.codigo).toBe('BLOQUEADO')
  })

  it('volver a iniciar sesión no devuelve los intentos del mismo código', async () => {
    await verificar('000000')
    await entrar()
    expect(auth.estadoCodigo().intentosRestantes).toBe(4)
  })

  it('sin solicitud pendiente no hay nada que reenviar', async () => {
    auth.cancelarSolicitud()
    expect((await correr(auth.reenviarCodigo())).error.codigo).toBe('SIN_SOLICITUD')
  })
})

describe('enmascararCorreo', () => {
  it('deja la primera letra, tres asteriscos y el dominio', () => {
    expect(auth.enmascararCorreo('carlos@correo.com')).toBe('c***@correo.com')
    expect(auth.enmascararCorreo('maria.torres@pradofc.co')).toBe('m***@pradofc.co')
    expect(auth.enmascararCorreo('a@x.co')).toBe('a***@x.co')
  })
})
