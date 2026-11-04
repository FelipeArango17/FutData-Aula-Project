import { describe, expect, it } from 'vitest'
import {
  carteraVencidaPorDeportista, generarCsvCobros, mesAnterior, recaudoPorMes, resumenAcudiente, resumenCartera, ultimosPagosRecibidos,
} from './resumenCartera.js'

let n = 0
const cobro = (extra = {}) => ({
  id: `c${++n}`, idDeportista: 'd1', nombreDeportista: 'Pedro Gómez', tipo: 'MENSUALIDAD', concepto: 'Mensualidad', monto: 100000,
  fechaVencimiento: '2026-11-05', fechaPago: null, estado: 'pendiente', ...extra,
})
const pagado = (fechaPago, monto = 100000, extra = {}) => cobro({ estado: 'pagado', fechaPago, monto, ...extra })

describe('mesAnterior', () => {
  it('resta meses y cruza el cambio de año', () => {
    expect(mesAnterior('2026-11')).toBe('2026-10')
    expect(mesAnterior('2026-01')).toBe('2025-12')
    expect(mesAnterior('2026-03', 5)).toBe('2025-10')
  })
})

describe('resumenCartera', () => {
  it('sin cobros todo es cero y no hay mes anterior', () => {
    const r = resumenCartera([], '2026-11-10')
    expect(r.hayCobros).toBe(false)
    expect(r.recaudoMes).toEqual({ total: 0, cantidad: 0 })
    expect(r.recaudoMesAnterior).toBeNull()
    expect(r.carteraVencida).toEqual({ total: 0, cantidad: 0 })
    expect(r.ultimosPagos).toEqual([])
    expect(r.recaudoPorMes).toHaveLength(6)
  })

  it('un mes sin pagos da recaudo 0 aunque haya cobros pendientes', () => {
    const r = resumenCartera([cobro()], '2026-11-10')
    expect(r.hayCobros).toBe(true)
    expect(r.recaudoMes.total).toBe(0)
    expect(r.porCobrarMes).toEqual({ total: 100000, cantidad: 1 })
  })

  it('el recaudo del mes cuenta todos los tipos y solo APROBADOS con fechaPago en el mes', () => {
    const r = resumenCartera([
      pagado('2026-11-02', 90000), pagado('2026-11-09', 150000, { tipo: 'INSCRIPCION' }), pagado('2026-10-30', 70000),
      cobro({ estado: 'rechazado', fechaPago: '2026-11-03' }),
    ], '2026-11-10')
    expect(r.recaudoMes).toEqual({ total: 240000, cantidad: 2 })
  })

  it('informa el mes anterior (sin porcentaje) solo si ese mes tiene pagos', () => {
    const sin = resumenCartera([pagado('2026-11-02', 100000)], '2026-11-10')
    expect(sin.recaudoMesAnterior).toBeNull()
    const con = resumenCartera([pagado('2026-11-02', 150000), pagado('2026-10-20', 100000)], '2026-11-10')
    expect(con.recaudoMesAnterior).toEqual({ mes: '2026-10', total: 100000 })
  })

  it('cambio de mes y de año: el 1 de enero mira diciembre', () => {
    const cobros = [pagado('2025-12-31', 80000), pagado('2026-01-01', 90000)]
    const r = resumenCartera(cobros, '2026-01-01')
    expect(r.recaudoMes.total).toBe(90000)
    expect(r.recaudoMesAnterior).toEqual({ mes: '2025-12', total: 80000 })
  })

  it('límites de fecha: el último día del mes cuenta en su mes y el primero del siguiente, en el otro', () => {
    const cobros = [pagado('2026-10-31', 100000), pagado('2026-11-01', 50000)]
    expect(resumenCartera(cobros, '2026-10-31').recaudoMes.total).toBe(100000)
    expect(resumenCartera(cobros, '2026-11-01').recaudoMes.total).toBe(50000)
  })

  it('la cartera vencida suma solo los vencidos; los pendientes al día y los rechazados no cuentan', () => {
    const r = resumenCartera([
      cobro({ estado: 'vencido', monto: 90000 }), cobro({ estado: 'vencido', monto: 60000 }),
      cobro({ estado: 'pendiente' }), cobro({ estado: 'rechazado' }), pagado('2026-11-02'),
    ], '2026-11-10')
    expect(r.carteraVencida).toEqual({ total: 150000, cantidad: 2 })
  })

  it('por cobrar del mes: pendientes al día con vencimiento en el mes de hoy', () => {
    const r = resumenCartera([
      cobro({ fechaVencimiento: '2026-11-30' }), cobro({ fechaVencimiento: '2026-12-01' }), cobro({ estado: 'vencido', fechaVencimiento: '2026-11-01' }),
    ], '2026-11-10')
    expect(r.porCobrarMes).toEqual({ total: 100000, cantidad: 1 })
  })
})

