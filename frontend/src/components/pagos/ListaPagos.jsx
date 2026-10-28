import Badge from '../ui/Badge.jsx'
import Card from '../ui/Card.jsx'
import { formatearCOP } from '../../utils/dinero.js'

// Estado del pago siempre con texto + ícono + color (Badge). Pagado / Pendiente / Vencido.
const TONO_ESTADO = { pagado: 'success', pendiente: 'warning', vencido: 'danger' }
const TEXTO_ESTADO = { pagado: 'Pagado', pendiente: 'Pendiente', vencido: 'Vencido' }

export function EstadoPago({ estado }) {
  return <Badge tone={TONO_ESTADO[estado]}>{TEXTO_ESTADO[estado]}</Badge>
}

// Historial de pagos de un deportista. conTabla (escritorio, cuando el contenedor mide ≥ 40 rem): tabla accesible;
// si no: tarjetas apiladas. fechaLarga convierte "AAAA-MM-DD" en "viernes 31 de octubre de 2026".
export default function ListaPagos({ pagos, nombre, conTabla, fechaLarga }) {
  if (conTabla) {
    return (
      <table className="w-full border-collapse text-left text-body">
        <caption className="sr-only">Pagos de {nombre}</caption>
        <thead>
          <tr className="border-b bg-app">
            <th scope="col" className="p-3 text-body-medium">Concepto</th>
            <th scope="col" className="p-3 text-body-medium">Vencimiento</th>
            <th scope="col" className="p-3 text-body-medium">Fecha de pago</th>
            <th scope="col" className="p-3 text-right text-body-medium">Monto</th>
            <th scope="col" className="p-3 text-body-medium">Estado</th>
          </tr>
        </thead>
        <tbody>
          {pagos.map((p) => (
            <tr key={p.id} className="border-b last:border-b-0">
              <th scope="row" className="break-words p-3 text-left font-normal">{p.concepto}</th>
              <td className="break-words p-3">{fechaLarga(p.fechaVencimiento)}</td>
              <td className="break-words p-3">{p.fechaPago ? fechaLarga(p.fechaPago) : 'Sin pagar'}</td>
              <td className="break-words p-3 text-right">{formatearCOP(p.monto)}</td>
              <td className="p-3"><EstadoPago estado={p.estado} /></td>
            </tr>
          ))}
        </tbody>
      </table>
    )
  }
  return (
    <ul className="grid grid-cols-1 gap-3" aria-label={`Pagos de ${nombre}`}>
      {pagos.map((p) => (
        <Card as="li" key={p.id} className="flex flex-col gap-3 bg-app">
          <div className="flex flex-wrap items-start justify-between gap-2">
            <p className="min-w-0 grow basis-[10rem] break-words text-body-medium">{p.concepto}</p>
            <EstadoPago estado={p.estado} />
          </div>
          <dl className="grid grid-cols-1 gap-1 text-body">
            <div className="flex flex-wrap gap-x-2">
              <dt className="text-secondary">Monto:</dt>
              <dd className="break-words text-body-medium">{formatearCOP(p.monto)}</dd>
            </div>
            <div className="flex flex-wrap gap-x-2">
              <dt className="text-secondary">Vencimiento:</dt>
              <dd className="break-words">{fechaLarga(p.fechaVencimiento)}</dd>
            </div>
            <div className="flex flex-wrap gap-x-2">
              <dt className="text-secondary">Fecha de pago:</dt>
              <dd className="break-words">{p.fechaPago ? fechaLarga(p.fechaPago) : 'Sin pagar'}</dd>
            </div>
          </dl>
        </Card>
      ))}
    </ul>
  )
}
