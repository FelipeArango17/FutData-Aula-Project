import { afterEach, describe, expect, it, vi } from 'vitest'
import { MODULOS_PREVISTOS, modulosConApi, usarApiDe } from './usarApi.js'

describe('usarApiDe', () => {
  afterEach(() => vi.unstubAllEnvs())

  it('en las pruebas es siempre false, aunque el módulo esté en la lista', () => {
    expect(usarApiDe('entrenadores', { modo: 'test', modulos: 'entrenadores' })).toBe(false)
    expect(usarApiDe('entrenadores')).toBe(false) // el MODE real de vitest es "test"
  })
  it('sin la variable solo "entrenadores" usa la API (igual que antes)', () => {
    // Hermética: `modulos: undefined` cae en import.meta.env, así que se quita la variable (no se pone '', que significa "ninguno").
    vi.stubEnv('VITE_API_MODULOS', undefined)
    expect(usarApiDe('entrenadores', { modo: 'development', modulos: undefined })).toBe(true)
    for (const m of MODULOS_PREVISTOS.filter((x) => x !== 'entrenadores')) expect(usarApiDe(m, { modo: 'production', modulos: undefined })).toBe(false)
  })
  it('con la variable, solo los módulos de la lista (sin importar espacios ni mayúsculas)', () => {
    const modulos = ' Entrenadores, deportistas ,'
    expect(usarApiDe('entrenadores', { modo: 'production', modulos })).toBe(true)
    expect(usarApiDe('deportistas', { modo: 'production', modulos })).toBe(true)
    expect(usarApiDe('pagos', { modo: 'production', modulos })).toBe(false)
  })
  it('la variable vacía apaga todos los módulos', () => {
    expect(usarApiDe('entrenadores', { modo: 'production', modulos: '' })).toBe(false)
    expect(modulosConApi('')).toEqual([])
  })
})
