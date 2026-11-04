import { describe, expect, it } from 'vitest'
import { mesYAnio, saludoDe } from './saludo.js'

describe('saludoDe', () => {
  it('usa el nombre real', () => expect(saludoDe({ nombre: ' Laura Gómez ' })).toBe('Hola, Laura Gómez'))
  it('sin nombre solo saluda', () => {
    expect(saludoDe({ nombre: '' })).toBe('Hola')
    expect(saludoDe(null)).toBe('Hola')
    expect(saludoDe(undefined)).toBe('Hola')
  })
})
describe('mesYAnio', () => {
  it('escribe el mes y el año', () => expect(mesYAnio('2026-11-03')).toBe('noviembre 2026'))
})
