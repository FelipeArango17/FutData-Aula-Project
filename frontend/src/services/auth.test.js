import { describe, expect, it } from 'vitest'
import { MENSAJE_SIN_CONFIGURACION, sinConfiguracion } from './auth.js'

describe('sinConfiguracion', () => {
  it('producción sin Supabase configurado -> bloqueado (no hay ingreso simulado)', () => {
    expect(sinConfiguracion(true, false)).toBe(true)
  })
  it('producción con Supabase configurado -> normal', () => {
    expect(sinConfiguracion(true, true)).toBe(false)
  })
  it('desarrollo y pruebas siguen con el ingreso simulado', () => {
    expect(sinConfiguracion(false, false)).toBe(false)
    expect(sinConfiguracion()).toBe(false) // en vitest PROD es false
  })
  it('el mensaje es el acordado', () => {
    expect(MENSAJE_SIN_CONFIGURACION).toBe('La aplicación no está configurada. Contacta al administrador.')
  })
})
