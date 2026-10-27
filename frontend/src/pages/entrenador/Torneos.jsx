import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'

const torneos = [
  { nombre: 'Copa Prado 2026 · Medellín', detalle: '8 nov. · Sub-10', estado: 'Convocatoria', tone: 'warning' },
  { nombre: 'Liga Antioquia · Envigado', detalle: '15 nov. · Sub-12', estado: 'Confirmado', tone: 'success' },
]
const convocados = [
  ['Santiago Ríos', 'Convocado', 'success'], ['Juan Pablo Cano', 'Convocado', 'success'],
  ['Samuel Londoño', 'Convocado', 'success'], ['Mateo Vélez', 'No disponible', 'danger'],
  ['Tomás Giraldo', 'Convocado', 'success'],
]

export default function Torneos() {
  return (
    <Page title="Torneos" subtitle="Competencias y convocatorias de tus categorías">
      <ul className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {torneos.map((t) => (
          <Card as="li" key={t.nombre} className="flex flex-col gap-3">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div className="min-w-0">
                <h2 className="break-words text-h3">{t.nombre}</h2>
                <p className="text-body text-secondary">{t.detalle}</p>
              </div>
              <Badge tone={t.tone}>{t.estado}</Badge>
            </div>
            <Button variant="secondary">Ver</Button>
          </Card>
        ))}
      </ul>
      <Card aria-labelledby="conv-titulo" className="flex flex-col gap-3">
        <div>
          <h2 id="conv-titulo" className="text-h3">Convocatoria · Copa Prado</h2>
          <p className="text-body text-secondary">Sub-10 · 8 nov. · 14 de 18 convocados</p>
        </div>
        <ul className="flex flex-col gap-3">
          {convocados.map(([nombre, estado, tone]) => (
            <li key={nombre} className="flex flex-wrap items-center justify-between gap-2">
              <span className="break-words text-body-medium">{nombre}</span>
              <Badge tone={tone}>{estado}</Badge>
            </li>
          ))}
        </ul>
        <Button>Enviar convocatoria por correo</Button>
      </Card>
    </Page>
  )
}
