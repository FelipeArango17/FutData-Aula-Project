import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// El servicio guarda los datos en memoria a nivel de módulo: cada prueba carga una copia nueva.
let servicio
beforeEach(async () => {
  vi.useFakeTimers()
  vi.resetModules()
  servicio = await import('./deportistasService.js')
})
afterEach(() => vi.useRealTimers())

// Espera la respuesta del servicio avanzando el retardo simulado
const resolver = async (promesa) => {
  const resultado = promesa.then((v) => ({ v }), (e) => ({ e }))
  await vi.runAllTimersAsync()
  const { v, e } = await resultado
  if (e) throw e
  return v
}

describe('listarCategorias', () => {
  it('devuelve las categorías oficiales y una no vigente', async () => {
    const lista = await resolver(servicio.listarCategorias())
    expect(lista.filter((c) => c.activa).map((c) => c.nombre)).toEqual(['Sub-8', 'Sub-10', 'Sub-12', 'Sub-14', 'Sub-16'])
    expect(lista.find((c) => c.nombre === 'Sub-18').activa).toBe(false)
  })

  it('espera el retardo simulado antes de responder', async () => {
    let listo = false
    servicio.listarCategorias().then(() => { listo = true })
    await vi.advanceTimersByTimeAsync(100)
    expect(listo).toBe(false)
    await vi.advanceTimersByTimeAsync(200)
    expect(listo).toBe(true)
  })
})

describe('listarDeportistas', () => {
  it('sin filtros devuelve todos', async () => {
    expect(await resolver(servicio.listarDeportistas())).toHaveLength(21)
  })

  it('filtra por categoría (el id puede ser número o texto)', async () => {
    const a = await resolver(servicio.listarDeportistas({ idCategoria: 3 }))
    const b = await resolver(servicio.listarDeportistas({ idCategoria: '3' }))
    expect(a).toHaveLength(6)
    expect(b).toEqual(a)
    expect(a.every((x) => x.idCategoria === 3)).toBe(true)
  })

  it('una categoría sin deportistas devuelve lista vacía', async () => {
    expect(await resolver(servicio.listarDeportistas({ idCategoria: 5 }))).toEqual([])
  })

  it('busca por nombre sin distinguir mayúsculas ni tildes', async () => {
    const r = await resolver(servicio.listarDeportistas({ texto: 'TOMAS' }))
    expect(r.map((x) => x.nombre)).toEqual(['Tomás'])
    const r2 = await resolver(servicio.listarDeportistas({ texto: 'rios' }))
    expect(r2.map((x) => x.nombre)).toEqual(['Santiago', 'Valeria'])
  })

  it('busca por documento y combina con la categoría', async () => {
    expect(await resolver(servicio.listarDeportistas({ texto: '1100000012' }))).toHaveLength(1)
    expect(await resolver(servicio.listarDeportistas({ idCategoria: 1, texto: 'giraldo' }))).toEqual([])
  })

  it('un texto sin coincidencias devuelve lista vacía', async () => {
    expect(await resolver(servicio.listarDeportistas({ texto: 'zzzz' }))).toEqual([])
  })

  it('categoría inexistente lanza CATEGORIA_NO_EXISTE', async () => {
    await expect(resolver(servicio.listarDeportistas({ idCategoria: 99 }))).rejects.toMatchObject({ codigo: 'CATEGORIA_NO_EXISTE' })
  })

  it('devuelve copias: modificar el resultado no cambia los datos', async () => {
    const [primero] = await resolver(servicio.listarDeportistas({ idCategoria: 1 }))
    primero.nombre = 'X'
    const [otra] = await resolver(servicio.listarDeportistas({ idCategoria: 1 }))
    expect(otra.nombre).toBe('Santiago')
  })

  it('dos deportistas de demo tienen el acudiente de demostración', async () => {
    const todos = await resolver(servicio.listarDeportistas())
    expect(todos.filter((x) => x.acudiente === 'acudiente@pradofc.co')).toHaveLength(2)
  })

  it('un fallo simulado lanza ERROR_DE_RED una sola vez', async () => {
    servicio.configurarSimulacion({ fallos: 1 })
    await expect(resolver(servicio.listarDeportistas())).rejects.toMatchObject({ codigo: 'ERROR_DE_RED' })
    expect(await resolver(servicio.listarDeportistas())).toHaveLength(21)
  })
})

