/* global process */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import CamposDeportista from './CamposDeportista.jsx'
import { validarDatosDeportista } from '../../services/deportistasService.js'
import { hoyLocal } from '../../utils/fechas.js'
import { validarFechaNacimiento, validarFechaPago, validarMes } from '../../utils/validaciones.js'

const tzOriginal = process.env.TZ
beforeEach(() => {
  process.env.TZ = 'America/Bogota'
  vi.useFakeTimers()
})
afterEach(() => {
  vi.useRealTimers()
  if (tzOriginal === undefined) delete process.env.TZ
  else process.env.TZ = tzOriginal
})

const deportista = (fechaNacimiento) => ({
  nombre: 'Lucas', apellido: 'Pérez', tipoDocumento: 'Tarjeta de identidad', documento: '1200000001', fechaNacimiento, telefono: '3001234567', direccion: 'Calle 50 # 10-20',
})
const texto = () => ({ value: '', error: undefined, onChange: () => {} })

describe('el "hoy" de las validaciones es el día local', () => {
  it('con el reloj en 2026-10-31, una fecha de nacimiento del 23 al 31 de octubre es válida en el formulario de deportista', () => {
    vi.setSystemTime(new Date('2026-10-31T10:00:00-05:00'))
    for (const dia of ['2026-10-23', '2026-10-30', '2026-10-31']) {
      expect(validarDatosDeportista(deportista(dia)), dia).toEqual({})
    }
    expect(validarDatosDeportista(deportista('2026-11-01'))).toEqual({ fechaNacimiento: 'La fecha de nacimiento no puede ser futura.' })
  })

  it('el max del input, la edad mostrada y la validación coinciden', () => {
    vi.setSystemTime(new Date('2026-10-31T10:00:00-05:00'))
    const html = renderToStaticMarkup(<CamposDeportista texto={texto} fechaNacimiento="2026-10-31" />)
    expect(html).toContain('max="2026-10-31"')
    expect(html).toContain('0 años')
  })

  it('a las 8 p. m. en Colombia el "hoy" es el día local, no el siguiente en UTC', () => {
    vi.setSystemTime(new Date('2026-10-31T20:00:00-05:00'))
    expect(new Date().toISOString().slice(0, 10)).toBe('2026-11-01')
    expect(hoyLocal()).toBe('2026-10-31')
    expect(validarFechaNacimiento('2026-10-31')).toBeNull()
    expect(validarFechaNacimiento('2026-11-01')).toBe('La fecha de nacimiento no puede ser futura.')
    expect(validarFechaPago('2026-10-31')).toBeNull()
    expect(validarFechaPago('2026-11-01')).toBe('La fecha de pago no puede ser futura.')
    expect(validarMes('2026-10')).toBeNull()
    expect(validarMes('2026-11')).toBe('No se puede registrar el pago de un mes futuro.')
  })
})
