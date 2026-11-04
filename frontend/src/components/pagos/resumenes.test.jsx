import { describe, expect, it, vi } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import EstadoDeCarga from './EstadoDeCarga.jsx'
import IndicadoresCartera, { UltimosPagos } from './IndicadoresCartera.jsx'
import { RecaudoPorMes, VencidosPorDeportista } from './ReporteCartera.jsx'
import { resumenCartera } from '../../utils/resumenCartera.js'

const html = (nodo) => renderToStaticMarkup(<MemoryRouter>{nodo}</MemoryRouter>)
const cobro = (extra) => ({ id: 'x', idDeportista: 'd', nombreDeportista: 'Pedro Gómez', concepto: 'Mensualidad', monto: 90000, fechaVencimiento: '2026-11-05', fechaPago: null, estado: 'pendiente', ...extra })

describe('EstadoDeCarga', () => {
  it('cargando', () => expect(html(<EstadoDeCarga cargando texto="Cargando…" />)).toContain('role="status"'))
  it('error con Reintentar', () => {
    const h = html(<EstadoDeCarga mensajeError="Falló" onReintentar={vi.fn()} />)
    expect(h).toContain('role="alert"')
    expect(h).toContain('Reintentar')
  })
  it('vacío', () => expect(html(<EstadoDeCarga vacio="Aún no hay pagos registrados." />)).toContain('Aún no hay pagos registrados.'))
  it('contenido', () => expect(html(<EstadoDeCarga><p>hola</p></EstadoDeCarga>)).toContain('hola'))
})

describe('indicadores', () => {
  it('muestra los montos en pesos y no muestra mes anterior si estuvo vacío', () => {
    const r = resumenCartera([cobro({ estado: 'pagado', fechaPago: '2026-11-02', monto: 120000 }), cobro({ estado: 'vencido', monto: 90000 })], '2026-11-10')
    const h = html(<IndicadoresCartera resumen={r} />)
    expect(h).toContain('120.000')
    expect(h).toContain('Atención')
    expect(h).not.toContain('Mes anterior')
  })
  it('recaudo con mes anterior: texto neutro, sin porcentaje ni insignia de variación', () => {
    const r = resumenCartera([cobro({ estado: 'pagado', fechaPago: '2026-11-02', monto: 120000 }), cobro({ estado: 'pagado', fechaPago: '2026-10-20', monto: 900000 })], '2026-11-03')
    const h = html(<IndicadoresCartera resumen={r} />)
    expect(h).toMatch(/Mes anterior \(oct\. 2026\): \$\s900\.000/)
    expect(h).not.toContain('%')
    expect(h).not.toContain('frente a')
  })
  it('sin pagos: textos claros', () => {
    expect(html(<UltimosPagos pagos={[]} />)).toContain('Aún no hay pagos recibidos.')
    expect(html(<VencidosPorDeportista filas={[]} />)).toContain('No hay cobros vencidos.')
  })
  it('la tabla de 6 meses tiene una fila por mes', () => {
    const r = resumenCartera([], '2026-11-10')
    expect(html(<RecaudoPorMes meses={r.recaudoPorMes} />).match(/<tr/g)).toHaveLength(7)
  })
})