describe('obtenerDeportista', () => {
  it('acepta el id como número o texto', async () => {
    expect((await resolver(servicio.obtenerDeportista(2))).nombre).toBe('Valeria')
    expect((await resolver(servicio.obtenerDeportista('2'))).nombre).toBe('Valeria')
  })

  it('un deportista inexistente lanza DEPORTISTA_NO_EXISTE', async () => {
    await expect(resolver(servicio.obtenerDeportista(999))).rejects.toMatchObject({ codigo: 'DEPORTISTA_NO_EXISTE' })
  })
})

describe('asignarCategoria', () => {
  it('cambia la categoría y queda guardada (ids como texto también sirven)', async () => {
    const r = await resolver(servicio.asignarCategoria('1', '5'))
    expect(r.idCategoria).toBe(5)
    expect(await resolver(servicio.listarDeportistas({ idCategoria: 5 }))).toHaveLength(1)
    expect(await resolver(servicio.listarDeportistas({ idCategoria: 1 }))).toHaveLength(4)
  })

  it('una categoría inexistente lanza CATEGORIA_NO_EXISTE y no cambia nada', async () => {
    await expect(resolver(servicio.asignarCategoria(1, 99))).rejects.toMatchObject({ codigo: 'CATEGORIA_NO_EXISTE' })
    expect((await resolver(servicio.obtenerDeportista(1))).idCategoria).toBe(1)
  })

  it('una categoría no vigente lanza CATEGORIA_NO_VIGENTE', async () => {
    await expect(resolver(servicio.asignarCategoria(1, 6))).rejects.toMatchObject({ codigo: 'CATEGORIA_NO_VIGENTE' })
    expect((await resolver(servicio.obtenerDeportista(1))).idCategoria).toBe(1)
  })

  it('un deportista inexistente lanza DEPORTISTA_NO_EXISTE', async () => {
    await expect(resolver(servicio.asignarCategoria(999, 1))).rejects.toMatchObject({ codigo: 'DEPORTISTA_NO_EXISTE' })
  })
})

describe('calcularEdad', () => {
  it('cuenta años cumplidos a la fecha de referencia', () => {
    expect(servicio.calcularEdad('2018-10-22', '2026-10-22')).toBe(8)
    expect(servicio.calcularEdad('2018-10-23', '2026-10-22')).toBe(7)
    expect(servicio.calcularEdad('2018-03-14')).toBe(8)
  })
})

describe('datos de demo: teléfono y dirección', () => {
  it('los 21 deportistas tienen teléfono ficticio de 10 dígitos y dirección', async () => {
    const todos = await resolver(servicio.listarDeportistas())
    expect(todos).toHaveLength(21)
    expect(todos.every((x) => /^300000\d{4}$/.test(x.telefono) && x.direccion.length >= 5)).toBe(true)
    expect(todos[0].telefono).toBe('3000000001')
  })
})

describe('TIPOS_DOCUMENTO_DEPORTISTA', () => {
  it('incluye Registro civil y Tarjeta de identidad primero', () => {
    expect(servicio.TIPOS_DOCUMENTO_DEPORTISTA.slice(0, 2)).toEqual(['Registro civil', 'Tarjeta de identidad'])
  })
})

