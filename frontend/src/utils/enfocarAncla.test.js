import { describe, expect, it, vi } from 'vitest'
import { enfocarAncla } from './enfocarAncla.js'

const documento = (elemento) => ({ getElementById: vi.fn((id) => (id === 'foto-deportista' ? elemento : null)) })

describe('enfocarAncla', () => {
  it('enfoca y trae a la vista el elemento del hash', () => {
    const elemento = { focus: vi.fn(), scrollIntoView: vi.fn() }
    expect(enfocarAncla('#foto-deportista', documento(elemento))).toBe(true)
    expect(elemento.focus).toHaveBeenCalledTimes(1)
    expect(elemento.scrollIntoView).toHaveBeenCalledTimes(1)
  })
  it('sin hash o con un hash que no existe no hace nada', () => {
    const elemento = { focus: vi.fn(), scrollIntoView: vi.fn() }
    for (const hash of ['', '#', undefined, '#otro']) expect(enfocarAncla(hash, documento(elemento))).toBe(false)
    expect(elemento.focus).not.toHaveBeenCalled()
  })
})
