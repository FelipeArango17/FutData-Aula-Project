import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { CATEGORIAS_POR_EDAD, categoriaPorFecha, edadPorAnio, idCategoriaAlGuardar, rangoAniosNacimiento } from './categorias.js'

// El reloj queda en una fecha fija: la categoría se recalcula cada año con el año actual
beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date(2026, 9, 22, 12, 0))
})
afterEach(() => vi.useRealTimers())

describe('CATEGORIAS_POR_EDAD', () => {
  it('la tabla cubre las edades 8 a 16 sin huecos ni cruces', () => {
    expect(CATEGORIAS_POR_EDAD.map((c) => c.nombre)).toEqual(['Sub-8', 'Sub-10', 'Sub-12', 'Sub-14', 'Sub-16'])
    const edades = CATEGORIAS_POR_EDAD.flatMap((c) => Array.from({ length: c.edadMaxima - c.edadMinima + 1 }, (_, i) => c.edadMinima + i))
    expect(edades).toEqual([8, 9, 10, 11, 12, 13, 14, 15, 16])
  })
})

describe('edadPorAnio', () => {
  it('es el año de hoy menos el año de nacimiento, sin mirar mes ni día', () => {
    expect(edadPorAnio('2018-12-31')).toBe(8)
    expect(edadPorAnio('2018-01-01')).toBe(8)
    expect(edadPorAnio('2018-12-31', '2027-01-01')).toBe(9)
  })
})

describe('categoriaPorFecha (reloj en 2026)', () => {
  it.each([
    ['2018-03-14', 'Sub-8'],
    ['2017-11-02', 'Sub-10'],
    ['2016-05-10', 'Sub-10'],
    ['2015-01-01', 'Sub-12'],
    ['2014-12-31', 'Sub-12'],
    ['2013-06-06', 'Sub-14'],
    ['2012-06-06', 'Sub-14'],
    ['2011-06-06', 'Sub-16'],
    ['2010-01-01', 'Sub-16'],
  ])('%s -> %s', (fecha, categoria) => {
    expect(categoriaPorFecha(fecha)).toBe(categoria)
  })

  it('fuera de 2010–2018 no hay categoría: 2019 y 2009', () => {
    expect(categoriaPorFecha('2019-01-01')).toBeNull()
    expect(categoriaPorFecha('2009-12-31')).toBeNull()
  })

  it('sin fecha completa no hay categoría', () => {
    for (const f of ['', undefined, null, '2018', '2018-03']) expect(categoriaPorFecha(f), String(f)).toBeNull()
  })

  it('con el reloj en 2027 la tabla se corre un año: 2019 -> Sub-8 y 2018 -> Sub-10', () => {
    vi.setSystemTime(new Date(2027, 0, 10, 12, 0))
    expect(categoriaPorFecha('2019-05-05')).toBe('Sub-8')
    expect(categoriaPorFecha('2018-05-05')).toBe('Sub-10')
    expect(categoriaPorFecha('2010-05-05')).toBeNull()
  })

  it('acepta un `hoy` explícito', () => {
    expect(categoriaPorFecha('2019-05-05', '2027-06-01')).toBe('Sub-8')
  })
})

describe('rangoAniosNacimiento', () => {
  it('en 2026 va de 2010 a 2018', () => {
    expect(rangoAniosNacimiento()).toEqual({ anioMinimo: 2010, anioMaximo: 2018 })
  })

  it('con el reloj en 2027 pasa a 2011–2019', () => {
    vi.setSystemTime(new Date(2027, 0, 10, 12, 0))
    expect(rangoAniosNacimiento()).toEqual({ anioMinimo: 2011, anioMaximo: 2019 })
  })
})

describe('idCategoriaAlGuardar (formulario del deportista)', () => {
  const categorias = [{ id: 1, nombre: 'Sub-8' }, { id: 2, nombre: 'Sub-10' }, { id: 3, nombre: 'Sub-12' }]

  it('deportista nuevo: sale de la categoría que da la fecha, como texto', () => {
    const vacio = { fechaNacimiento: '', idCategoria: '' }
    expect(idCategoriaAlGuardar('2016-05-10', vacio, categorias)).toBe('2')
    expect(idCategoriaAlGuardar('2018-01-01', vacio, categorias)).toBe('1')
  })

  it('sin categoría para esa fecha, o categoría que no está en la lista, queda sin asignar', () => {
    expect(idCategoriaAlGuardar('2019-01-01', { fechaNacimiento: '', idCategoria: '' }, categorias)).toBe('')
    expect(idCategoriaAlGuardar('2012-01-01', { fechaNacimiento: '', idCategoria: '' }, categorias)).toBe('') // Sub-14 no está en la lista
  })

  it('al editar con la misma fecha conserva la categoría que ya tenía, aunque no sea la de la fecha', () => {
    const inicial = { fechaNacimiento: '2017-10-25', idCategoria: '1' } // 2017 sería Sub-10, pero tenía Sub-8
    expect(idCategoriaAlGuardar('2017-10-25', inicial, categorias)).toBe('1')
    expect(idCategoriaAlGuardar('2017-10-25', { ...inicial, idCategoria: 1 }, categorias)).toBe('1')
  })

  it('al editar sin categoría y con la misma fecha sigue sin categoría', () => {
    expect(idCategoriaAlGuardar('2017-10-25', { fechaNacimiento: '2017-10-25', idCategoria: '' }, categorias)).toBe('')
    expect(idCategoriaAlGuardar('2017-10-25', { fechaNacimiento: '2017-10-25', idCategoria: null }, categorias)).toBe('')
  })

  it('al editar con otra fecha se recalcula, y cambia con el reloj', () => {
    const inicial = { fechaNacimiento: '2018-03-14', idCategoria: '1' }
    expect(idCategoriaAlGuardar('2015-03-14', inicial, categorias)).toBe('3')
    expect(idCategoriaAlGuardar('2018-03-14', { ...inicial, fechaNacimiento: '2017-01-01' }, categorias)).toBe('1')
    vi.setSystemTime(new Date(2027, 0, 10, 12, 0))
    expect(idCategoriaAlGuardar('2018-03-14', { fechaNacimiento: '', idCategoria: '' }, categorias)).toBe('2')
  })
})
