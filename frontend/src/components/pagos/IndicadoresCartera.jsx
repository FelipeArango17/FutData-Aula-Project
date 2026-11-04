import Card from '../ui/Card.jsx'
import Badge from '../ui/Badge.jsx'
import { EstadoPago } from './ListaPagos.jsx'
import { formatearCOP } from '../../utils/dinero.js'
import { etiquetaMes } from '../../utils/resumenCartera.js'
import { fechaLargaConAnio } from '../../utils/fechas.js'

const plural = (n, uno, varios) => `${n} ${n === 1 ? uno : varios}`

function Kpi({ titulo, valor, detalle, referencia, badge }) {
  return (
    <Card className="flex flex-col gap-2">
      <h2 className="text-label uppercase text-secondary">{titulo}</h2>
      <p className="text-kpi">{valor}</p>
      <p className="text-small text-secondary">{detalle}</p>
      {referencia && <p className="text-small text-secondary">{referencia}</p>}
      {badge && <div><Badge tone={badge.tone}>{badge.texto}</Badge></div>}
    </Card>
  )
}

// Los tres indicadores de pagos. `resumen` sale de resumenCartera().
export default function IndicadoresCartera({ resumen }) {
  const { recaudoMes, carteraVencida, porCobrarMes, recaudoMesAnterior } = resumen
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <Kpi
        titulo="Recaudo del mes"
        valor={formatearCOP(recaudoMes.total)}
        detalle={recaudoMes.cantidad === 0 ? 'Aún no hay pagos recibidos este mes' : plural(recaudoMes.cantidad, 'pago recibido', 'pagos recibidos')}
        referencia={recaudoMesAnterior && `Mes anterior (${etiquetaMes(recaudoMesAnterior.mes)}): ${formatearCOP(recaudoMesAnterior.total)}`}
      />
      <Kpi
        titulo="Cartera vencida"
        valor={formatearCOP(carteraVencida.total)}
        detalle={carteraVencida.cantidad === 0 ? 'No hay cobros vencidos' : plural(carteraVencida.cantidad, 'cobro vencido', 'cobros vencidos')}
        badge={carteraVencida.cantidad > 0 ? { tone: 'danger', texto: 'Atención' } : { tone: 'success', texto: 'Al día' }}
      />
      <Kpi
        titulo="Por cobrar este mes"
        valor={formatearCOP(porCobrarMes.total)}
        detalle={porCobrarMes.cantidad === 0 ? 'No hay cobros pendientes con vencimiento este mes' : plural(porCobrarMes.cantidad, 'cobro pendiente', 'cobros pendientes')}
      />
    </div>
  )
}

// Últimos pagos recibidos (hasta 5). Si no hay ninguno, un texto claro.
export function UltimosPagos({ pagos }) {
  return (
    <Card aria-labelledby="ultimos-pagos-titulo" className="flex flex-col gap-3">
      <h2 id="ultimos-pagos-titulo" className="text-h3">Últimos pagos recibidos</h2>
      {pagos.length === 0 ? (
        <p className="text-body text-secondary">Aún no hay pagos recibidos.</p>
      ) : (
        <ul className="flex flex-col gap-3">
          {pagos.map((p) => (
            <li key={p.id} className="flex flex-wrap items-center justify-between gap-2">
              <div className="min-w-0">
                <p className="break-words text-body-medium">{p.nombreDeportista} · {p.concepto}</p>
                <p className="text-small text-secondary">{formatearCOP(p.monto)} · {fechaLargaConAnio(p.fechaPago)}</p>
              </div>
              <EstadoPago estado={p.estado} />
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}
