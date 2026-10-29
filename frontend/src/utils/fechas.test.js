import { describe, expect, it } from 'vitest'
import {
  diaCorto, diaLargo, diasDeSemana, esDomingo, esFechaValida, esHoraValida, fechaLargaConAnio,
  formatHora, lunesDe, mesEnTexto, momentoLegible, partesMomento, rangoSemana, sumarDias, sumarMinutos,
} from './fechas.js'

describe('esFechaValida', () => {
  it('acepta fechas reales, incluido el 29 de febrero de un año bisiesto', () => {
    expect(esFechaValida('2026-10-22')).toBe(true)
    expect(esFechaValida('2028-02-29')).toBe(true)
    expect(esFechaValida('2026-12-31')).toBe(true)
  })

  it('rechaza fechas que no existen', () => {
    expect(esFechaValida('2027-02-29')).toBe(false)
    expect(esFechaValida('2026-02-30')).toBe(false)
    expect(esFechaValida('2026-04-31')).toBe(false)
  })

  // ERROR REAL conocido (no corregido, solo documentado): con mes 13, mes 00 o día fuera de 01-31 la función
  // lanza RangeError ("Invalid time value") en vez de devolver false. it.fails pasa mientras el error exista;
  // cuando se corrija, esta prueba fallará y hay que cambiarla por it normal.
  it.fails('rechaza mes 13, mes 00 y día 32 devolviendo false (hoy lanza RangeError)', () => {
    expect(esFechaValida('2026-13-01')).toBe(false)
    expect(esFechaValida('2026-00-10')).toBe(false)
    expect(esFechaValida('2026-10-32')).toBe(false)
  })

  it('rechaza formatos distintos de AAAA-MM-DD', () => {
    expect(esFechaValida('')).toBe(false)
    expect(esFechaValida('22/10/2026')).toBe(false)
    expect(esFechaValida('2026-1-1')).toBe(false)
    expect(esFechaValida('2026-10-22T10:00')).toBe(false)
  })
})

describe('esHoraValida', () => {
  it('acepta los extremos del día', () => {
    expect(esHoraValida('00:00')).toBe(true)
    expect(esHoraValida('23:59')).toBe(true)
    expect(esHoraValida('09:05')).toBe(true)
  })

  it('rechaza horas fuera de rango o mal escritas', () => {
    expect(esHoraValida('24:00')).toBe(false)
    expect(esHoraValida('12:60')).toBe(false)
    expect(esHoraValida('9:00')).toBe(false)
    expect(esHoraValida('09:0')).toBe(false)
    expect(esHoraValida('')).toBe(false)
    expect(esHoraValida('10:00:00')).toBe(false)
  })
})

describe('sumarDias', () => {
  it('cruza el fin de mes y de año', () => {
    expect(sumarDias('2026-10-31', 1)).toBe('2026-11-01')
    expect(sumarDias('2026-12-31', 1)).toBe('2027-01-01')
  })

  it('respeta el 29 de febrero', () => {
    expect(sumarDias('2028-02-28', 1)).toBe('2028-02-29')
    expect(sumarDias('2028-02-29', 1)).toBe('2028-03-01')
    expect(sumarDias('2027-02-28', 1)).toBe('2027-03-01')
  })

  it('resta días y acepta cero', () => {
    expect(sumarDias('2026-03-01', -1)).toBe('2026-02-28')
    expect(sumarDias('2026-10-22', 0)).toBe('2026-10-22')
    expect(sumarDias('2026-10-22', 7)).toBe('2026-10-29')
  })
})

