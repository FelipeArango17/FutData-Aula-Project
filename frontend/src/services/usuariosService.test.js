import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// El servicio guarda los datos en memoria a nivel de módulo: cada prueba carga una copia nueva.
let servicio
beforeEach(async () => {
  vi.useFakeTimers()
  vi.resetModules()
  servicio = await import('./usuariosService.js')
})
afterEach(() => vi.useRealTimers())

const ADMIN = { rol: 'administrador', correo: 'administrador@pradofc.co' }
// Otro administrador (no está en la lista): puede actuar sobre Carlos Mendoza
const OTRO_ADMIN = { rol: 'administrador', correo: 'otro.admin@pradofc.co' }
const VALIDO = { nombre: 'Julián', apellido: 'Ocampo Ruiz', correo: 'julian.ocampo@pradofc.co', rol: 'tesorero' }

// Espera la respuesta del servicio avanzando el retardo simulado
const resolver = async (promesa) => {
  const resultado = promesa.then((v) => ({ v }), (e) => ({ e }))
  await vi.runAllTimersAsync()
  const { v, e } = await resultado
  if (e) throw e
  return v
}
const codigoDe = async (promesa) => {
  try {
    await resolver(promesa)
  } catch (e) {
    return e.codigo
  }
  return null
}
const por = (lista, correo) => lista.find((x) => x.correo === correo)

describe('datos de ejemplo', () => {
  it('trae 8 usuarios con las 4 cuentas demo y al menos uno revocado', async () => {
    const lista = await resolver(servicio.listarUsuarios({}, ADMIN))
    expect(lista).toHaveLength(8)
    for (const rol of servicio.ROLES) expect(por(lista, `${rol}@pradofc.co`).rol).toBe(rol)
    expect(lista.some((x) => !x.activo)).toBe(true)
    expect(lista.every((x) => Object.keys(x).sort().join() === 'activo,apellido,correo,creadoEn,creadoPor,id,nombre,rol')).toBe(true)
  })

  it('espera el retardo simulado antes de responder', async () => {
    let listo = false
    servicio.listarUsuarios({}, ADMIN).then(() => { listo = true })
    await vi.advanceTimersByTimeAsync(100)
    expect(listo).toBe(false)
    await vi.advanceTimersByTimeAsync(200)
    expect(listo).toBe(true)
  })

  it('simula un fallo de red con ERROR_DE_RED', async () => {
    servicio.configurarSimulacion({ fallos: 1 })
    expect(await codigoDe(servicio.listarUsuarios({}, ADMIN))).toBe('ERROR_DE_RED')
    expect(await resolver(servicio.listarUsuarios({}, ADMIN))).toHaveLength(8)
  })
})

describe('listarUsuarios: búsqueda y filtros', () => {
  const listar = (f) => resolver(servicio.listarUsuarios(f, ADMIN))

  it('busca por nombre, apellido o correo, sin mayúsculas ni tildes', async () => {
    expect((await listar({ texto: 'GOMEZ' })).map((x) => x.correo)).toEqual(['tesorero@pradofc.co'])
    expect((await listar({ texto: 'sofia.henao' })).map((x) => x.correo)).toEqual(['sofia.henao@pradofc.co'])
    expect(await listar({ texto: 'carlos' })).toHaveLength(2)
    expect(await listar({ texto: 'nadie' })).toEqual([])
  })

  it('filtra por rol y por estado, y los combina', async () => {
    expect((await listar({ rol: 'administrador' })).map((x) => x.id)).toEqual([1, 8])
    expect((await listar({ rol: 'todos' }))).toHaveLength(8)
    expect((await listar({ estado: 'revocados' })).map((x) => x.id)).toEqual([6])
    expect(await listar({ estado: 'activos' })).toHaveLength(7)
    expect(await listar({ rol: 'acudiente', estado: 'activos' })).toHaveLength(2)
    expect(await listar({ rol: 'acudiente', estado: 'revocados', texto: 'pedro' })).toHaveLength(1)
  })

  it('devuelve copias: cambiar el resultado no cambia los datos', async () => {
    const [primero] = await listar()
    primero.rol = 'acudiente'
    expect((await listar())[0].rol).toBe('administrador')
  })
})

