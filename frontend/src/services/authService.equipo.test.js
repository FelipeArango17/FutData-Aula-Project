import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// Equipo de confianza y cierre de sesión global (HU-003). Archivo aparte para no tocar authService.test.js.
// localStorage se reemplaza por uno falso (el de Node no sirve en Vitest) y el servicio se carga de nuevo en cada prueba.
const CLAVE = 'Demo2026!'
const CODIGO = '123456'
const CLAVE_EQUIPOS = 'futdata.equipoConfiable'
const INICIO = new Date('2026-10-22T10:00:00.000Z')
const DIA = 24 * 60 * 60 * 1000
const ADMIN = { rol: 'administrador', correo: 'otro.admin@pradofc.co' }

function almacenFalso(inicial = {}) {
  const datos = new Map(Object.entries(inicial))
  return {
    getItem: (k) => (datos.has(k) ? datos.get(k) : null),
    setItem: (k, v) => void datos.set(k, String(v)),
    removeItem: (k) => void datos.delete(k),
    clear: () => datos.clear(),
  }
}

let auth
let usuarios
let almacen
let ahora

async function cargar() {
  vi.resetModules()
  auth = await import('./authService.js')
  usuarios = await import('./usuariosService.js')
  auth.configurarSimulacion({ ahora: () => new Date(ahora) })
}

beforeEach(async () => {
  vi.useFakeTimers()
  vi.setSystemTime(INICIO)
  ahora = INICIO.getTime()
  almacen = almacenFalso()
  vi.stubGlobal('localStorage', almacen)
  vi.stubGlobal('sessionStorage', almacenFalso())
  await cargar()
})
afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

// Ejecuta una llamada del servicio dejando pasar su retardo simulado; devuelve { valor } o { error }
async function correr(promesa) {
  const resultado = promesa.then((valor) => ({ valor }), (error) => ({ error }))
  await vi.advanceTimersByTimeAsync(auth.RETARDO_MS)
  return resultado
}
const entrar = (correo = 'acudiente@pradofc.co', clave = CLAVE) => correr(auth.iniciarSesion(correo, clave))
const verificar = (codigo = CODIGO, opciones) => correr(auth.verificarCodigo(codigo, opciones))
const guardados = () => JSON.parse(almacen.getItem(CLAVE_EQUIPOS) ?? '[]')
// Deja el equipo de confianza para ese correo (inicio de sesión + código + casilla)
async function recordar(correo = 'acudiente@pradofc.co') {
  await entrar(correo)
  await verificar(CODIGO, { recordar: true })
}

describe('recordar este equipo', () => {
  it('con código correcto y casilla marcada guarda el correo con vencimiento a 30 días', async () => {
    await recordar()
    expect(guardados()).toEqual([{ correo: 'acudiente@pradofc.co', expiraEn: new Date(INICIO.getTime() + 30 * DIA).toISOString() }])
    expect(auth.equipoRecordado('Acudiente@pradofc.co')).toBe(true)
    expect(auth.estadoEquipo('acudiente@pradofc.co')).toEqual({ expiraEn: '2026-11-21T10:00:00.000Z' })
  })

  it('sin la casilla, o con un código incorrecto, no guarda nada', async () => {
    await entrar()
    await verificar('000000', { recordar: true })
    expect(guardados()).toEqual([])
    await verificar(CODIGO)
    expect(guardados()).toEqual([])
    expect(auth.equipoRecordado('acudiente@pradofc.co')).toBe(false)
  })

  it('al vencer y volver a verificar el código, se recuerda con un vencimiento nuevo y sin duplicar la entrada', async () => {
    await recordar()
    ahora += 31 * DIA
    await recordar()
    expect(guardados()).toHaveLength(1)
    expect(guardados()[0].expiraEn).toBe(new Date(ahora + 30 * DIA).toISOString())
  })
})

