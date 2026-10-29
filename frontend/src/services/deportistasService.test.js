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
