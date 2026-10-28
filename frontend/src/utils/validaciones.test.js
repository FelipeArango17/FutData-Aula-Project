import { describe, expect, it } from 'vitest'
import { esCorreoValido } from './validaciones.js'

describe('esCorreoValido', () => {
  it('acepta correos con formato válido', () => {
    expect(esCorreoValido('nombre@correo.com')).toBe(true)
    expect(esCorreoValido('carlos.diaz@pradofc.co')).toBe(true)
    expect(esCorreoValido('a+b@sub.dominio.org')).toBe(true)
    expect(esCorreoValido('  nombre@correo.com  ')).toBe(true)
  })

  it('rechaza correos incompletos o con espacios', () => {
    expect(esCorreoValido('')).toBe(false)
    expect(esCorreoValido('nombre')).toBe(false)
    expect(esCorreoValido('nombre@')).toBe(false)
    expect(esCorreoValido('@correo.com')).toBe(false)
    expect(esCorreoValido('nombre@correo')).toBe(false)
    expect(esCorreoValido('nom bre@correo.com')).toBe(false)
    expect(esCorreoValido('a@@b.com')).toBe(false)
  })
})
