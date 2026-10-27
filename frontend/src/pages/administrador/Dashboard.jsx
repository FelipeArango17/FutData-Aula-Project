import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'

const indicadores = [
  { titulo: 'Deportistas activos', valor: '86', detalle: '4 inscripciones nuevas este mes', badge: ['success', '+4'] },
  { titulo: 'Asistencia semanal', valor: '86 %', detalle: 'Meta del club: 85 %', badge: ['success', 'En meta'] },
  { titulo: 'Cartera vencida', valor: '$ 2,3 M', detalle: '14 pagos vencidos', badge: ['danger', 'Atención'] },
  { titulo: 'Documentos pendientes', valor: '9', detalle: 'Fichas médicas y registros civiles', badge: ['warning', 'Revisar'] },
]

export default function Dashboard() {
  return (
    <Page title="Hola, Carlos" subtitle="Resumen del club · Octubre 2026">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {indicadores.map((i) => (
          <Card key={i.titulo} className="flex flex-col gap-2">
            <h2 className="text-label uppercase text-secondary">{i.titulo}</h2>
            <p className="text-kpi">{i.valor}</p>
            <p className="text-small text-secondary">{i.detalle}</p>
            <div><Badge tone={i.badge[0]}>{i.badge[1]}</Badge></div>
          </Card>
        ))}
      </div>
      <Card aria-labelledby="accesos-titulo" className="flex flex-col gap-3">
        <h2 id="accesos-titulo" className="text-h3">Accesos rápidos</h2>
        <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
          <Button to="/administrador/deportistas/inscribir">Inscribir jugador</Button>
          <Button variant="secondary" to="/administrador/torneos">Crear torneo</Button>
        </div>
      </Card>
    </Page>
  )
}
