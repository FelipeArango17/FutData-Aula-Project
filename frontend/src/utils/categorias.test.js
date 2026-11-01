import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  CATEGORIAS_POR_EDAD, NOMBRES_CATEGORIAS, NOTA_CATEGORIA_POR_ANIO, textoAniosCategoria, categoriaPorFecha, edadPorAnio, idCategoriaAlGuardar, rangoAniosNacimiento,
} from './categorias.js'

// El reloj queda en una fecha fija: la categoría se recalcula cada año con el año actual
beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date(2026, 9, 22, 12, 0))
})
afterEach(() => vi.useRealTimers())

describe('CATEGORIAS_POR_EDAD', () => {
  it('son las 9 categorías del club, en orden, sin las eliminadas', () => {
    expect(NOMBRES_CATEGORIAS).toEqual(['Párvulos', 'Cachorros', 'Semillero', 'Sub-11', 'Sub-12', 'Sub-13', 'Sub-14', 'Sub-15', 'Sub-16'])
    for (const eliminada of ['Sub-8', 'Sub-9', 'Sub-10', 'Sub 13 y 14 Formación', 'Femenino', 'Arqueros']) expect(NOMBRES_CATEGORIAS).not.toContain(eliminada)
  })

  it('cubren las edades 4 a 16 sin huecos ni cruces', () => {
    const edades = CATEGORIAS_POR_EDAD.flatMap((c) => Array.from({ length: c.edadMaxima - c.edadMinima + 1 }, (_, i) => c.edadMinima + i))
    expect(edades).toEqual([4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16])
    expect(CATEGORIAS_POR_EDAD[0]).toMatchObject({ nombre: 'Párvulos', edadMinima: 4, edadMaxima: 6 })
    expect(CATEGORIAS_POR_EDAD[1]).toMatchObject({ nombre: 'Cachorros', edadMinima: 7, edadMaxima: 8 })
    expect(CATEGORIAS_POR_EDAD[2]).toMatchObject({ nombre: 'Semillero', edadMinima: 9, edadMaxima: 10 })
  })
})

describe('edadPorAnio', () => {
  it('es el año de hoy menos el año de nacimiento, sin mirar mes ni día', () => {
    expect(edadPorAnio('2018-12-31')).toBe(8)
    expect(edadPorAnio('2018-01-01')).toBe(8)
    expect(edadPorAnio('2018-12-31', '2027-01-15')).toBe(9)
  })
})

describe('categoriaPorFecha (reloj en 2026)', () => {
  it.each([
    ['2022-12-31', 'Párvulos'], // límite más joven
    ['2021-06-15', 'Párvulos'],
    ['2020-01-01', 'Párvulos'], // límite de arriba de Párvulos
    ['2019-12-31', 'Cachorros'],
    ['2018-01-01', 'Cachorros'], // límite de abajo de Cachorros
    ['2017-12-31', 'Semillero'], // límite más joven de Semillero
    ['2017-05-05', 'Semillero'],
    ['2016-01-01', 'Semillero'], // límite más viejo de Semillero
    ['2015-12-31', 'Sub-11'],
    ['2015-01-01', 'Sub-11'],
    ['2014-12-31', 'Sub-12'],
    ['2013-06-06', 'Sub-13'],
    ['2012-06-06', 'Sub-14'],
    ['2011-06-06', 'Sub-15'],
    ['2010-01-01', 'Sub-16'], // límite más viejo
  ])('%s -> %s', (fecha, categoria) => {
    expect(categoriaPorFecha(fecha)).toBe(categoria)
  })

  it('fuera de 2010–2022 no hay categoría: 2009 y 2023', () => {
    expect(categoriaPorFecha('2009-12-31')).toBeNull()
    expect(categoriaPorFecha('2023-01-01')).toBeNull()
    expect(categoriaPorFecha('2026-10-22')).toBeNull()
  })

  it('el 29 de febrero cuenta por el año: 2020-02-29 es Párvulos y 2016-02-29 es Semillero', () => {
    expect(categoriaPorFecha('2020-02-29')).toBe('Párvulos')
    expect(categoriaPorFecha('2016-02-29')).toBe('Semillero')
    expect(categoriaPorFecha('2012-02-29')).toBe('Sub-14')
  })

  it('sin fecha completa no hay categoría', () => {
    for (const f of ['', undefined, null, '2018', '2018-03']) expect(categoriaPorFecha(f), String(f)).toBeNull()
  })

  it('cambio de año: con el reloj en 2027-01-15 todo se corre un año', () => {
    vi.setSystemTime(new Date(2027, 0, 15, 12, 0))
    expect(categoriaPorFecha('2023-01-01')).toBe('Párvulos')
    expect(categoriaPorFecha('2020-01-01')).toBe('Cachorros')
    expect(categoriaPorFecha('2018-05-05')).toBe('Semillero')
    expect(categoriaPorFecha('2016-05-05')).toBe('Sub-11')
    expect(categoriaPorFecha('2011-05-05')).toBe('Sub-16')
    expect(categoriaPorFecha('2010-05-05')).toBeNull()
    expect(categoriaPorFecha('2024-01-01')).toBeNull()
  })

  it('acepta un `hoy` explícito', () => {
    expect(categoriaPorFecha('2019-05-05', '2027-06-01')).toBe('Cachorros')
    expect(categoriaPorFecha('2019-05-05', '2026-06-01')).toBe('Cachorros')
    expect(categoriaPorFecha('2019-05-05', '2028-06-01')).toBe('Semillero')
  })
})

