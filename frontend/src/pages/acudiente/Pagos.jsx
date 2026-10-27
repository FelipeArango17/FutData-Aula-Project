import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'

const pagos = [
  { concepto: 'Mensualidad octubre', detalle: 'Vence 30 oct. · $ 90.000', estado: 'Pendiente', tone: 'warning', accion: 'Pagar' },
  { concepto: 'Uniforme (2.º pago)', detalle: 'Venció 10 oct. · $ 90.000', estado: 'Vencido', tone: 'danger', accion: 'Pagar' },
  { concepto: 'Mensualidad septiembre', detalle: 'Pagado 15 sep. · $ 90.000', estado: 'Pagado', tone: 'success', accion: 'Comprobante' },
]

export default function Pagos() {
  return (
    <Page title="Pagos" subtitle="Estado de cuenta de Santiago">
      <Card aria-labelledby="saldo-titulo">
        <h2 id="saldo-titulo" className="text-small text-secondary">Saldo pendiente</h2>
        <p className="text-kpi">$ 180.000</p>
        <p className="text-body text-secondary">2 conceptos</p>
      </Card>
      <ul className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {pagos.map((p) => (
          <Card as="li" key={p.concepto} className="flex flex-col gap-3">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div className="min-w-0">
                <h2 className="break-words text-h3">{p.concepto}</h2>
                <p className="text-body text-secondary">{p.detalle}</p>
              </div>
              <Badge tone={p.tone}>{p.estado}</Badge>
            </div>
            <Button variant={p.tone === 'success' ? 'secondary' : 'primary'}>{p.accion}</Button>
          </Card>
        ))}
      </ul>
    </Page>
  )
}