describe('obtenerUsuario', () => {
  it('devuelve el usuario con su historial (acepta el id como texto)', async () => {
    const { usuario, historial } = await resolver(servicio.obtenerUsuario('6', ADMIN))
    expect(usuario.correo).toBe('inactivo@pradofc.co')
    expect(historial).toHaveLength(1)
  })

  it('un id inexistente lanza USUARIO_NO_EXISTE', async () => {
    expect(await codigoDe(servicio.obtenerUsuario(999, ADMIN))).toBe('USUARIO_NO_EXISTE')
    expect(await codigoDe(servicio.obtenerUsuario('entrenadores', ADMIN))).toBe('USUARIO_NO_EXISTE')
  })
})

describe('crearUsuario', () => {
  it('crea un usuario activo con quién y cuándo lo creó, y queda en la lista', async () => {
    vi.setSystemTime(new Date('2026-10-29T12:00:00Z'))
    const { usuario, historial } = await resolver(servicio.crearUsuario({ ...VALIDO, nombre: '  Julián ' }, ADMIN))
    expect(usuario).toMatchObject({ id: 9, nombre: 'Julián', apellido: 'Ocampo Ruiz', rol: 'tesorero', activo: true, creadoPor: ADMIN.correo, creadoEn: '2026-10-29T12:00:00.250Z' })
    expect(historial).toEqual([])
    expect(await resolver(servicio.listarUsuarios({}, ADMIN))).toHaveLength(9)
  })

  it.each([
    ['nombre', 'nombre'],
    ['apellido', 'apellido'],
    ['correo', 'correo'],
    ['rol', 'rol'],
  ])('si falta %s lanza VALIDACION con el error de ese campo y no crea nada', async (campo) => {
    const vacio = { ...VALIDO, [campo]: '' }
    let falla
    try {
      await resolver(servicio.crearUsuario(vacio, ADMIN))
    } catch (e) {
      falla = e
    }
    expect(falla.codigo).toBe('VALIDACION')
    expect(Object.keys(falla.errores)).toEqual([campo])
    expect(await resolver(servicio.listarUsuarios({}, ADMIN))).toHaveLength(8)
  })

  it('reporta todos los campos faltantes a la vez', async () => {
    const falla = await resolver(servicio.crearUsuario({}, ADMIN)).catch((e) => e)
    expect(Object.keys(falla.errores).sort()).toEqual(['apellido', 'correo', 'nombre', 'rol'])
  })

  it('rechaza un correo mal escrito y un nombre con números', async () => {
    const falla = await resolver(servicio.crearUsuario({ ...VALIDO, correo: 'sin-arroba', nombre: 'J0hn' }, ADMIN)).catch((e) => e)
    expect(Object.keys(falla.errores).sort()).toEqual(['correo', 'nombre'])
  })

  it('un rol inválido lanza VALIDACION', async () => {
    const falla = await resolver(servicio.crearUsuario({ ...VALIDO, rol: 'superusuario' }, ADMIN)).catch((e) => e)
    expect(falla.codigo).toBe('VALIDACION')
    expect(falla.errores.rol).toBe('Selecciona el rol.')
  })

  it('un correo repetido lanza CORREO_DUPLICADO sin importar mayúsculas ni espacios', async () => {
    expect(await codigoDe(servicio.crearUsuario({ ...VALIDO, correo: 'Tesorero@PRADOFC.co' }, ADMIN))).toBe('CORREO_DUPLICADO')
    expect(await codigoDe(servicio.crearUsuario({ ...VALIDO, correo: ' tesorero@pradofc.co ' }, ADMIN))).toBe('CORREO_DUPLICADO')
    expect(await resolver(servicio.listarUsuarios({}, ADMIN))).toHaveLength(8)
  })

  it('un usuario creado no tiene contraseña ni otros datos', async () => {
    const { usuario } = await resolver(servicio.crearUsuario(VALIDO, ADMIN))
    expect(Object.keys(usuario).sort()).toEqual(['activo', 'apellido', 'correo', 'creadoEn', 'creadoPor', 'id', 'nombre', 'rol'])
  })
})

