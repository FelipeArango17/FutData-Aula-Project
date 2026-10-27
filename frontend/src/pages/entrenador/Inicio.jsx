import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'

const semana = [
  { lugar: 'Sub-10 · Cancha 1', cuando: 'Hoy · 4:00 p. m.', estado: 'Sin asistencia', tone: 'warning' },
  { lugar: 'Sub-12 · Cancha 2', cuando: 'Hoy · 5:30 p. m.', estado: 'Sin asistencia', tone: 'warning' },
  { lugar: 'Sub-10 · Cancha 1', cuando: 'Mar 27 · 4:00 p. m.', estado: 'Programado', tone: 'neutral' },
  { lugar: 'Sub-12 · Cancha 2', cuando: 'Mié 28 · 5:30 p. m.', estado: 'Programado', tone: 'neutral' },
]
const faltas = [
  { nombre: 'Mateo Vélez', detalle: '6 faltas · 62 % asistencia', accion: 'Revisar', tone: 'danger' },
  { nombre: 'Tomás Giraldo', detalle: '4 faltas · 71 % asistencia', accion: 'Seguir', tone: 'warning' },
  { nombre: 'Nicolás Mejía', detalle: '3 faltas · 76 % asistencia', accion: 'Seguir', tone: 'warning' },
]

function Indicador({ titulo, valor, detalle }) {
  return (
    <Card className="flex flex-col gap-1">
      <h2 className="text-small text-secondary">{titulo}</h2>
      <p className="text-kpi">{valor}</p>
      <p className="break-words text-small text-secondary">{detalle}</p>
    </Card>
  )
}

export default function Inicio() {
  return (
    <Page title="Hola, Andrés" subtitle="Tus categorías: Sub-10 y Sub-12">
      <Button to="/entrenador/asistencia" className="md:self-start">Tomar asistencia</Button>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Indicador titulo="Jugadores Sub-10" valor="17" detalle="2 con documentos pendientes" />
        <Indicador titulo="Asistencia Sub-10" valor="88 %" detalle="Últimas 4 semanas" />
        <Indicador titulo="Entrenamientos hoy" valor="1" detalle="4:00 p. m. · Cancha 1" />
        <Indicador titulo="Convocados Copa Prado" valor="14 / 18" detalle="Faltan 4 por confirmar" />
      </div>
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <Card aria-labelledby="semana-titulo" className="flex flex-col gap-3">
          <h2 id="semana-titulo" className="text-h3">Entrenamientos de la semana</h2>
          <ul className="flex flex-col gap-3">
            {semana.map((s) => (
              <li key={s.lugar + s.cuando} className="flex flex-wrap items-center justify-between gap-2">
                <div className="min-w-0">
                  <p className="break-words text-body-medium">{s.lugar}</p>
                  <p className="text-small text-secondary">{s.cuando}</p>
                </div>
                <Badge tone={s.tone}>{s.estado}</Badge>
              </li>
            ))}
          </ul>
        </Card>
        <Card aria-labelledby="faltas-titulo" className="flex flex-col gap-3">
          <h2 id="faltas-titulo" className="text-h3">Jugadores con más faltas</h2>
          <ul className="flex flex-col gap-3">
            {faltas.map((f) => (
              <li key={f.nombre} className="flex flex-wrap items-center justify-between gap-2">
                <div className="min-w-0">
                  <p className="break-words text-body-medium">{f.nombre}</p>
                  <p className="text-small text-secondary">{f.detalle}</p>
                </div>
                <Badge tone={f.tone}>{f.accion}</Badge>
              </li>
            ))}
          </ul>
        </Card>
      </div>
    </Page>
  )
}