describe('crearDeportista', () => {
  const admin = { rol: 'administrador', correo: 'administrador@pradofc.co' }
  const datos = (extra = {}) => ({
    nombre: 'Lucas',
    apellido: 'Prueba Ejemplo',
    tipoDocumento: 'Registro civil',
    documento: '1200000001',
    fechaNacimiento: '2017-05-10',
    telefono: '3000000099',
    direccion: 'Calle 50 # 10-20, Barrio Ejemplo',
    idCategoria: '',
    ...extra,
  })

  it('creación correcta: id nuevo, activo, sin acudiente, quién y cuándo lo creó', async () => {
    const r = await resolver(servicio.crearDeportista(datos(), admin))
    expect(r).toMatchObject({
      id: 22, nombre: 'Lucas', apellido: 'Prueba Ejemplo', tipoDocumento: 'Registro civil', documento: '1200000001',
      fechaNacimiento: '2017-05-10', telefono: '3000000099', idCategoria: null, activo: true, acudiente: null, creadoPor: 'administrador@pradofc.co',
    })
    expect(typeof r.creadoEn).toBe('string')
    expect(Number.isNaN(Date.parse(r.creadoEn))).toBe(false)
    const guardado = await resolver(servicio.obtenerDeportista(22))
    expect(guardado.nombre).toBe('Lucas')
    expect(await resolver(servicio.listarDeportistas())).toHaveLength(22)
  })

  it('no guarda la edad, y calcularEdad la da desde la fecha de nacimiento y HOY_EJEMPLO (2026-10-22)', async () => {
    const r = await resolver(servicio.crearDeportista(datos({ fechaNacimiento: '2018-10-22' }), admin))
    expect('edad' in r).toBe(false)
    expect(servicio.calcularEdad(r.fechaNacimiento)).toBe(8)
    expect(servicio.calcularEdad('2018-10-23')).toBe(7)
  })

  it('recorta espacios de los textos y con categoría vigente la asigna', async () => {
    const r = await resolver(servicio.crearDeportista(datos({ nombre: '  Lucas ', direccion: '  Calle 50 # 10-20  ', idCategoria: '3' }), admin))
    expect(r.nombre).toBe('Lucas')
    expect(r.direccion).toBe('Calle 50 # 10-20')
    expect(r.idCategoria).toBe(3)
    expect((await resolver(servicio.listarDeportistas({ idCategoria: 3 }))).map((x) => x.id)).toContain(r.id)
  })

  it.each([
    ['nombre', { nombre: '' }, 'Escribe los nombres.'],
    ['apellido', { apellido: '   ' }, 'Escribe los apellidos.'],
    ['tipoDocumento', { tipoDocumento: '' }, 'Selecciona el tipo de documento.'],
    ['documento', { documento: '' }, 'Escribe el número de documento.'],
    ['fechaNacimiento', { fechaNacimiento: '' }, 'Indica la fecha de nacimiento.'],
    ['telefono', { telefono: '' }, 'Escribe el teléfono.'],
    ['direccion', { direccion: '' }, 'Escribe la dirección.'],
  ])('falta %s: VALIDACION con el mensaje de ese campo y no crea nada', async (campo, cambio, mensaje) => {
    const falla = resolver(servicio.crearDeportista(datos(cambio), admin))
    await expect(falla).rejects.toMatchObject({ codigo: 'VALIDACION', errores: { [campo]: mensaje } })
    expect(await resolver(servicio.listarDeportistas())).toHaveLength(21)
  })

  it('todo vacío: VALIDACION con los 7 campos obligatorios', async () => {
    const vacio = { nombre: '', apellido: '', tipoDocumento: '', documento: '', fechaNacimiento: '', telefono: '', direccion: '' }
    const e = await resolver(servicio.crearDeportista(vacio, admin)).catch((x) => x)
    expect(e.codigo).toBe('VALIDACION')
    expect(Object.keys(e.errores).sort()).toEqual(['apellido', 'direccion', 'documento', 'fechaNacimiento', 'nombre', 'telefono', 'tipoDocumento'])
  })

  it('reglas de formato: fecha futura (2026-10-23), documento corto, teléfono de 9 dígitos, nombre con números', async () => {
    const e = await resolver(servicio.crearDeportista(datos({ fechaNacimiento: '2026-10-23', documento: '12345', telefono: '300000009', nombre: 'Lu2' }), admin)).catch((x) => x)
    expect(e.errores).toEqual({
      nombre: 'Los nombres solo pueden tener letras, espacios, guion y apóstrofo.',
      documento: 'El documento debe tener entre 6 y 12 dígitos.',
      fechaNacimiento: 'La fecha de nacimiento no puede ser futura.',
      telefono: 'El teléfono debe tener exactamente 10 dígitos, sin espacios.',
    })
  })

  it('hoy (2026-10-22) es válido y 2026-10-23 no, siempre respecto a HOY_EJEMPLO', async () => {
    await expect(resolver(servicio.crearDeportista(datos({ fechaNacimiento: '2026-10-22' }), admin))).resolves.toBeTruthy()
    await expect(resolver(servicio.crearDeportista(datos({ fechaNacimiento: '2026-10-23', documento: '1200000002' }), admin))).rejects.toMatchObject({ codigo: 'VALIDACION' })
  })

  it('documento duplicado: DOCUMENTO_DUPLICADO sin revelar el nombre del otro deportista', async () => {
    const e = await resolver(servicio.crearDeportista(datos({ documento: '1100000007' }), admin)).catch((x) => x)
    expect(e.codigo).toBe('DOCUMENTO_DUPLICADO')
    expect(e.message).toBe('Ya existe un deportista registrado con este documento.')
    expect(e.message).not.toMatch(/Juan|Cano/)
    expect(await resolver(servicio.listarDeportistas())).toHaveLength(21)
  })

  it('el duplicado se compara solo por número: con otro tipo de documento también es duplicado', async () => {
    await expect(resolver(servicio.crearDeportista(datos({ documento: '1100000007', tipoDocumento: 'Pasaporte' }), admin))).rejects.toMatchObject({ codigo: 'DOCUMENTO_DUPLICADO' })
  })

  it('dos registros seguidos con el mismo número: el segundo es duplicado', async () => {
    await resolver(servicio.crearDeportista(datos(), admin))
    await expect(resolver(servicio.crearDeportista(datos(), admin))).rejects.toMatchObject({ codigo: 'DOCUMENTO_DUPLICADO' })
  })

  it('mismoDocumento ignora espacios y puntos en ambos lados', () => {
    expect(servicio.mismoDocumento('1.100.000.007', '1100000007')).toBe(true)
    expect(servicio.mismoDocumento(' 1100000007 ', '1 100 000 007')).toBe(true)
    expect(servicio.mismoDocumento('1100000007', '1100000008')).toBe(false)
    expect(servicio.mismoDocumento('', '')).toBe(false)
    expect(servicio.mismoDocumento(undefined, null)).toBe(false)
  })

  it('sin permiso: SIN_PERMISO para entrenador, tesorero, acudiente y sin actor', async () => {
    for (const actor of [{ rol: 'entrenador', correo: 'e@x.co' }, { rol: 'tesorero', correo: 't@x.co' }, { rol: 'acudiente', correo: 'a@x.co' }, undefined]) {
      await expect(resolver(servicio.crearDeportista(datos(), actor))).rejects.toMatchObject({ codigo: 'SIN_PERMISO' })
    }
    expect(await resolver(servicio.listarDeportistas())).toHaveLength(21)
  })

  it('categoría no vigente (Sub-18): CATEGORIA_NO_VIGENTE y no crea nada; inexistente: CATEGORIA_NO_EXISTE', async () => {
    await expect(resolver(servicio.crearDeportista(datos({ idCategoria: 6 }), admin))).rejects.toMatchObject({ codigo: 'CATEGORIA_NO_VIGENTE' })
    await expect(resolver(servicio.crearDeportista(datos({ idCategoria: 99 }), admin))).rejects.toMatchObject({ codigo: 'CATEGORIA_NO_EXISTE' })
    expect(await resolver(servicio.listarDeportistas())).toHaveLength(21)
  })

  it('un fallo de red lanza ERROR_DE_RED y no crea nada', async () => {
    servicio.configurarSimulacion({ fallos: 1 })
    await expect(resolver(servicio.crearDeportista(datos(), admin))).rejects.toMatchObject({ codigo: 'ERROR_DE_RED' })
    expect(await resolver(servicio.listarDeportistas())).toHaveLength(21)
  })
})

