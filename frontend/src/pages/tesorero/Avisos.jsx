import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'

const avisos = [
  { titulo: 'Pago recibido', etiqueta: 'Pago', tone: 'success', detalle: 'Samuel Londoño · $ 90.000 · Mensualidad octubre', cuando: 'Hace 1 h' },
  { titulo: 'Pago vencido', etiqueta: 'Mora', tone: 'danger', detalle: 'Mateo Vélez · Mensualidad octubre · venció el 15 oct.', cuando: 'Hace 3 h' },
  { titulo: 'Comprobante enviado', etiqueta: 'Info', tone: 'neutral', detalle: 'Juan Pablo Cano · Inscripción 2026', cuando: 'Ayer' },
  { titulo: 'Meta de recaudo', etiqueta: 'Meta', tone: 'success', detalle: 'Octubre alcanzó el 89 % de la meta', cuando: 'Hace 2 días' },
]

export default function Avisos() {
  return (
    <Page title="Avisos" subtitle="Novedades de pagos y cartera">
      <ul className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {avisos.map((a) => (
          <Card as="li" key={a.titulo} className="flex flex-col gap-1">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <h2 className="min-w-0 break-words text-body-medium">{a.titulo}</h2>
              <Badge tone={a.tone}>{a.etiqueta}</Badge>
            </div>
            <p className="break-words text-body text-secondary">{a.detalle}</p>
            <p className="text-small text-secondary">{a.cuando}</p>
          </Card>
        ))}
      </ul>
    </Page>
  )
}