describe('cambiarRol', () => {
  it('cambia el rol y lo deja en el historial con nombres legibles, el más reciente primero', async () => {
    vi.setSystemTime(new Date('2026-10-29T12:00:00Z'))
    const primero = await resolver(servicio.cambiarRol(4, 'tesorero', ADMIN))
    expect(primero.usuario.rol).toBe('tesorero')
    expect(primero.historial[0]).toEqual({
      fechaHora: '2026-10-29T12:00:00.250Z', // el retardo simulado (250 ms) corre con el reloj falso
      responsable: ADMIN.correo,
      cambios: [{ campo: 'rol', antes: 'Acudiente', despues: 'Tesorero' }],
    })
    vi.setSystemTime(new Date('2026-10-29T13:00:00Z'))
    const segundo = await resolver(servicio.cambiarRol(4, 'entrenador', ADMIN))
    expect(segundo.historial.map((h) => h.cambios[0].despues)).toEqual(['Entrenador', 'Tesorero'])
    expect(await resolver(servicio.obtenerHistorial(4, ADMIN))).toEqual(segundo.historial)
  })

  it('el mismo rol lanza SIN_CAMBIOS y no agrega historial', async () => {
    expect(await codigoDe(servicio.cambiarRol(4, 'acudiente', ADMIN))).toBe('SIN_CAMBIOS')
    expect(await resolver(servicio.obtenerHistorial(4, ADMIN))).toEqual([])
  })

  it('un rol inválido lanza VALIDACION y un usuario inexistente USUARIO_NO_EXISTE', async () => {
    expect(await codigoDe(servicio.cambiarRol(4, 'padre', ADMIN))).toBe('VALIDACION')
    expect(await codigoDe(servicio.cambiarRol(99, 'tesorero', ADMIN))).toBe('USUARIO_NO_EXISTE')
  })

  it('un administrador no puede quitarse su propio rol (AUTOACCESO), aunque el correo cambie de mayúsculas', async () => {
    expect(await codigoDe(servicio.cambiarRol(1, 'tesorero', ADMIN))).toBe('AUTOACCESO')
    expect(await codigoDe(servicio.cambiarRol(1, 'tesorero', { ...ADMIN, correo: 'ADMINISTRADOR@pradofc.co' }))).toBe('AUTOACCESO')
    expect((await resolver(servicio.obtenerUsuario(1, ADMIN))).usuario.rol).toBe('administrador')
  })

  it('no deja el sistema sin administradores activos (ULTIMO_ADMINISTRADOR)', async () => {
    await resolver(servicio.cambiarRol(8, 'tesorero', ADMIN))
    expect(await codigoDe(servicio.cambiarRol(1, 'tesorero', OTRO_ADMIN))).toBe('ULTIMO_ADMINISTRADOR')
  })

  it('con otro administrador activo se puede quitar el rol de administrador a alguien más', async () => {
    const { usuario } = await resolver(servicio.cambiarRol(8, 'entrenador', ADMIN))
    expect(usuario.rol).toBe('entrenador')
  })

  it('nombrar a otro administrador sí es posible (acudiente -> administrador)', async () => {
    expect((await resolver(servicio.cambiarRol(4, 'administrador', ADMIN))).usuario.rol).toBe('administrador')
  })
})

