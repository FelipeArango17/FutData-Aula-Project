import { beforeEach, describe, expect, it, vi } from 'vitest'

// El servicio guarda los datos en memoria a nivel de módulo: cada prueba carga una copia nueva.
let servicio
beforeEach(async () => {
  vi.resetModules()
  servicio = await import('./entrenadoresService.js')
})

const datos = (extra = {}) => ({
  nombre: 'Luis',
  apellido: 'Pérez',
  tipoDocumento: 'CC',
  documento: '1098765432',
  fechaNacimiento: '1990-05-14',
  telefono: '3001112233',
  correo: 'luis.perez@pradofc.co',
  especialidad: 'Porteros',
  categorias: ['Cachorros'],
  horarioTrabajo: '',
  direccion: 'Calle 40 Sur 75b-81',
  ...extra,
})

describe('listar y obtener', () => {
  it('listar devuelve los cinco entrenadores de ejemplo (sin Dairo), como copias', async () => {
    const lista = await servicio.listar()
    expect(lista.map((e) => `${e.nombre} ${e.apellido}`)).toEqual(['Nicolás Ejemplo', 'David Mesa', 'Jhon Cardona', 'Giovanny Ejemplo', 'Jhon Ramírez'])
    lista[0].categorias.push('Sub-16')
    lista.pop()
    const otra = await servicio.listar()
    expect(otra).toHaveLength(5)
    expect(otra[0].categorias).toEqual(['Sub-12'])
  })

  it('las categorías de ejemplo coinciden con las del servicio de entrenamientos', async () => {
    const lista = await servicio.listar()
    expect(lista.map((e) => e.categorias)).toEqual([['Sub-12'], ['Sub-11'], ['Sub-13', 'Sub-14'], ['Sub-15'], ['Sub-16']])
  })

  it('obtener acepta el id como número o como texto y devuelve null si no existe', async () => {
    expect((await servicio.obtener(2)).nombre).toBe('David')
    expect((await servicio.obtener('1')).nombre).toBe('Nicolás')
    expect(await servicio.obtener(99)).toBeNull()
  })
})

describe('detectarDuplicado', () => {
  it('mismo tipo y número de documento es duplicado', async () => {
    const r = await servicio.detectarDuplicado(datos({ documento: '1017234567' }))
    expect(r).toEqual({ campo: 'documento' })
  })

  it('el mismo número con otro tipo de documento NO es duplicado', async () => {
    const r = await servicio.detectarDuplicado(datos({ documento: '1017234567', tipoDocumento: 'Pasaporte' }))
    expect(r).toBeNull()
  })

  it('mismo correo es duplicado sin distinguir mayúsculas ni espacios en los bordes', async () => {
    expect(await servicio.detectarDuplicado(datos({ correo: 'DAVID.MESA@PRADOFC.CO' }))).toEqual({ campo: 'correo' })
    expect(await servicio.detectarDuplicado(datos({ correo: '  david.mesa@pradofc.co ' }))).toEqual({ campo: 'correo' })
  })

  it('datos distintos no son duplicado', async () => {
    expect(await servicio.detectarDuplicado(datos())).toBeNull()
  })

  it('si coinciden documento y correo, informa primero el documento', async () => {
    const r = await servicio.detectarDuplicado(datos({ documento: '1037001122', correo: 'nicolas.ejemplo@pradofc.co' }))
    expect(r).toEqual({ campo: 'documento' })
  })

  it('ignorarId evita el choque de un entrenador consigo mismo, pero no con otro', async () => {
    const andres = await servicio.obtener(1)
    expect(await servicio.detectarDuplicado(andres)).toEqual({ campo: 'documento' })
    expect(await servicio.detectarDuplicado(andres, 1)).toBeNull()
    expect(await servicio.detectarDuplicado(andres, '1')).toBeNull()
    expect(await servicio.detectarDuplicado(andres, 2)).toEqual({ campo: 'documento' })
  })
})