describe('listarDeportistas: filtro por estado y búsqueda (HU-010)', () => {
  it('sin el parámetro estado conserva el comportamiento de siempre: activos e inactivos', async () => {
    expect(await resolver(servicio.listarDeportistas())).toHaveLength(21)
    expect(await resolver(servicio.listarDeportistas({ estado: 'todos' }))).toHaveLength(21)
    expect(await resolver(servicio.listarDeportistas({ idCategoria: 1 }))).toHaveLength(5)
  })

  it('activos e inactivos: hay 3 inactivos para probar el filtro', async () => {
    const activos = await resolver(servicio.listarDeportistas({ estado: 'activos' }))
    const inactivos = await resolver(servicio.listarDeportistas({ estado: 'inactivos' }))
    expect(inactivos.map((x) => x.id)).toEqual([5, 10, 16])
    expect(activos).toHaveLength(18)
    expect(activos.every((x) => x.activo)).toBe(true)
  })

  it('el estado se combina con la búsqueda y con la categoría', async () => {
    expect((await resolver(servicio.listarDeportistas({ texto: 'osorio', estado: 'inactivos' }))).map((x) => x.nombre)).toEqual(['Luciana'])
    expect(await resolver(servicio.listarDeportistas({ texto: 'osorio', estado: 'activos' }))).toEqual([])
    expect((await resolver(servicio.listarDeportistas({ idCategoria: 3, estado: 'inactivos' }))).map((x) => x.id)).toEqual([16])
  })

  it('busca por nombre, apellido o documento sin distinguir tildes ni mayúsculas', async () => {
    expect((await resolver(servicio.listarDeportistas({ texto: 'SEBASTIAN' }))).map((x) => x.id)).toEqual([15])
    expect((await resolver(servicio.listarDeportistas({ texto: 'hernández' }))).map((x) => x.id)).toEqual([15])
    expect((await resolver(servicio.listarDeportistas({ texto: '1100000012' }))).map((x) => x.id)).toEqual([12])
    expect(await resolver(servicio.listarDeportistas({ texto: 'zzzz' }))).toEqual([])
  })
})