describe('iniciarSesion con equipo de confianza', () => {
  it('con equipo vigente no pide el código: entra directo y no queda solicitud pendiente', async () => {
    await recordar()
    ahora += 5 * DIA
    const { valor } = await entrar()
    expect(valor).toMatchObject({ correo: 'acudiente@pradofc.co', rol: 'acudiente', verificada: true })
    expect(auth.estadoCodigo()).toBe(null)
  })

  it('la contraseña siempre se pide: con una incorrecta no omite nada', async () => {
    await recordar()
    expect((await entrar('acudiente@pradofc.co', 'otra')).error.codigo).toBe('CREDENCIALES')
  })

  it('un equipo de confianza de un correo no sirve para otro', async () => {
    await recordar('acudiente@pradofc.co')
    const { valor } = await entrar('tesorero@pradofc.co')
    expect(valor.verificada).toBeUndefined()
    expect(auth.estadoCodigo().correo).toBe('tesorero@pradofc.co')
  })

  it('con el equipo vencido pide el código como siempre y limpia la entrada', async () => {
    await recordar()
    ahora += 31 * DIA
    const { valor } = await entrar()
    expect(valor.verificada).toBeUndefined()
    expect(auth.estadoCodigo()).not.toBe(null)
    expect(guardados()).toEqual([])
    expect(auth.equipoRecordado('acudiente@pradofc.co')).toBe(false)
  })

  it('un día antes de vencer sigue vigente', async () => {
    await recordar()
    ahora += 30 * DIA - 1000
    expect((await entrar()).valor.verificada).toBe(true)
  })

  it('un usuario revocado sigue siendo INACTIVA aunque el equipo sea de confianza', async () => {
    await recordar('tesorero@pradofc.co')
    await correr(usuarios.revocarAcceso(2, ADMIN))
    expect((await entrar('tesorero@pradofc.co')).error.codigo).toBe('INACTIVA')
    // y al restablecerlo, el equipo de confianza vuelve a valer
    await correr(usuarios.restablecerAcceso(2, ADMIN))
    expect((await entrar('tesorero@pradofc.co')).valor.verificada).toBe(true)
  })

  it('una cuenta inactiva (inactivo@pradofc.co) no entra aunque tenga una entrada de confianza', async () => {
    almacen.setItem(CLAVE_EQUIPOS, JSON.stringify([{ correo: 'inactivo@pradofc.co', expiraEn: new Date(ahora + DIA).toISOString() }]))
    expect((await entrar('inactivo@pradofc.co')).error.codigo).toBe('INACTIVA')
  })

  it('el bloqueo por intentos manda sobre el equipo de confianza', async () => {
    await entrar()
    for (let i = 0; i < 4; i++) expect((await verificar('000000')).error.codigo).toBe('INCORRECTO')
    expect((await verificar('000000')).error.codigo).toBe('BLOQUEADO')
    // el equipo se marca de confianza después (por ejemplo, desde otra pestaña): el bloqueo sigue valiendo
    almacen.setItem(CLAVE_EQUIPOS, JSON.stringify([{ correo: 'acudiente@pradofc.co', expiraEn: new Date(ahora + DIA).toISOString() }]))
    expect((await entrar()).error.codigo).toBe('BLOQUEADO')
    // pasado el bloqueo (15 min), el equipo de confianza vuelve a omitir el código
    await vi.advanceTimersByTimeAsync(16 * 60 * 1000)
    ahora += 16 * 60 * 1000
    expect((await entrar()).valor.verificada).toBe(true)
  })

  it('ignora entradas dañadas en localStorage', async () => {
    almacen.setItem(CLAVE_EQUIPOS, '{no es json')
    expect((await entrar()).valor.verificada).toBeUndefined()
    almacen.setItem(CLAVE_EQUIPOS, JSON.stringify([null, { correo: 5 }, { correo: 'acudiente@pradofc.co', expiraEn: 'ayer' }]))
    expect((await entrar()).valor.verificada).toBeUndefined()
  })
})

