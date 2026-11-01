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
  nombre: 'Lucas', apellido: 'Pérez', tipoDocumento: 'Tarjeta de identidad', documento: '1200000001', fechaNacimiento, telefono: '3001234567', direccion: 'Calle 50 # 10-20', grupoEntrenamiento: 'mañana',
})
const texto = () => ({ value: '', error: undefined, onChange: () => {} })

describe('el "hoy" de las validaciones es el día local', () => {
  it('con el reloj en 2026-10-31 el formulario de deportista acepta los años 2010 a 2022 y rechaza 2009 y 2023', () => {
    vi.setSystemTime(new Date('2026-10-31T10:00:00-05:00'))
    for (const dia of ['2022-10-31', '2018-12-31', '2010-01-01']) {
      expect(validarDatosDeportista(deportista(dia)), dia).toEqual({})
    }
    const fuera = { fechaNacimiento: 'Solo se inscriben deportistas nacidos entre 2010 y 2022.' }
    expect(validarDatosDeportista(deportista('2023-01-01'))).toEqual(fuera)
    expect(validarDatosDeportista(deportista('2009-12-31'))).toEqual(fuera)
    // una fecha de octubre de 2026 ya no es futura el 31, pero tampoco está en el rango; el 1 de noviembre es futura
    expect(validarDatosDeportista(deportista('2026-10-23'))).toEqual(fuera)
    expect(validarDatosDeportista(deportista('2026-11-01'))).toEqual({ fechaNacimiento: 'La fecha de nacimiento no puede ser futura.' })
  })

  it('la edad mostrada usa el día local (2018-10-31 con el reloj en 2026-10-31 tiene 8 años)', () => {
    vi.setSystemTime(new Date('2026-10-31T10:00:00-05:00'))
    const html = renderToStaticMarkup(<CamposDeportista texto={texto} fechaNacimiento="2018-10-31" />)
    expect(html).toContain('value="8 años"')
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