describe('obtenerFichaDeportista y obtenerHistorial', () => {
  const admin = { rol: 'administrador', correo: 'administrador@pradofc.co' }

  it('el administrador ve la ficha con teléfono y dirección (el id puede ser texto) y su historial', async () => {
    const { deportista, historial } = await resolver(servicio.obtenerFichaDeportista('3', admin))
    expect(deportista).toMatchObject({ id: 3, nombre: 'Emilio', telefono: '3000000003', creadoPor: 'administrador@pradofc.co' })
    expect(typeof deportista.creadoEn).toBe('string')
    expect(historial).toHaveLength(1)
    expect(historial[0].cambios).toEqual([
      { campo: 'telefono', antes: '3000000999', despues: '3000000003' },
      { campo: 'idCategoria', antes: 2, despues: 1 },
    ])
  })

  it('un deportista sin ediciones tiene el historial vacío', async () => {
    expect((await resolver(servicio.obtenerFichaDeportista(1, admin))).historial).toEqual([])
    expect(await resolver(servicio.obtenerHistorial(1, admin))).toEqual([])
  })

  it('id inexistente o inválido: DEPORTISTA_NO_EXISTE', async () => {
    await expect(resolver(servicio.obtenerFichaDeportista(999, admin))).rejects.toMatchObject({ codigo: 'DEPORTISTA_NO_EXISTE' })
    await expect(resolver(servicio.obtenerFichaDeportista('abc', admin))).rejects.toMatchObject({ codigo: 'DEPORTISTA_NO_EXISTE' })
    await expect(resolver(servicio.obtenerHistorial(999, admin))).rejects.toMatchObject({ codigo: 'DEPORTISTA_NO_EXISTE' })
  })

  it('sin permiso: SIN_PERMISO para los demás roles y sin actor', async () => {
    for (const actor of [{ rol: 'entrenador', correo: 'e@x.co' }, { rol: 'acudiente', correo: 'acudiente@pradofc.co' }, { rol: 'tesorero', correo: 't@x.co' }, undefined]) {
      await expect(resolver(servicio.obtenerFichaDeportista(1, actor))).rejects.toMatchObject({ codigo: 'SIN_PERMISO' })
      await expect(resolver(servicio.obtenerHistorial(1, actor))).rejects.toMatchObject({ codigo: 'SIN_PERMISO' })
    }
  })

  it('devuelve copias: modificar el resultado no cambia el historial guardado', async () => {
    const { historial } = await resolver(servicio.obtenerFichaDeportista(3, admin))
    historial[0].cambios.pop()
    historial.pop()
    expect((await resolver(servicio.obtenerHistorial(3, admin)))[0].cambios).toHaveLength(2)
  })
})

