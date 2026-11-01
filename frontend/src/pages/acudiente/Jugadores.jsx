import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'
import Avatar from '../../components/ui/Avatar.jsx'

const jugadores = [
  {
    id: 'santiago', nombre: 'Santiago Ríos', categoria: 'Cachorros · Prado FC', asistencia: 'Asistencia 90 %',
    pago: { tone: 'warning', texto: 'Pago pendiente' }, docs: { tone: 'warning', texto: 'Faltan documentos' },
    proximo: 'Sábado 9:00 a. m. · Horizontes', accion: { texto: 'Pagar ahora', to: '/acudiente/pagos' },
  },
  {
    id: 'valeria', nombre: 'Valeria Ríos', categoria: 'Semillero · Prado FC', asistencia: 'Asistencia 94 %',
    pago: { tone: 'success', texto: 'Al día' }, docs: { tone: 'success', texto: 'Documentos al día' },
    proximo: 'Sábado 8:30 a. m. · Horizontes', accion: { texto: 'Ver pagos', to: '/acudiente/pagos' },
  },
]

export default function Jugadores() {
  return (
    <Page title="Mis jugadores" subtitle="2 jugadores inscritos y aprobados">
      <ul className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {jugadores.map((j) => (
          <Card as="li" key={j.id} className="flex flex-col gap-3">
            <div className="flex items-center gap-3">
              <Avatar name={j.nombre} />
              <div className="min-w-0">
                <h2 className="break-words text-h3">{j.nombre}</h2>
                <p className="text-small text-secondary">{j.categoria}</p>
              </div>
            </div>
            <div className="flex flex-wrap gap-2">
              <Badge tone={j.pago.tone}>{j.pago.texto}</Badge>
              <Badge tone="success">{j.asistencia}</Badge>
              <Badge tone={j.docs.tone}>{j.docs.texto}</Badge>
            </div>
            <p className="text-body text-secondary">{j.proximo}</p>
            <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
              <Button variant="secondary" to={`/acudiente/jugadores/${j.id}`}>Ver perfil</Button>
              <Button to={j.accion.to}>{j.accion.texto}</Button>
            </div>
          </Card>
        ))}
      </ul>
      <p className="text-body text-secondary">
        ¿Hay otro jugador en tu familia? La inscripción se hace en secretaría.
      </p>
    </Page>
  )
}
