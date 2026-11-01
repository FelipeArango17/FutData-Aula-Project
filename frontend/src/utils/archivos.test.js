import { describe, expect, it } from 'vitest'
import { TAMANO_MAXIMO_BYTES, TIPOS_DOCUMENTO, TIPOS_FOTO, formatearTamano, validarArchivo } from './archivos.js'

const archivo = (type, size, name = 'archivo') => ({ name, type, size })

describe('constantes', () => {
  it('coinciden con los límites del backend', () => {
    expect(TAMANO_MAXIMO_BYTES).toBe(5242880)
    expect(TIPOS_FOTO).toEqual(['image/jpeg', 'image/png'])
    expect(TIPOS_DOCUMENTO).toEqual(['application/pdf', 'image/jpeg', 'image/png'])
  })
})

describe('formatearTamano', () => {
  it('usa MB con coma decimal, KB y bytes', () => {
    expect(formatearTamano(2.4 * 1024 * 1024)).toBe('2,4 MB')
    expect(formatearTamano(5 * 1024 * 1024)).toBe('5 MB')
    expect(formatearTamano(350 * 1024)).toBe('350 KB')
    expect(formatearTamano(800)).toBe('800 bytes')
    expect(formatearTamano(1)).toBe('1 byte')
  })
})

describe('validarArchivo', () => {
  it('sin archivo', () => {
    expect(validarArchivo(null)).toBe('Elige un archivo.')
    expect(validarArchivo(undefined, { tipos: TIPOS_FOTO })).toBe('Elige un archivo.')
  })

  it('acepta los tipos permitidos', () => {
    for (const t of TIPOS_DOCUMENTO) expect(validarArchivo(archivo(t, 1000))).toBeNull()
    for (const t of TIPOS_FOTO) expect(validarArchivo(archivo(t, 1000), { tipos: TIPOS_FOTO })).toBeNull()
  })

  it('rechaza un tipo no permitido con la lista de formatos que sí se aceptan', () => {
    expect(validarArchivo(archivo('application/pdf', 1000), { tipos: TIPOS_FOTO })).toBe('Solo se aceptan archivos JPG o PNG.')
    expect(validarArchivo(archivo('image/gif', 1000))).toBe('Solo se aceptan archivos PDF, JPG o PNG.')
    expect(validarArchivo(archivo('', 1000))).toBe('Solo se aceptan archivos PDF, JPG o PNG.')
  })

  it('rechaza un archivo vacío', () => {
    expect(validarArchivo(archivo('image/png', 0))).toBe('El archivo está vacío.')
  })

  it('justo en el límite: 5 MB vale y 5 MB + 1 byte no', () => {
    expect(validarArchivo(archivo('application/pdf', TAMANO_MAXIMO_BYTES))).toBeNull()
    expect(validarArchivo(archivo('application/pdf', 1))).toBeNull()
    expect(validarArchivo(archivo('application/pdf', TAMANO_MAXIMO_BYTES + 1))).toBe('El archivo pesa 5242881 bytes; el máximo es 5 MB.')
  })

  it('el mensaje de tamaño dice cuánto pesa', () => {
    expect(validarArchivo(archivo('image/png', 6.3 * 1024 * 1024))).toBe('El archivo pesa 6,3 MB; el máximo es 5 MB.')
  })

  it('acepta un maxBytes propio', () => {
    expect(validarArchivo(archivo('image/png', 2000), { maxBytes: 1024 })).toBe('El archivo pesa 2 KB; el máximo es 1 KB.')
  })

  it('el tipo se revisa antes que el tamaño', () => {
    expect(validarArchivo(archivo('image/gif', 0))).toBe('Solo se aceptan archivos PDF, JPG o PNG.')
  })
})
