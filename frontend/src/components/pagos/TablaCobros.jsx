import Badge from '../ui/Badge.jsx'
import Button from '../ui/Button.jsx'
import Card from '../ui/Card.jsx'
import Icon from '../Icon.jsx'
import { EstadoPago } from './ListaPagos.jsx'
import { ETIQUETAS_TIPO } from '../../utils/pagosApi.js'
import { fechaLargaConAnio } from '../../utils/fechas.js'
import { formatearCOP } from '../../utils/dinero.js'

// Lista de cobros para el tesorero y el administrador: cargando, error, vacío o una tarjeta por cobro. Solo dibuja lo que recibe
// (sin servicios ni efectos), así cada estado se puede probar con un render estático. accionesDe(cobro) pone los botones de la fila.
export default function TablaCobros({ cargando, mensajeError, onReintentar, cobros, accionesDe }) {
  if (cargando) {
    return (
      <div className="flex items-start gap-3 rounded-lg border bg-surface p-4">
        <Icon name="clock" className="mt-0.5 h-5 w-5 shrink-0 text-secondary" />
        <p role="status" className="min-w-0 break-words text-body">Cargando los cobros…</p>
      </div>
    )
  }
  if (mensajeError) {
    return (
      <div role="alert" className="flex items-start gap-3 rounded-lg border bg-surface p-4">
        <Icon name="alert" className="mt-0.5 h-5 w-5 shrink-0 text-secondary" />
        <div className="flex min-w-0 flex-1 flex-col gap-3 break-words text-body">
          <p>{mensajeError}</p>
          {onReintentar && <Button variant="secondary" onClick={onReintentar} className="sm:self-start">Reintentar</Button>}
        </div>
      </div>
    )
  }
  if (cobros.length === 0) {
    return (
      <div className="flex items-start gap-3 rounded-lg border bg-surface p-4">
        <Icon name="info" className="mt-0.5 h-5 w-5 shrink-0 text-secondary" />
        <p className="min-w-0 break-words text-body">No hay cobros con estos filtros.</p>
      </div>
    )
  }
  return (
    <ul className="grid grid-cols-1 gap-4 md:grid-cols-2">
      {cobros.map((c) => (
        <Card as="li" key={c.id} className="flex flex-col gap-3">
          <div className="flex flex-wrap items-start justify-between gap-2">
            <div className="min-w-0">
              <h2 className="break-words text-body-medium">{c.nombreDeportista || 'Deportista'}</h2>
              <p className="break-words text-small text-secondary">{ETIQUETAS_TIPO[c.tipo] ?? c.tipo} · {c.concepto}</p>
              <p className="break-words text-small text-secondary">Vence: {fechaLargaConAnio(c.fechaVencimiento)}</p>
              {c.fechaPago && <p className="break-words text-small text-secondary">Pagado el {fechaLargaConAnio(c.fechaPago)}{c.metodo ? ` · ${c.metodo}` : ''}</p>}
            </div>
            <EstadoPago estado={c.estado} />
          </div>
          <p className="text-h3">{formatearCOP(c.monto)}</p>
          {c.iniciadoEnWompi && c.estadoApi !== 'APROBADO' && (
            <p className="flex items-start gap-2 text-small text-secondary">
              <Icon name="info" className="mt-0.5 h-4 w-4 shrink-0" />
              <span className="min-w-0 break-words">Iniciado en Wompi: si el pago ya se hizo y no aparece, usa Conciliar.</span>
            </p>
          )}
          {c.notaInterna && <Badge tone="neutral">Nota: {c.notaInterna}</Badge>}
          {accionesDe && <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">{accionesDe(c)}</div>}
        </Card>
      ))}
    </ul>
  )
}