describe('crear', () => {
  it('asigna un id nuevo, limpia espacios y ordena las categorías como las oficiales', async () => {
    const creado = await servicio.crear(datos({
      nombre: '  Luis ',
      apellido: ' Pérez  ',
      telefono: ' 3001112233 ',
      correo: ' luis.perez@pradofc.co ',
      especialidad: ' Porteros ',
      categorias: ['Sub-16', 'Cachorros', 'Sub-12'],
      horarioTrabajo: undefined,
    }))
    expect(creado).toEqual(expect.objectContaining({
      nombre: 'Luis',
      apellido: 'Pérez',
      tipoDocumento: 'CC',
      documento: '1098765432',
      fechaNacimiento: '1990-05-14',
      telefono: '3001112233',
      correo: 'luis.perez@pradofc.co',
      especialidad: 'Porteros',
      categorias: ['Cachorros', 'Sub-12', 'Sub-16'],
      horarioTrabajo: '',
      direccion: 'Calle 40 Sur 75b-81',
    }))
    expect(creado.id).toMatch(/^[0-9a-f-]{36}$/)
    expect(await servicio.listar()).toHaveLength(6)
    expect(await servicio.obtener(6)).toEqual(creado)
  })

  it('descarta categorías que no existen', async () => {
    const creado = await servicio.crear(datos({ categorias: ['Semillero', 'Sub-99'] }))
    expect(creado.categorias).toEqual(['Semillero'])
  })

  it('los ids no se repiten', async () => {
    const a = await servicio.crear(datos())
    const b = await servicio.crear(datos({ documento: '1098765433', correo: 'otro@pradofc.co' }))
    expect(b.id).not.toBe(a.id)
  })

  it('rechaza un duplicado con error que indica el campo y no guarda', async () => {
    const error = await servicio.crear(datos({ correo: 'david.mesa@pradofc.co' })).catch((e) => e)
    expect(error).toBeInstanceOf(Error)
    expect(error.duplicado).toEqual({ campo: 'correo' })
    expect(await servicio.listar()).toHaveLength(5)
  })
})

describe('editar', () => {
  it('actualiza los datos conservando el id', async () => {
    const editado = await servicio.editar(1, datos({ nombre: 'Nicolás Felipe', categorias: ['Sub-12', 'Cachorros'] }))
    expect(editado.id).toMatch(/^[0-9a-f-]{36}$/)
    expect(editado.nombre).toBe('Nicolás Felipe')
    expect(editado.categorias).toEqual(['Cachorros', 'Sub-12'])
    expect(await servicio.listar()).toHaveLength(5)
    expect((await servicio.obtener(1)).nombre).toBe('Nicolás Felipe')
  })

  it('guardar sin cambiar nada no choca consigo mismo', async () => {
    const original = await servicio.obtener(2)
    const sinId = { ...original }
    delete sinId.id
    expect(await servicio.editar(2, sinId)).toEqual(original)
  })

  it('acepta el id como texto', async () => {
    const editado = await servicio.editar('1', datos({ categorias: ['Cachorros', 'Sub-12'] }))
    expect(editado.id).toMatch(/^[0-9a-f-]{36}$/)
  })

  it('rechaza usar el documento o el correo de otro entrenador', async () => {
    const porDocumento = await servicio.editar(1, datos({ documento: '1037001122' })).catch((e) => e)
    expect(porDocumento.duplicado).toEqual({ campo: 'documento' })
    const porCorreo = await servicio.editar(1, datos({ correo: 'David.Mesa@pradofc.co' })).catch((e) => e)
    expect(porCorreo.duplicado).toEqual({ campo: 'correo' })
    expect((await servicio.obtener(1)).documento).toBe('1017234567')
  })

  it('falla si el entrenador no existe', async () => {
    await expect(servicio.editar(99, datos())).rejects.toThrow('no existe')
  })
})