describe('ultimosPagosRecibidos', () => {
  it('ordena por fecha de pago descendente y limita a 5', () => {
    const lista = ['2026-11-01', '2026-11-07', '2026-10-15', '2026-11-03', '2026-11-05', '2026-09-01', '2026-11-06'].map((f) => pagado(f))
    expect(ultimosPagosRecibidos(lista).map((c) => c.fechaPago)).toEqual(['2026-11-07', '2026-11-06', '2026-11-05', '2026-11-03', '2026-11-01'])
  })
})

describe('recaudoPorMes', () => {
  it('devuelve 6 meses terminando en el actual, con ceros en los vacíos', () => {
    const r = recaudoPorMes([pagado('2026-11-02', 100000), pagado('2026-07-10', 40000), pagado('2026-04-30', 999)], '2026-11-15')
    expect(r.map((m) => m.mes)).toEqual(['2026-06', '2026-07', '2026-08', '2026-09', '2026-10', '2026-11'])
    expect(r.map((m) => m.total)).toEqual([0, 40000, 0, 0, 0, 100000])
  })
})

describe('carteraVencidaPorDeportista', () => {
  it('agrupa por deportista, de mayor a menor', () => {
    const r = carteraVencidaPorDeportista([
      cobro({ estado: 'vencido', idDeportista: 'a', nombreDeportista: 'Ana', monto: 50000 }),
      cobro({ estado: 'vencido', idDeportista: 'b', nombreDeportista: 'Beto', monto: 90000 }),
      cobro({ estado: 'vencido', idDeportista: 'a', nombreDeportista: 'Ana', monto: 50000 }),
    ])
    expect(r).toEqual([
      { idDeportista: 'a', nombre: 'Ana', total: 100000, cantidad: 2 },
      { idDeportista: 'b', nombre: 'Beto', total: 90000, cantidad: 1 },
    ])
  })
})

describe('resumenAcudiente', () => {
  const pago = (extra) => ({ id: `p${++n}`, concepto: 'Mensualidad', monto: 90000, fechaVencimiento: '2026-11-05', fechaPago: null, estado: 'pendiente', ...extra })
  it('separa próximos pagos y últimos pagados, con el nombre del deportista', () => {
    const estado = {
      totalPendiente: 180000, totalVencido: 90000,
      deportistas: [{ deportista: { id: 'd1', nombre: 'Santi' }, pagos: [
        pago({ estado: 'pendiente', fechaVencimiento: '2026-11-30' }), pago({ estado: 'vencido', fechaVencimiento: '2026-10-10' }),
        pago({ estado: 'pagado', fechaPago: '2026-09-03' }), pago({ estado: 'pagado', fechaPago: '2026-10-03' }), pago({ estado: 'rechazado' }),
      ] }],
    }
    const r = resumenAcudiente(estado)
    expect(r.proximos.map((p) => p.estado)).toEqual(['vencido', 'pendiente'])
    expect(r.proximos[0].nombreDeportista).toBe('Santi')
    expect(r.ultimosPagados.map((p) => p.fechaPago)).toEqual(['2026-10-03', '2026-09-03'])
    expect(r.hayPagos).toBe(true)
    expect(r.totalVencido).toBe(90000)
  })
  it('sin pagos devuelve listas vacías', () => {
    expect(resumenAcudiente({ deportistas: [] })).toMatchObject({ hayPagos: false, proximos: [], ultimosPagados: [] })
  })
})

describe('generarCsvCobros', () => {
  it('arma encabezado y filas, neutraliza fórmulas y escapa comillas', () => {
    const csv = generarCsvCobros([cobro({ nombreDeportista: '=HYPERLINK("x")', concepto: 'Uniforme; talla M', estado: 'vencido' })])
    const [encabezado, fila] = csv.split('\r\n')
    expect(encabezado).toBe('Deportista;Tipo;Concepto;Monto;Vencimiento;Fecha de pago;Estado')
    expect(fila).toBe('"\'=HYPERLINK(""x"")";MENSUALIDAD;"Uniforme; talla M";100000;2026-11-05;;Vencido')
  })
})