describe('rangoAniosNacimiento', () => {
  it('en 2026 va de 2010 a 2022', () => {
    expect(rangoAniosNacimiento()).toEqual({ anioMinimo: 2010, anioMaximo: 2022 })
  })

  it('con el reloj en 2027 pasa a 2011–2023', () => {
    vi.setSystemTime(new Date(2027, 0, 15, 12, 0))
    expect(rangoAniosNacimiento()).toEqual({ anioMinimo: 2011, anioMaximo: 2023 })
  })

  it('acepta un `hoy` explícito', () => {
    expect(rangoAniosNacimiento('2030-03-01')).toEqual({ anioMinimo: 2014, anioMaximo: 2026 })
  })

  it('los extremos del rango son exactamente las fechas que sí tienen categoría', () => {
    const { anioMinimo, anioMaximo } = rangoAniosNacimiento()
    expect(categoriaPorFecha(`${anioMinimo}-06-01`)).not.toBeNull()
    expect(categoriaPorFecha(`${anioMaximo}-06-01`)).not.toBeNull()
    expect(categoriaPorFecha(`${anioMinimo - 1}-06-01`)).toBeNull()
    expect(categoriaPorFecha(`${anioMaximo + 1}-06-01`)).toBeNull()
  })
})

describe('idCategoriaAlGuardar (formulario del deportista)', () => {
  const categorias = [{ id: 1, nombre: 'Párvulos' }, { id: 2, nombre: 'Cachorros' }, { id: 3, nombre: 'Semillero' }]
  const vacio = { fechaNacimiento: '', idCategoria: '' }

  it('deportista nuevo: sale de la categoría que da la fecha, como texto', () => {
    expect(idCategoriaAlGuardar('2016-05-10', vacio, categorias)).toBe('3')
    expect(idCategoriaAlGuardar('2018-01-01', vacio, categorias)).toBe('2')
    expect(idCategoriaAlGuardar('2022-01-01', vacio, categorias)).toBe('1')
  })

  it('sin categoría para esa fecha, o categoría que no está en la lista, queda sin asignar', () => {
    expect(idCategoriaAlGuardar('2023-01-01', vacio, categorias)).toBe('')
    expect(idCategoriaAlGuardar('2012-01-01', vacio, categorias)).toBe('') // Sub-14 no está en la lista de ejemplo
  })

  it('al editar con la misma fecha conserva la categoría que ya tenía, aunque no sea la de la fecha', () => {
    const inicial = { fechaNacimiento: '2017-10-25', idCategoria: '2' } // 2017 sería Semillero, pero tenía Cachorros
    expect(idCategoriaAlGuardar('2017-10-25', inicial, categorias)).toBe('2')
    expect(idCategoriaAlGuardar('2017-10-25', { ...inicial, idCategoria: 2 }, categorias)).toBe('2')
  })

  it('al editar sin categoría y con la misma fecha sigue sin categoría', () => {
    expect(idCategoriaAlGuardar('2017-10-25', { fechaNacimiento: '2017-10-25', idCategoria: '' }, categorias)).toBe('')
    expect(idCategoriaAlGuardar('2017-10-25', { fechaNacimiento: '2017-10-25', idCategoria: null }, categorias)).toBe('')
  })

  it('al editar con otra fecha se recalcula, y cambia con el reloj', () => {
    const inicial = { fechaNacimiento: '2018-03-14', idCategoria: '2' }
    expect(idCategoriaAlGuardar('2016-03-14', inicial, categorias)).toBe('3')
    expect(idCategoriaAlGuardar('2018-03-14', { ...inicial, fechaNacimiento: '2017-01-01' }, categorias)).toBe('2')
    vi.setSystemTime(new Date(2027, 0, 15, 12, 0))
    expect(idCategoriaAlGuardar('2020-03-14', vacio, categorias)).toBe('2')
  })
})

describe('textoAniosCategoria (ficha del deportista)', () => {
  it('una categoría de un año: "Sub-11 (nacidos en 2015)"', () => {
    expect(textoAniosCategoria('Sub-11')).toBe('nacidos en 2015')
    expect(textoAniosCategoria('Sub-12')).toBe('nacidos en 2014')
    expect(textoAniosCategoria('Sub-16')).toBe('nacidos en 2010')
  })

  it('Cachorros y Semillero reúnen dos años y Párvulos tres', () => {
    expect(textoAniosCategoria('Semillero')).toBe('nacidos en 2016 y 2017')
    expect(textoAniosCategoria('Cachorros')).toBe('nacidos en 2018 y 2019')
    expect(textoAniosCategoria('Párvulos')).toBe('nacidos de 2020 a 2022')
  })

  it('cambia con el año y devuelve null si la categoría no existe', () => {
    expect(textoAniosCategoria('Sub-11', '2027-01-15')).toBe('nacidos en 2016')
    expect(textoAniosCategoria('Semillero', '2027-01-15')).toBe('nacidos en 2017 y 2018')
    for (const noExiste of ['Sub-18', 'Sub-8', 'Sub-9', 'Sub-10']) expect(textoAniosCategoria(noExiste), noExiste).toBeNull()
  })

  it('la nota de las listas explica que la categoría sale del año', () => {
    expect(NOTA_CATEGORIA_POR_ANIO).toBe('La categoría se define por año de nacimiento, no por edad cumplida.')
  })
})