describe('revocarAcceso y restablecerAcceso', () => {
  it('revocar pone activo=false, no borra al usuario y queda en el historial', async () => {
    const { usuario, historial } = await resolver(servicio.revocarAcceso(4, ADMIN))
    expect(usuario.activo).toBe(false)
    expect(historial[0].cambios).toEqual([{ campo: 'activo', antes: 'Activo', despues: 'Acceso revocado' }])
    expect(await resolver(servicio.listarUsuarios({}, ADMIN))).toHaveLength(8)
    expect(por(await resolver(servicio.listarUsuarios({ estado: 'revocados' }, ADMIN)), 'acudiente@pradofc.co')).toBeTruthy()
  })

  it('restablecer lo vuelve a activar y suma otra entrada al historial', async () => {
    await resolver(servicio.revocarAcceso(4, ADMIN))
    const { usuario, historial } = await resolver(servicio.restablecerAcceso(4, ADMIN))
    expect(usuario.activo).toBe(true)
    expect(historial.map((h) => h.cambios[0].despues)).toEqual(['Activo', 'Acceso revocado'])
  })

  it('revocar a un revocado o restablecer a un activo lanza SIN_CAMBIOS', async () => {
    expect(await codigoDe(servicio.revocarAcceso(6, ADMIN))).toBe('SIN_CAMBIOS')
    expect(await codigoDe(servicio.restablecerAcceso(4, ADMIN))).toBe('SIN_CAMBIOS')
  })

  it('un administrador no puede revocarse a sí mismo (AUTOACCESO)', async () => {
    expect(await codigoDe(servicio.revocarAcceso(1, ADMIN))).toBe('AUTOACCESO')
    expect((await resolver(servicio.obtenerUsuario(1, ADMIN))).usuario.activo).toBe(true)
  })

  it('no se puede revocar al último administrador activo (ULTIMO_ADMINISTRADOR)', async () => {
    await resolver(servicio.revocarAcceso(8, ADMIN))
    expect(await codigoDe(servicio.revocarAcceso(1, OTRO_ADMIN))).toBe('ULTIMO_ADMINISTRADOR')
  })

  it('un administrador revocado ya no cuenta como activo, pero restablecerlo no tiene restricción', async () => {
    await resolver(servicio.revocarAcceso(8, ADMIN))
    expect((await resolver(servicio.restablecerAcceso(8, ADMIN))).usuario.activo).toBe(true)
  })

  it('el usuario revocado de ejemplo ya tiene su historial', async () => {
    const historial = await resolver(servicio.obtenerHistorial(6, ADMIN))
    expect(historial[0].cambios[0].despues).toBe('Acceso revocado')
  })
})

describe('SIN_PERMISO', () => {
  it.each([
    ['tesorero', { rol: 'tesorero', correo: 'tesorero@pradofc.co' }],
    ['entrenador', { rol: 'entrenador', correo: 'entrenador@pradofc.co' }],
    ['acudiente', { rol: 'acudiente', correo: 'acudiente@pradofc.co' }],
    ['sin actor', undefined],
  ])('%s no puede usar ninguna función', async (_nombre, actor) => {
    const llamadas = [
      () => servicio.listarUsuarios({}, actor),
      () => servicio.obtenerUsuario(4, actor),
      () => servicio.obtenerHistorial(4, actor),
      () => servicio.crearUsuario(VALIDO, actor),
      () => servicio.cambiarRol(4, 'tesorero', actor),
      () => servicio.revocarAcceso(4, actor),
      () => servicio.restablecerAcceso(6, actor),
    ]
    for (const llamada of llamadas) expect(await codigoDe(llamada())).toBe('SIN_PERMISO')
    expect(await resolver(servicio.listarUsuarios({}, ADMIN))).toHaveLength(8)
  })
})

describe('acceso vigente (lo usa authService)', () => {
  it('es false para revocados, true para activos y null para correos desconocidos', async () => {
    expect(servicio.accesoVigente('INACTIVO@pradofc.co')).toBe(false)
    expect(servicio.accesoVigente('acudiente@pradofc.co')).toBe(true)
    expect(servicio.accesoVigente('nadie@pradofc.co')).toBe(null)
  })
})