describe('actualizarDeportista', () => {
  const admin = { rol: 'administrador', correo: 'administrador@pradofc.co' }
  const otroAdmin = { rol: 'administrador', correo: 'otro.admin@pradofc.co' }
  const guardado = async (id) => (await resolver(servicio.obtenerFichaDeportista(id, admin)))

  it('edición con historial: guarda antes y después de cada campo cambiado y quién la hizo', async () => {
    const r = await resolver(servicio.actualizarDeportista(1, { telefono: '3000000555', direccion: 'Carrera 7 # 8-9, Barrio Ejemplo', idCategoria: '3' }, admin))
    expect(r.deportista).toMatchObject({ id: 1, telefono: '3000000555', idCategoria: 3, nombre: 'Santiago' })
    expect(r.historial).toHaveLength(1)
    expect(typeof r.historial[0].fechaHora).toBe('string')
    expect(Number.isNaN(Date.parse(r.historial[0].fechaHora))).toBe(false)
    expect(r.historial[0].responsable).toBe('administrador@pradofc.co')
    expect(r.historial[0].cambios).toEqual([
      { campo: 'telefono', antes: '3000000001', despues: '3000000555' },
      { campo: 'direccion', antes: 'Calle 11 # 21-11, Barrio Ejemplo', despues: 'Carrera 7 # 8-9, Barrio Ejemplo' },
      { campo: 'idCategoria', antes: 1, despues: 3 },
    ])
    expect((await guardado(1)).deportista.telefono).toBe('3000000555')
    // y ahora aparece en el grupo nuevo
    expect((await resolver(servicio.listarDeportistas({ idCategoria: 3 }))).map((x) => x.id)).toContain(1)
  })

  it('el historial se acumula y nunca se borra, también el sembrado', async () => {
    await resolver(servicio.actualizarDeportista(3, { telefono: '3000000111' }, admin))
    const r = await resolver(servicio.actualizarDeportista(3, { telefono: '3000000222' }, otroAdmin))
    expect(r.historial).toHaveLength(3)
    expect(r.historial[0].responsable).toBe('administrador@pradofc.co') // la entrada sembrada sigue primera
    expect(r.historial[1].cambios[0]).toEqual({ campo: 'telefono', antes: '3000000003', despues: '3000000111' })
    expect(r.historial[2]).toMatchObject({ responsable: 'otro.admin@pradofc.co', cambios: [{ campo: 'telefono', antes: '3000000111', despues: '3000000222' }] })
  })

  it('solo cambia los campos del formulario: id, acudiente, activo, creadoPor y creadoEn se ignoran', async () => {
    const antes = (await guardado(1)).deportista
    const r = await resolver(servicio.actualizarDeportista(1, { nombre: 'Santi', id: 99, acudiente: 'otro@x.co', activo: false, creadoPor: 'x', creadoEn: 'ayer' }, admin))
    expect(r.deportista).toMatchObject({ id: 1, nombre: 'Santi', acudiente: antes.acudiente, activo: true })
    expect(r.deportista.creadoPor).toBe(antes.creadoPor)
    expect(r.deportista.creadoEn).toBe(antes.creadoEn)
    expect(r.historial[0].cambios).toEqual([{ campo: 'nombre', antes: 'Santiago', despues: 'Santi' }])
  })

  it('sin cambios reales: SIN_CAMBIOS, sin historial nuevo (con espacios en los bordes también)', async () => {
    const actual = (await guardado(1)).deportista
    const mismos = { nombre: ` ${actual.nombre} `, apellido: actual.apellido, tipoDocumento: actual.tipoDocumento, documento: actual.documento, fechaNacimiento: actual.fechaNacimiento, telefono: actual.telefono, direccion: actual.direccion, idCategoria: String(actual.idCategoria) }
    await expect(resolver(servicio.actualizarDeportista(1, mismos, admin))).rejects.toMatchObject({ codigo: 'SIN_CAMBIOS' })
    await expect(resolver(servicio.actualizarDeportista(1, {}, admin))).rejects.toMatchObject({ codigo: 'SIN_CAMBIOS' })
    expect((await guardado(1)).historial).toEqual([])
  })

  it('"" y null en la categoría son lo mismo: quitar la categoría y volver a dejarla vacía no es un cambio', async () => {
    const r = await resolver(servicio.actualizarDeportista(1, { idCategoria: '' }, admin))
    expect(r.deportista.idCategoria).toBeNull()
    expect(r.historial[0].cambios).toEqual([{ campo: 'idCategoria', antes: 1, despues: null }])
    await expect(resolver(servicio.actualizarDeportista(1, { idCategoria: null }, admin))).rejects.toMatchObject({ codigo: 'SIN_CAMBIOS' })
    await expect(resolver(servicio.actualizarDeportista(1, { idCategoria: '' }, admin))).rejects.toMatchObject({ codigo: 'SIN_CAMBIOS' })
  })

  it('documento duplicado en OTRO deportista: DOCUMENTO_DUPLICADO y no guarda nada', async () => {
    const e = await resolver(servicio.actualizarDeportista(1, { documento: '1100000002', nombre: 'Cambiado' }, admin)).catch((x) => x)
    expect(e.codigo).toBe('DOCUMENTO_DUPLICADO')
    expect(e.message).toBe('Ya existe un deportista registrado con este documento.')
    expect(e.message).not.toMatch(/Valeria/)
    const { deportista, historial } = await guardado(1)
    expect(deportista.nombre).toBe('Santiago')
    expect(historial).toEqual([])
  })

  it('el documento propio no cuenta como duplicado y se puede cambiar por uno libre', async () => {
    await expect(resolver(servicio.actualizarDeportista(1, { documento: '1100000001', nombre: 'Santi' }, admin))).resolves.toBeTruthy()
    const r = await resolver(servicio.actualizarDeportista(1, { documento: '1100000099' }, admin))
    expect(r.deportista.documento).toBe('1100000099')
  })

  it('validación: nombre inválido, fecha futura (2026-10-23), documento corto y teléfono de 9 dígitos; no guarda nada', async () => {
    const e = await resolver(servicio.actualizarDeportista(1, { nombre: 'S4', fechaNacimiento: '2026-10-23', documento: '123', telefono: '300000000' }, admin)).catch((x) => x)
    expect(e.codigo).toBe('VALIDACION')
    expect(Object.keys(e.errores).sort()).toEqual(['documento', 'fechaNacimiento', 'nombre', 'telefono'])
    expect(e.errores.fechaNacimiento).toBe('La fecha de nacimiento no puede ser futura.')
    const { deportista, historial } = await guardado(1)
    expect(deportista).toMatchObject({ nombre: 'Santiago', documento: '1100000001' })
    expect(historial).toEqual([])
  })

  it('la fecha de nacimiento se valida contra HOY_EJEMPLO: hoy (2026-10-22) sí, mañana no', async () => {
    await expect(resolver(servicio.actualizarDeportista(1, { fechaNacimiento: '2026-10-22' }, admin))).resolves.toBeTruthy()
    await expect(resolver(servicio.actualizarDeportista(2, { fechaNacimiento: '2026-10-23' }, admin))).rejects.toMatchObject({ codigo: 'VALIDACION' })
    expect(servicio.calcularEdad('2026-10-22')).toBe(0)
  })

  it('categoría no vigente (Sub-18) o inexistente: no guarda nada', async () => {
    await expect(resolver(servicio.actualizarDeportista(1, { idCategoria: 6 }, admin))).rejects.toMatchObject({ codigo: 'CATEGORIA_NO_VIGENTE' })
    await expect(resolver(servicio.actualizarDeportista(1, { idCategoria: 99 }, admin))).rejects.toMatchObject({ codigo: 'CATEGORIA_NO_EXISTE' })
    const { deportista, historial } = await guardado(1)
    expect(deportista.idCategoria).toBe(1)
    expect(historial).toEqual([])
  })

  it('deportista inexistente: DEPORTISTA_NO_EXISTE', async () => {
    await expect(resolver(servicio.actualizarDeportista(999, { nombre: 'X' }, admin))).rejects.toMatchObject({ codigo: 'DEPORTISTA_NO_EXISTE' })
  })

  it('sin permiso: SIN_PERMISO (antes que la existencia y la validación) y no cambia nada', async () => {
    for (const actor of [{ rol: 'entrenador', correo: 'e@x.co' }, { rol: 'acudiente', correo: 'acudiente@pradofc.co' }, undefined]) {
      await expect(resolver(servicio.actualizarDeportista(1, { nombre: 'Intruso' }, actor))).rejects.toMatchObject({ codigo: 'SIN_PERMISO' })
      await expect(resolver(servicio.actualizarDeportista(999, { nombre: '' }, actor))).rejects.toMatchObject({ codigo: 'SIN_PERMISO' })
    }
    expect((await guardado(1)).deportista.nombre).toBe('Santiago')
  })

  it('un deportista recién creado también se puede editar y arranca con historial vacío', async () => {
    const nuevo = await resolver(servicio.crearDeportista({ nombre: 'Lucas', apellido: 'Prueba Ejemplo', tipoDocumento: 'Registro civil', documento: '1200000001', fechaNacimiento: '2017-05-10', telefono: '3000000099', direccion: 'Calle 50 # 10-20', idCategoria: '' }, admin))
    expect((await guardado(nuevo.id)).historial).toEqual([])
    const r = await resolver(servicio.actualizarDeportista(nuevo.id, { idCategoria: 5 }, admin))
    expect(r.historial[0].cambios).toEqual([{ campo: 'idCategoria', antes: null, despues: 5 }])
  })
})