describe('dejarDeRecordarEquipo y cerrarSesionGlobal', () => {
  it('dejar de recordar quita solo la entrada de ese correo', async () => {
    await recordar('acudiente@pradofc.co')
    await recordar('tesorero@pradofc.co')
    auth.dejarDeRecordarEquipo('Acudiente@pradofc.co')
    expect(guardados().map((e) => e.correo)).toEqual(['tesorero@pradofc.co'])
    expect((await entrar('acudiente@pradofc.co')).valor.verificada).toBeUndefined()
  })

  it('cerrarSesionGlobal borra los equipos de ese correo y no los de otros', async () => {
    await recordar('acudiente@pradofc.co')
    await recordar('tesorero@pradofc.co')
    expect(auth.versionSesion('acudiente@pradofc.co')).toBe(0)
    const { valor } = await correr(auth.cerrarSesionGlobal('Acudiente@pradofc.co'))
    expect(valor).toEqual({ version: 1 })
    expect(guardados().map((e) => e.correo)).toEqual(['tesorero@pradofc.co'])
    expect(auth.versionSesion('acudiente@pradofc.co')).toBe(1)
    expect(auth.versionSesion('tesorero@pradofc.co')).toBe(0)
    // después de cerrar en todos, hay que verificar el código otra vez
    expect((await entrar('acudiente@pradofc.co')).valor.verificada).toBeUndefined()
    expect((await entrar('tesorero@pradofc.co')).valor.verificada).toBe(true)
  })

  it('cada cierre global sube la versión de sesión', async () => {
    await correr(auth.cerrarSesionGlobal('acudiente@pradofc.co'))
    expect((await correr(auth.cerrarSesionGlobal('acudiente@pradofc.co'))).valor.version).toBe(2)
  })

  it('cerrarSesionGlobal descarta un código pendiente de ese correo', async () => {
    await entrar()
    expect(auth.estadoCodigo()).not.toBe(null)
    await correr(auth.cerrarSesionGlobal('acudiente@pradofc.co'))
    expect(auth.estadoCodigo()).toBe(null)
  })
})

describe('localStorage con errores', () => {
  const roto = {
    getItem: () => { throw new Error('bloqueado') },
    setItem: () => { throw new Error('bloqueado') },
    removeItem: () => { throw new Error('bloqueado') },
  }

  it('el inicio de sesión y el código siguen funcionando, solo que sin recordar entre cargas', async () => {
    vi.stubGlobal('localStorage', roto)
    expect((await entrar()).valor.rol).toBe('acudiente')
    expect((await verificar(CODIGO, { recordar: true })).valor.rol).toBe('acudiente')
    // otra carga de la página: no se recuerda nada
    await cargar()
    const { valor } = await entrar()
    expect(valor.verificada).toBeUndefined()
    expect(auth.estadoCodigo()).not.toBe(null)
  })

  it('las demás funciones tampoco lanzan', async () => {
    vi.stubGlobal('localStorage', roto)
    expect(() => auth.dejarDeRecordarEquipo('acudiente@pradofc.co')).not.toThrow()
    expect(auth.estadoEquipo('acudiente@pradofc.co')).toBe(null)
    expect((await correr(auth.cerrarSesionGlobal('acudiente@pradofc.co'))).valor.version).toBe(1)
  })
})

describe('cerrarSesion (sesión de este equipo)', () => {
  it('deja el estado vacío, borra la sesión y NO toca el equipo de confianza ni el rol de desarrollo', async () => {
    const sesion = await import('../context/sesion.js')
    almacen.setItem('futdata.rol', 'administrador')
    await recordar()
    sesion.guardarSesion({ estado: 'verificada', usuario: { correo: 'acudiente@pradofc.co', rol: 'acudiente', nombre: 'María Torres' } })
    expect(sesion.leerSesion().estado).toBe('verificada')

    const resultado = sesion.limpiarSesion()
    expect(resultado).toBe(sesion.VACIA)
    expect(resultado).toEqual({ estado: 'sin-sesion', usuario: null })
    expect(sessionStorage.getItem(sesion.CLAVE_SESION)).toBe(null)
    expect(sesion.leerSesion()).toEqual({ estado: 'sin-sesion', usuario: null })
    expect(guardados()).toHaveLength(1)
    expect(almacen.getItem('futdata.rol')).toBe('administrador')
  })

  it('limpiarSesion no lanza si sessionStorage falla', async () => {
    const sesion = await import('../context/sesion.js')
    vi.stubGlobal('sessionStorage', { getItem: () => { throw new Error('x') }, setItem: () => { throw new Error('x') }, removeItem: () => { throw new Error('x') } })
    expect(sesion.limpiarSesion()).toEqual({ estado: 'sin-sesion', usuario: null })
    expect(sesion.leerSesion()).toEqual({ estado: 'sin-sesion', usuario: null })
  })
})
