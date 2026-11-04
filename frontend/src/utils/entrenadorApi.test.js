import { describe, expect, it } from 'vitest'
import {
  TODAS_LAS_CATEGORIAS, adaptarPerfilEntrenador, agruparPorCategoria, filtrarPorCategoria, mensajeDeErrorEntrenador, ordenarJugadores,
  sesionesDelEntrenador, textoEdad, textoJugadores, unirSinDuplicados,
} from './entrenadorApi.js'

const j = (id, nombre, apellido, extra = {}) => ({ id, nombre, apellido, edad: 10, categoria: 'Sub-11', ...extra })

describe('adaptarPerfilEntrenador', () => {
  it('toma idEntrenador y las categorías de /usuarios/yo', () => {
    const perfil = { id: 'u', rol: 'ENTRENADOR', entrenador: { idEntrenador: 'e1', categorias: [{ idCategoria: 'c1', nombre: 'Sub-11', extra: 1 }, { idCategoria: 'c2', nombre: 'Sub-12' }] } }
    expect(adaptarPerfilEntrenador(perfil)).toEqual({ idEntrenador: 'e1', categorias: [{ idCategoria: 'c1', nombre: 'Sub-11' }, { idCategoria: 'c2', nombre: 'Sub-12' }] })
  })

  it('acepta id como respaldo si el backend lo manda con ese nombre', () => {
    expect(adaptarPerfilEntrenador({ entrenador: { idEntrenador: 'e1', categorias: [{ id: 'c9', nombre: 'Sub-13' }] } }).categorias).toEqual([{ idCategoria: 'c9', nombre: 'Sub-13' }])
  })

  it('devuelve null si la cuenta no tiene entrenador vinculado', () => {
    expect(adaptarPerfilEntrenador({ entrenador: null })).toBeNull()
    expect(adaptarPerfilEntrenador({})).toBeNull()
    expect(adaptarPerfilEntrenador(null)).toBeNull()
  })

  it('un entrenador sin categorías devuelve la lista vacía', () => {
    expect(adaptarPerfilEntrenador({ entrenador: { idEntrenador: 'e1', categorias: [] } }).categorias).toEqual([])
  })
})

describe('unir y agrupar', () => {
  it('unirSinDuplicados conserva la primera aparición', () => {
    const unidos = unirSinDuplicados([[j('1', 'Ana', 'A')], [j('1', 'Repetido', 'X'), j('2', 'Beto', 'B')]])
    expect(unidos.map((x) => x.id)).toEqual(['1', '2'])
    expect(unidos[0].nombre).toBe('Ana')
  })

  it('agruparPorCategoria cuenta por categoría y total único', () => {
    const cats = [{ idCategoria: 'c1', nombre: 'Sub-11' }, { idCategoria: 'c2', nombre: 'Sub-12' }, { idCategoria: 'c3', nombre: 'Sub-13' }]
    const r = agruparPorCategoria(cats, [[j('1', 'A', 'A'), j('2', 'B', 'B')], [j('2', 'B', 'B')], []])
    expect(r.categorias.map((c) => c.jugadores.length)).toEqual([2, 1, 0])
    expect(r.jugadores.map((x) => x.id)).toEqual(['1', '2'])
  })
})

describe('orden y filtro locales', () => {
  const lista = [
    j('1', 'Zoe', 'Álvarez', { edad: 12, categoria: 'Sub-12' }),
    j('2', 'Ana', 'Zapata', { edad: 9, categoria: 'Sub-11' }),
    j('3', 'Luis', 'Mejía', { edad: 12, categoria: 'Sub-11' }),
  ]

  it('por apellido ignora tildes y no muta la lista', () => {
    expect(ordenarJugadores(lista, 'apellido').map((x) => x.id)).toEqual(['1', '3', '2'])
    expect(lista.map((x) => x.id)).toEqual(['1', '2', '3'])
  })

  it('por nombre, edad y categoría', () => {
    expect(ordenarJugadores(lista, 'nombre').map((x) => x.id)).toEqual(['2', '3', '1'])
    expect(ordenarJugadores(lista, 'edad-asc').map((x) => x.id)).toEqual(['2', '1', '3'])
    expect(ordenarJugadores(lista, 'edad-desc').map((x) => x.id)).toEqual(['1', '3', '2'])
    expect(ordenarJugadores(lista, 'categoria', ['Sub-11', 'Sub-12']).map((x) => x.id)).toEqual(['3', '2', '1'])
  })

  it('un orden desconocido cae en apellido', () => {
    expect(ordenarJugadores(lista, 'raro').map((x) => x.id)).toEqual(['1', '3', '2'])
  })

  it('filtrarPorCategoria', () => {
    expect(filtrarPorCategoria(lista, TODAS_LAS_CATEGORIAS)).toHaveLength(3)
    expect(filtrarPorCategoria(lista, 'Sub-11').map((x) => x.id)).toEqual(['2', '3'])
    expect(filtrarPorCategoria(lista, 'Sub-16')).toEqual([])
  })
})

describe('sesionesDelEntrenador', () => {
  const franja = (id, categoria, dia, horaInicio, extra = {}) => ({ id, categoria, dia, horaInicio, horaFin: '19:00', sede: 'Horizontes', grupo: null, ...extra })
  const franjas = [
    franja('a', 'Sub-11', 2, '17:00'), // martes
    franja('b', 'Sub-11', 4, '17:00'), // jueves
    franja('c', 'Sub-16', 2, '18:00'), // otra categoría
    franja('d', 'Sub-12', 1, '17:00'), // lunes (ya pasó)
  ]
  const cats = [{ nombre: 'Sub-11' }, { nombre: 'Sub-12' }]

  it('separa hoy del resto de la semana y deja solo las categorías del entrenador', () => {
    const r = sesionesDelEntrenador(franjas, cats, '2026-10-20') // martes 20
    expect(r.hoy.map((s) => s.idFranja)).toEqual(['a'])
    expect(r.semana.map((s) => s.idFranja)).toEqual(['b'])
  })

  it('no incluye días anteriores a hoy', () => {
    const r = sesionesDelEntrenador(franjas, cats, '2026-10-21') // miércoles
    expect(r.hoy).toEqual([])
    expect(r.semana.map((s) => s.idFranja)).toEqual(['b'])
  })

  it('el domingo cuenta la semana que viene', () => {
    const r = sesionesDelEntrenador(franjas, cats, '2026-10-25') // domingo
    expect(r.hoy).toEqual([])
    expect(r.semana.map((s) => s.idFranja)).toEqual(['d', 'a', 'b'])
  })

  it('sin categorías no hay sesiones', () => {
    expect(sesionesDelEntrenador(franjas, [], '2026-10-20')).toEqual({ hoy: [], semana: [] })
  })
})

describe('textos', () => {
  it('singular y plural', () => {
    expect(textoEdad(1)).toBe('1 año')
    expect(textoEdad(9)).toBe('9 años')
    expect(textoJugadores(1)).toBe('1 jugador')
    expect(textoJugadores(0)).toBe('0 jugadores')
  })

  it('mensajeDeErrorEntrenador por estado', () => {
    expect(mensajeDeErrorEntrenador({ estado: 0 }, 'x')).toMatch(/conectar/)
    expect(mensajeDeErrorEntrenador({ estado: 401 }, 'x')).toMatch(/sesión venció/)
    expect(mensajeDeErrorEntrenador({ estado: 403 }, 'x')).toMatch(/permiso/)
    expect(mensajeDeErrorEntrenador({ estado: 500 }, 'genérico')).toBe('genérico')
  })
})
