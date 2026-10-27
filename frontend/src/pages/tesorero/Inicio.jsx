import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'

const recaudo = [['May', 15.2], ['Jun', 16.8], ['Jul', 14.9], ['Ago', 17.5], ['Sep', 18.1], ['Oct', 18.4]]
const maximo = Math.max(...recaudo.map(([, v]) => v))
const ultimos = [
  ['Samuel Londoño', '$ 90.000'], ['Juan Pablo Cano', '$ 150.000'], ['Santiago Ríos', '$ 90.000'],
]

export default function Inicio() {
  return (
    <Page title="Hola, Laura" subtitle="Estado de la cartera · Octubre 2026">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Card className="flex flex-col gap-2">
          <h2 className="text-label uppercase text-secondary">Recaudo de octubre</h2>
          <p className="text-kpi">$ 18,4 M</p>
          <p className="text-small text-secondary">+1,6 % frente a septiembre</p>
          <div><Badge tone="success">En meta</Badge></div>
        </Card>
        <Card className="flex flex-col gap-2">
          <h2 className="text-label uppercase text-secondary">Cartera vencida</h2>
          <p className="text-kpi">$ 2,3 M</p>
          <p className="text-small text-secondary">14 pagos vencidos</p>
          <div><Badge tone="danger">Atención</Badge></div>
        </Card>
      </div>
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <Card aria-labelledby="recaudo-titulo" className="flex flex-col gap-3">
          <h2 id="recaudo-titulo" className="text-h3">Recaudo mensual (millones COP)</h2>
          {/* Los valores están escritos encima de cada barra, así el dato no depende solo de la altura */}
          <ol className="grid grid-cols-6 items-end gap-2">
            {recaudo.map(([mes, valor]) => (
              <li key={mes} className="flex flex-col items-center gap-1">
                <span className="text-small">{String(valor).replace('.', ',')}</span>
                <span
                  className="w-full rounded-t-md bg-brand-primary"
                  style={{ height: `${Math.round((valor / maximo) * 6)}rem` }}
                />
                <span className="text-small text-secondary">{mes}</span>
              </li>
            ))}
          </ol>
        </Card>
        <Card aria-labelledby="ultimos-titulo" className="flex flex-col gap-3">
          <h2 id="ultimos-titulo" className="text-h3">Últimos pagos recibidos</h2>
          <ul className="flex flex-col gap-3">
            {ultimos.map(([nombre, valor]) => (
              <li key={nombre} className="flex flex-wrap items-center justify-between gap-2">
                <div className="min-w-0">
                  <p className="break-words text-body-medium">{nombre}</p>
                  <p className="text-small text-secondary">{valor}</p>
                </div>
                <Badge tone="success">Pagado</Badge>
              </li>
            ))}
          </ul>
          <Button variant="secondary" to="/tesorero/pagos">Ver todos los pagos</Button>
        </Card>
      </div>
    </Page>
  )
}
