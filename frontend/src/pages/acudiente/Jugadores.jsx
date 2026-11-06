import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'
import Avatar from '../../components/ui/Avatar.jsx'
import EstadoDeCarga from '../../components/pagos/EstadoDeCarga.jsx'
import useCarga from '../../hooks/useCarga.js'
import { acudientesPorApi, listarMisJugadores } from '../../services/acudientesService.js'
import { textoCategoriaConGrupo } from '../../utils/categorias.js'
import { textoEdad } from '../../utils/entrenadorApi.js'
import {
  ETIQUETA_INSCRIPCION, MENSAJE_SIN_JUGADORES, TONO_INSCRIPCION, mensajeDeErrorAcudiente, nombreCompleto, textoJugadores,
} from '../../utils/acudientesApi.js'

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

// Mis jugadores con la API (módulo "acudientes"): solo lo que entrega el servidor (nombre, edad, categoría, parentesco e inscripción).
// Asistencia, pagos y próximo entrenamiento no vienen en esta respuesta y por eso no se muestran.
function TarjetaJugador({ jugador }) {
  const nombre = nombreCompleto(jugador)
  const detalle = [jugador.edad === null ? null : textoEdad(jugador.edad), jugador.categoria && textoCategoriaConGrupo(jugador.categoria, jugador.grupoEntrenamiento)].filter(Boolean).join(' · ')
  return (
    <Card as="li" className="flex flex-col gap-3">
      <div className="flex items-center gap-3">
        <Avatar name={nombre} foto={jugador.foto} className="h-12 w-12" />
        <div className="min-w-0">
          <h2 className="break-words text-h3">{nombre}</h2>
          {detalle && <p className="break-words text-small text-secondary">{detalle}</p>}
          {jugador.parentesco && <p className="break-words text-small text-secondary">Parentesco: {jugador.parentesco}</p>}
        </div>
      </div>
      <div className="flex flex-wrap gap-2">
        <Badge tone={TONO_INSCRIPCION[jugador.estadoInscripcion]}>Inscripción: {ETIQUETA_INSCRIPCION[jugador.estadoInscripcion]}</Badge>
        {!jugador.activo && <Badge>Inactivo</Badge>}
      </div>
      <Button variant="secondary" to={`/acudiente/jugadores/${encodeURIComponent(jugador.id)}`} className="sm:self-start">Ver ficha</Button>
    </Card>
  )
}

// Vista pura (sin red): recibe el estado de la carga. La usan el contenedor de abajo y las pruebas.
export function VistaJugadores({ cargando, jugadores, error, onReintentar }) {
  const subtitulo = jugadores && jugadores.length > 0 ? `${textoJugadores(jugadores.length)} vinculados a tu cuenta` : 'Los jugadores vinculados a tu cuenta'
  return (
    <Page title="Mis jugadores" subtitle={subtitulo}>
      <EstadoDeCarga
        cargando={cargando}
        mensajeError={error ? mensajeDeErrorAcudiente(error, 'No se pudieron cargar tus jugadores.') : null}
        onReintentar={onReintentar}
        vacio={jugadores && jugadores.length === 0 ? MENSAJE_SIN_JUGADORES : null}
        texto="Cargando tus jugadores…"
      >
        {jugadores && (
          <>
            <ul aria-label="Lista de jugadores" className="grid grid-cols-1 gap-4 md:grid-cols-2">
              {jugadores.map((j) => <TarjetaJugador key={j.id} jugador={j} />)}
            </ul>
            <p className="text-body text-secondary">¿Hay otro jugador en tu familia? La inscripción se hace en secretaría.</p>
          </>
        )}
      </EstadoDeCarga>
    </Page>
  )
}

function JugadoresApi() {
  const { cargando, datos, error, recargar } = useCarga(listarMisJugadores)
  return <VistaJugadores cargando={cargando} jugadores={datos} error={error} onReintentar={recargar} />
}

function JugadoresDemo() {
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

export default function Jugadores() {
  return acudientesPorApi() ? <JugadoresApi /> : <JugadoresDemo />
}
