import { describe, expect, it } from 'vitest'
import { MAX_RESULTADOS, buscarDeportistas, opcionAlPulsarEnter } from './buscarDeportistas.js'
import { adaptarDeportista } from './deportistasApi.js'

// Con el formato real de DeportistaResponseDTO (para el administrador), pasado por el adaptador de las pantallas
const dto = (n, nombre, apellido, documento, extra = {}) => ({
  id: `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`, nombre, apellido, tipoDocumento: 'TI', documento, fechaNacimiento: '2016-08-20', edad: 10,
  telefono: '3001234567', direccion: 'Calle 10 # 20-30', posicion: null, foto: null, categoria: 'Semillero', idCategoria: '0a0a0a0a-1111-4222-8333-444444444444',
  grupo: null, activo: true, estadoInscripcion: 'PENDIENTE', ...extra,
})
const lista = [
  dto(1, 'Samuel', 'Londoño Ríos', '1.098.765.432'),
  dto(2, 'María José', 'Peña Gómez', '1200000002'),
  dto(3, 'Juan', 'Pérez', '1200000003'),
  dto(4, 'Juan', 'Pérez', '1200000004'), // homónimo con otro documento
  dto(5, 'Ángela', 'Ortiz', '98-765 4321'),
].map(adaptarDeportista)
const nombres = (r) => r.resultados.map((d) => `${d.nombre} ${d.apellido}`)

describe('buscarDeportistas', () => {
  it('sin distinguir mayúsculas ni tildes', () => {
    expect(nombres(buscarDeportistas(lista, 'LONDONO'))).toEqual(['Samuel Londoño Ríos'])
    expect(nombres(buscarDeportistas(lista, 'angela'))).toEqual(['Ángela Ortiz'])
    expect(nombres(buscarDeportistas(lista, 'maria jose pena'))).toEqual(['María José Peña Gómez'])
    expect(nombres(buscarDeportistas(lista, 'peña'))).toEqual(['María José Peña Gómez'])
  })

  it('en cualquier orden de palabras: «Londoño Samuel» también encuentra', () => {
    expect(nombres(buscarDeportistas(lista, 'Londoño Samuel'))).toEqual(['Samuel Londoño Ríos'])
    expect(nombres(buscarDeportistas(lista, 'gomez jose'))).toEqual(['María José Peña Gómez'])
  })

  it('por documento ignorando puntos, espacios y guiones, en cualquier lado', () => {
    expect(nombres(buscarDeportistas(lista, '1098765432'))).toEqual(['Samuel Londoño Ríos'])
    expect(nombres(buscarDeportistas(lista, '1.098.765'))).toEqual(['Samuel Londoño Ríos'])
    expect(nombres(buscarDeportistas(lista, '98 765-4321'))).toEqual(['Ángela Ortiz'])
    expect(nombres(buscarDeportistas(lista, '987654321'))).toEqual(['Ángela Ortiz'])
    expect(nombres(buscarDeportistas(lista, '120000000'))).toHaveLength(3)
  })

  it('homónimos: salen los dos, y el documento los distingue', () => {
    const juanes = buscarDeportistas(lista, 'juan perez')
    expect(juanes.total).toBe(2)
    expect(juanes.resultados.map((d) => d.documento)).toEqual(['1200000003', '1200000004'])
    expect(buscarDeportistas(lista, '1200000004').resultados.map((d) => d.id)).toEqual([lista[3].id])
  })

  it('sin texto (o solo espacios) devuelve las primeras opciones y el total de la lista', () => {
    expect(buscarDeportistas(lista, '')).toEqual({ resultados: lista, total: 5 })
    expect(buscarDeportistas(lista, '   ').total).toBe(5)
    expect(buscarDeportistas(lista, undefined).total).toBe(5)
    expect(buscarDeportistas(undefined, 'a')).toEqual({ resultados: [], total: 0 })
  })

  it('sin coincidencias: lista vacía y total 0', () => {
    expect(buscarDeportistas(lista, 'zzz')).toEqual({ resultados: [], total: 0 })
    expect(buscarDeportistas(lista, 'samuel 999')).toEqual({ resultados: [], total: 0 })
  })

  it('más de 30 coincidencias: devuelve 30 y cuántas fueron en total, en el orden de la lista', () => {
    const muchos = Array.from({ length: 130 }, (_, i) => adaptarDeportista(dto(i + 100, `Jugador${i}`, 'García', String(1300000000 + i))))
    const r = buscarDeportistas(muchos, 'garcia')
    expect(MAX_RESULTADOS).toBe(30)
    expect(r.resultados).toHaveLength(30)
    expect(r.total).toBe(130)
    expect(r.resultados[0].nombre).toBe('Jugador0')
    expect(buscarDeportistas(muchos, '').resultados).toHaveLength(30)
    expect(buscarDeportistas(muchos, '').total).toBe(130)
    expect(buscarDeportistas(muchos, 'jugador12', 5).resultados).toHaveLength(5)
  })
})

describe('opcionAlPulsarEnter', () => {
  it('una sola opción y sin flechas: Enter la elige (también tras filtrar hasta una)', () => {
    expect(opcionAlPulsarEnter([lista[0]], -1)).toBe(lista[0])
    expect(opcionAlPulsarEnter(buscarDeportistas(lista, 'londono').resultados, -1)).toBe(lista[0])
  })
  it('varias opciones y sin flechas: Enter no elige nada', () => {
    expect(opcionAlPulsarEnter(lista, -1)).toBeNull()
    expect(opcionAlPulsarEnter(buscarDeportistas(lista, 'juan perez').resultados, -1)).toBeNull()
  })
  it('sin opciones: no elige nada', () => {
    expect(opcionAlPulsarEnter([], -1)).toBeNull()
  })
  it('con flechas elige la resaltada, haya una o varias', () => {
    expect(opcionAlPulsarEnter(lista, 2)).toBe(lista[2])
    expect(opcionAlPulsarEnter([lista[0]], 0)).toBe(lista[0])
  })
  it('un índice fuera de rango no elige nada', () => {
    expect(opcionAlPulsarEnter(lista, 9)).toBeNull()
  })
})
