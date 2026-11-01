import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'
import Avatar from '../../components/ui/Avatar.jsx'

const pendientes = [
  { concepto: 'Uniforme (2.º pago)', detalle: '$ 90.000 · venció 10 oct.', estado: 'Vencido', tone: 'danger' },
  { concepto: 'Mensualidad octubre', detalle: '$ 90.000 · vence 30 oct.', estado: 'Pendiente', tone: 'warning' },
]

export default function Inicio() {
  return (
    <Page title="Hola, María" subtitle="Resumen de tus jugadores">
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <Card aria-label="Santiago Ríos" className="flex flex-col gap-3">
          <div className="flex items-center gap-3">
            <Avatar name="Santiago Ríos" />
            <div className="min-w-0">
              <h2 className="break-words text-h3">Santiago Ríos</h2>
              <p className="text-small text-secondary">Semillero · Prado FC</p>
            </div>
          </div>
          <div className="flex flex-wrap gap-2">
            <Badge tone="warning">Pago pendiente</Badge>
            <Badge tone="success">Asistencia 90 %</Badge>
          </div>
          <p className="text-body text-secondary">Sábado 8:30 a. m. · Horizontes</p>
          <Button to="/acudiente/pagos">Pagar ahora</Button>
        </Card>
        <Card aria-labelledby="pendientes-titulo" className="flex flex-col gap-3">
          <h2 id="pendientes-titulo" className="text-h3">Pendientes</h2>
          <ul className="flex flex-col gap-3">
            {pendientes.map((p) => (
              <li key={p.concepto} className="flex flex-wrap items-center justify-between gap-2">
                <div className="min-w-0">
                  <p className="break-words text-body-medium">{p.concepto}</p>
                  <p className="text-small text-secondary">{p.detalle}</p>
                </div>
                <Badge tone={p.tone}>{p.estado}</Badge>
              </li>
            ))}
          </ul>
        </Card>
      </div>
    </Page>
  )
}