describe('esDomingo y lunesDe', () => {
  it('detecta el domingo', () => {
    expect(esDomingo('2026-10-25')).toBe(true)
    expect(esDomingo('2026-10-24')).toBe(false)
    expect(esDomingo('2026-10-26')).toBe(false)
  })

  it('devuelve el lunes de la semana (el domingo cuenta como el final de la semana)', () => {
    expect(lunesDe('2026-10-19')).toBe('2026-10-19')
    expect(lunesDe('2026-10-22')).toBe('2026-10-19')
    expect(lunesDe('2026-10-24')).toBe('2026-10-19')
    expect(lunesDe('2026-10-25')).toBe('2026-10-19')
    expect(lunesDe('2026-10-26')).toBe('2026-10-26')
  })

  it('diasDeSemana da lunes a sábado', () => {
    expect(diasDeSemana('2026-10-26')).toEqual([
      '2026-10-26', '2026-10-27', '2026-10-28', '2026-10-29', '2026-10-30', '2026-10-31',
    ])
  })
})

describe('textos de fecha', () => {
  it('diaLargo y diaCorto', () => {
    expect(diaLargo('2026-10-19')).toBe('Lunes 19 de octubre')
    expect(diaLargo('2026-10-21')).toBe('Miércoles 21 de octubre')
    expect(diaLargo('2026-10-25')).toBe('Domingo 25 de octubre')
    expect(diaCorto('2026-10-19')).toBe('Lun 19')
    expect(diaCorto('2026-10-24')).toBe('Sáb 24')
  })

  it('rangoSemana, con la semana dentro de un mes y cruzando de mes', () => {
    expect(rangoSemana('2026-10-19')).toBe('19 al 24 de octubre')
    expect(rangoSemana('2026-10-26')).toBe('26 al 31 de octubre')
    expect(rangoSemana('2026-11-30')).toBe('30 de noviembre al 5 de diciembre')
  })
})

describe('formatHora', () => {
  it('formato de 12 horas con a. m. y p. m.', () => {
    expect(formatHora('00:00')).toBe('12:00 a. m.')
    expect(formatHora('09:05')).toBe('9:05 a. m.')
    expect(formatHora('11:59')).toBe('11:59 a. m.')
    expect(formatHora('12:00')).toBe('12:00 p. m.')
    expect(formatHora('17:30')).toBe('5:30 p. m.')
    expect(formatHora('23:59')).toBe('11:59 p. m.')
  })
})

describe('sumarMinutos', () => {
  it('suma dentro del día', () => {
    expect(sumarMinutos('10:00', 90)).toBe('11:30')
    expect(sumarMinutos('00:00', 0)).toBe('00:00')
    expect(sumarMinutos('09:45', 30)).toBe('10:15')
    expect(sumarMinutos('22:00', 119)).toBe('23:59')
  })

  it('devuelve null si pasa de la medianoche', () => {
    expect(sumarMinutos('23:59', 1)).toBeNull()
    expect(sumarMinutos('23:00', 90)).toBeNull()
    expect(sumarMinutos('22:30', 90)).toBeNull()
  })
})

describe('fechaLargaConAnio y momentoLegible', () => {
  it('fecha larga con año, en minúscula', () => {
    expect(fechaLargaConAnio('2026-10-22')).toBe('jueves 22 de octubre de 2026')
    expect(fechaLargaConAnio('2026-01-01')).toBe('jueves 1 de enero de 2026')
  })

  it('momento legible a partir de un instante ISO (hora local)', () => {
    expect(momentoLegible(new Date(2026, 9, 22, 16, 5).toISOString())).toBe('Jueves 22 de octubre de 2026, 4:05 p. m.')
    expect(momentoLegible(new Date(2026, 0, 3, 0, 30).toISOString())).toBe('Sábado 3 de enero de 2026, 12:30 a. m.')
    expect(partesMomento(new Date(2026, 9, 22, 9, 7).toISOString())).toEqual({ fecha: '2026-10-22', hora: '09:07' })
  })
})

describe('mesEnTexto', () => {
  it('da el nombre del mes en minúscula', () => {
    expect(mesEnTexto('2026-10-22')).toBe('octubre')
    expect(mesEnTexto('2026-01-01')).toBe('enero')
    expect(mesEnTexto('2026-12-31')).toBe('diciembre')
  })
})
