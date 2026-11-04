import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Button from '../../components/ui/Button.jsx'
import EstadoDeCarga from '../../components/pagos/EstadoDeCarga.jsx'
import { categoriaDeSesion, rangoDeSesion } from '../../components/horario/formatoSesion.js'
import useCarga from '../../hooks/useCarga.js'
import { useAuth } from '../../context/AuthContext.js'
import { cargarInicioEntrenador, inicioEntrenadorPorApi } from '../../services/entrenadorService.js'
import { autenticacionReal } from '../../services/supabaseClient.js'
import { modulosConApi } from '../../services/usarApi.js'
import { mensajeDeErrorEntrenador, textoEntrenamientos, textoJugadores } from '../../utils/entrenadorApi.js'
import { diaLargo } from '../../utils/fechas.js'
import { opcionVisible } from '../../utils/menusVisibles.js'
import { saludoDe } from '../../utils/saludo.js'

// Inicio del entrenador: sus categorías, cuántos jugadores activos tiene en cada una y sus entrenamientos de hoy y de la semana.
// Todo sale de la API (usuarios/yo, deportistas y horario-club); nada es de ejemplo.
const SIN_VINCULO = 'Tu cuenta todavía no está vinculada a un registro de entrenador. Pide al administrador que la revise.'
const SIN_CATEGORIAS = 'Todavía no tienes categorías asignadas. Cuando el administrador te asigne una, verás aquí tus jugadores y entrenamientos.'

function Sesiones({ id, titulo, sesiones, vacio, conDia }) {
  return (
    <Card aria-labelledby={id} className="flex flex-col gap-3">
      <h2 id={id} className="text-h3">{titulo}</h2>
      {sesiones.length === 0 ? (
        <p className="text-body text-secondary">{vacio}</p>
      ) : (
        <ul className="flex flex-col gap-3">
          {sesiones.map((s) => (
            <li key={s.clave} className="min-w-0">
              <p className="break-words text-body-medium">{categoriaDeSesion(s)}</p>
              <p className="break-words text-small text-secondary">{conDia ? `${diaLargo(s.fecha)} · ` : ''}{rangoDeSesion(s)} · {s.sede}</p>
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}

function Categorias({ jugadores, verJugadores }) {
  return (
    <Card aria-labelledby="categorias-titulo" className="flex flex-col gap-3">
      <h2 id="categorias-titulo" className="text-h3">Mis categorías</h2>
      <ul className="flex flex-col gap-3">
        {jugadores.categorias.map((c) => (
          <li key={c.idCategoria} className="flex flex-wrap items-baseline justify-between gap-2">
            <span className="break-words text-body-medium">{c.nombre}</span>
            <span className="text-small text-secondary">{textoJugadores(c.jugadores.length)}</span>
          </li>
        ))}
      </ul>
      {verJugadores && <div><Button variant="secondary" to="/entrenador/jugadores">Ver mis jugadores</Button></div>}
    </Card>
  )
}

function Indicador({ titulo, valor, detalle }) {
  return (
    <Card className="flex flex-col gap-1">
      <h2 className="text-small text-secondary">{titulo}</h2>
      <p className="text-kpi">{valor}</p>
      <p className="break-words text-small text-secondary">{detalle}</p>
    </Card>
  )
}

function Resumen({ datos, recargar }) {
  const modulos = modulosConApi()
  const visible = (modulo) => opcionVisible({ modulo }, { autenticacionReal, modulos })
  const { jugadores, horario } = datos
  const total = jugadores.error ? null : jugadores.jugadores.length

  return (
    <>
      {visible('asistencia') && <Button to="/entrenador/asistencia" className="md:self-start">Tomar asistencia</Button>}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Indicador
          titulo="Jugadores activos" valor={total ?? '—'}
          detalle={total === null ? 'No se pudo cargar' : `En ${datos.perfil.categorias.length === 1 ? 'tu categoría' : `tus ${datos.perfil.categorias.length} categorías`}`}
        />
        <Indicador
          titulo="Entrenamientos hoy" valor={horario.error ? '—' : horario.hoy.length}
          detalle={horario.error ? 'No se pudo cargar' : `${textoEntrenamientos(horario.semana.length)} más esta semana`}
        />
      </div>
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <EstadoDeCarga mensajeError={jugadores.error ? mensajeDeErrorEntrenador(jugadores.error, 'No se pudieron cargar tus jugadores.') : null} onReintentar={recargar}>
          <Categorias jugadores={jugadores} verJugadores={visible('misjugadores')} />
        </EstadoDeCarga>
        <EstadoDeCarga mensajeError={horario.error ? mensajeDeErrorEntrenador(horario.error, 'No se pudo cargar tu horario.') : null} onReintentar={recargar}>
          <Sesiones id="hoy-titulo" titulo="Entrenamientos de hoy" sesiones={horario.hoy} vacio="No tienes entrenamientos hoy." />
        </EstadoDeCarga>
      </div>
      {!horario.error && (
        <Sesiones id="semana-titulo" titulo="Resto de la semana" sesiones={horario.semana} vacio="No tienes más entrenamientos esta semana." conDia />
      )}
    </>
  )
}

// Vista pura (sin red): recibe el estado de la carga. La usan el contenedor de abajo y las pruebas.
export function VistaInicio({ usuario, conectado, cargando, datos, error, onReintentar }) {
  let vacio = null
  if (!conectado) vacio = 'Este resumen se calcula con datos reales y el módulo todavía no está conectado a la API.'
  else if (datos && !datos.perfil) vacio = SIN_VINCULO
  else if (datos && datos.perfil.categorias.length === 0) vacio = SIN_CATEGORIAS

  return (
    <Page title={saludoDe(usuario)} subtitle="Resumen de tus categorías">
      <EstadoDeCarga
        cargando={cargando}
        mensajeError={error ? mensajeDeErrorEntrenador(error, 'No se pudo cargar tu resumen.') : null}
        onReintentar={onReintentar}
        vacio={vacio}
        texto="Cargando tu resumen…"
      >
        {datos && <Resumen datos={datos} recargar={onReintentar} />}
      </EstadoDeCarga>
    </Page>
  )
}

export default function Inicio() {
  const { usuario } = useAuth()
  const conectado = inicioEntrenadorPorApi()
  const { cargando, datos, error, recargar } = useCarga(cargarInicioEntrenador, conectado)
  return <VistaInicio usuario={usuario} conectado={conectado} cargando={cargando} datos={datos} error={error} onReintentar={recargar} />
}
