import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import SelectorMes from './SelectorMes.jsx'
import { OPCIONES_MES, opcionesAnio, partesDePeriodo, periodoTrasCambio } from '../../utils/periodo.js'

describe('utils/periodo', () => {
  it('doce meses en español, de enero a diciembre, con valor de dos dígitos', () => {
    expect(OPCIONES_MES).toHaveLength(12)
    expect(OPCIONES_MES[0]).toEqual({ valor: '01', texto: 'Enero' })
    expect(OPCIONES_MES[11]).toEqual({ valor: '12', texto: 'Diciembre' })
  })

  it('partesDePeriodo separa AAAA-MM y tolera valores vacíos o inválidos', () => {
    expect(partesDePeriodo('2026-11')).toEqual({ anio: '2026', mes: '11' })
    for (const v of ['', undefined, null, '2026-13', '2026-1', 'x']) expect(partesDePeriodo(v)).toEqual({ anio: '', mes: '' })
  })

  it('años: del anterior al siguiente; un año fuera de rango se conserva', () => {
    expect(opcionesAnio('', 2026)).toEqual(['2025', '2026', '2027'])
    expect(opcionesAnio('2030-01', 2026)).toEqual(['2025', '2026', '2027', '2030'])
  })

  it('emite siempre AAAA-MM: completa el que falte con hoy y vacía si se limpia uno', () => {
    expect(periodoTrasCambio('2026-11', 'mes', '03', '2026-11-04')).toBe('2026-03')
    expect(periodoTrasCambio('2026-11', 'anio', '2027', '2026-11-04')).toBe('2027-11')
    expect(periodoTrasCambio('', 'mes', '03', '2026-11-04')).toBe('2026-03')
    expect(periodoTrasCambio('', 'anio', '2027', '2026-11-04')).toBe('2027-11')
    expect(periodoTrasCambio('2026-11', 'mes', '', '2026-11-04')).toBe('')
  })
})

describe('SelectorMes', () => {
  const dibujar = (props) => renderToStaticMarkup(<SelectorMes id="p" label="Periodo (mes)" onChange={() => {}} {...props} />)

  it('dos listas con etiquetas asociadas y el valor seleccionado', () => {
    const h = dibujar({ value: '2026-03' })
    expect(h).toContain('<legend')
    expect(h).toContain('Periodo (mes)')
    expect(h).toContain('for="p-mes"')
    expect(h).toContain('id="p-mes"')
    expect(h).toContain('for="p-anio"')
    expect(h).toContain('id="p-anio"')
    expect(h).toMatch(/<option value="03" selected="">Marzo<\/option>/)
    expect(h).toMatch(/<option value="2026" selected="">2026<\/option>/)
    expect(h).toContain('>Enero<')
    expect(h).toContain('>Diciembre<')
  })

  it('sin valor no hay nada seleccionado', () => {
    expect(dibujar({ value: '' })).not.toContain('selected=""><')
  })

  it('desactivado: todo el grupo queda deshabilitado y la nota sigue ligada con aria-describedby', () => {
    const h = dibujar({ disabled: true, describedBy: 'nota-periodo' })
    expect(h).toMatch(/<fieldset[^>]*disabled=""/)
    expect(h).toContain('aria-describedby="nota-periodo"')
  })

  it('activo: sin disabled; el error se enlaza y se marca inválido', () => {
    const h = dibujar({ error: 'Elige el mes.' })
    expect(h).not.toMatch(/<fieldset[^>]*disabled/)
    expect(h).toContain('aria-describedby="p-error"')
    expect(h).toContain('aria-invalid="true"')
    expect(h).toContain('Elige el mes.')
  })
})
