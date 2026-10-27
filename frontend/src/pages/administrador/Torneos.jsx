import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'

const torneos = [
  { nombre: 'Copa Prado 2026 · Medellín', fecha: '8 nov. 2026', estado: 'Convocatoria', tone: 'warning', delegadas: ['Sub-10 → Carlos Díaz', 'Sub-12 → Andrés Mora'] },
  { nombre: 'Liga Antioquia · Envigado', fecha: '15 nov. 2026', estado: 'Confirmado', tone: 'success', delegadas: ['Sub-12 → Andrés Mora'] },
]

export default function Torneos() {
  return (
    <Page title="Torneos" subtitle="Solo administración crea torneos y delega categorías">
      <Button className="md:self-start">Crear torneo</Button>
      <ul className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {torneos.map((t) => (
          <Card as="li" key={t.nombre} className="flex flex-col gap-3">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div className="min-w-0">
                <h2 className="break-words text-h3">{t.nombre}</h2>
                <p className="text-body text-secondary">{t.fecha}</p>
              </div>
              <Badge tone={t.tone}>{t.estado}</Badge>
            </div>
            <div>
              <h3 className="text-label uppercase text-secondary">Categorías delegadas</h3>
              <ul className="mt-1 flex flex-col gap-1">
                {t.delegadas.map((d) => <li key={d} className="break-words text-body-medium">{d}</li>)}
              </ul>
            </div>
            <Button variant="secondary">Delegar categoría</Button>
          </Card>
        ))}
      </ul>
    </Page>
  )
}
