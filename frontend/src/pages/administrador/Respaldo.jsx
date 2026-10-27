import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'

const historial = [
  ['25 oct. · 2:00 a. m.', '48 MB'], ['24 oct. · 2:00 a. m.', '48 MB'], ['23 oct. · 2:00 a. m.', '47 MB'],
]

export default function Respaldo() {
  return (
    <Page title="Respaldo" subtitle="Copias de seguridad de la base de datos">
      <Card aria-labelledby="ultimo-titulo" className="flex flex-col gap-3">
        <h2 id="ultimo-titulo" className="text-label uppercase text-secondary">Último respaldo</h2>
        <p className="text-kpi">Hoy · 2:00 a. m.</p>
        <p className="text-body text-secondary">Automático diario</p>
        <div><Badge tone="success">Correcto</Badge></div>
        <Button>Generar respaldo ahora</Button>
      </Card>
      <Card aria-labelledby="historial-titulo" className="flex flex-col gap-3">
        <h2 id="historial-titulo" className="text-h3">Historial</h2>
        <ul className="flex flex-col gap-3">
          {historial.map(([cuando, peso]) => (
            <li key={cuando} className="flex flex-wrap items-center justify-between gap-2">
              <div className="min-w-0">
                <p className="break-words text-body-medium">{cuando}</p>
                <p className="text-small text-secondary">{peso}</p>
              </div>
              <Badge tone="success">Correcto</Badge>
            </li>
          ))}
        </ul>
        <Button variant="secondary">Restaurar desde un respaldo</Button>
      </Card>
    </Page>
  )
}
