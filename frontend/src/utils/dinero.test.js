import { describe, expect, it } from 'vitest'
import { formatearCOP } from './dinero.js'

// Intl usa un espacio duro entre el símbolo y el número: se normaliza para comparar
const texto = (n) => formatearCOP(n).replaceAll(String.fromCharCode(160), ' ')

describe('formatearCOP', () => {
  it('usa punto de miles, símbolo $ y sin decimales', () => {
    expect(texto(90000)).toBe('$ 90.000')
    expect(texto(150000)).toBe('$ 150.000')
    expect(texto(1234567)).toBe('$ 1.234.567')
  })

  it('formatea cero', () => {
    expect(texto(0)).toBe('$ 0')
  })

  it('redondea si llega con decimales (los montos son enteros)', () => {
    expect(texto(90000.4)).toBe('$ 90.000')
    expect(texto(90000.6)).toBe('$ 90.001')
  })

  it('el símbolo y el número no se separan con un espacio normal (no parte la línea)', () => {
    expect(formatearCOP(90000)).not.toContain(String.fromCharCode(32))
  })

  it('rechaza lo que no es un número', () => {
    expect(() => formatearCOP('90000')).toThrow(TypeError)
    expect(() => formatearCOP(NaN)).toThrow(TypeError)
    expect(() => formatearCOP(undefined)).toThrow(TypeError)
  })
})
