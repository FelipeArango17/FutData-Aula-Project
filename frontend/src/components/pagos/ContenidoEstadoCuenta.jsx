import Badge from '../ui/Badge.jsx'
import Button from '../ui/Button.jsx'
import Avatar from '../ui/Avatar.jsx'
import FilterChips from '../ui/FilterChips.jsx'
import Icon from '../Icon.jsx'
import ListaPagos from './ListaPagos.jsx'
import { FILTROS_ESTADO_CUENTA, cumpleFiltro } from '../../utils/pagosApi.js'
import { diaLargo } from '../../utils/fechas.js'
import { formatearCOP } from '../../utils/dinero.js'

// Cuerpo de la pantalla de pagos del acudiente: cargando, error, vacío o el estado de cuenta. Solo dibuja lo que recibe (sin
// efectos ni servicios), así cada estado se puede probar con un render estático.
// "viernes 31 de octubre de 2026"
const fechaLarga = (fecha) => `${diaLargo(fecha).toLowerCase()} de ${fecha.slice(0, 4)}`

function Mensaje({ icono = 'info', rol, children }) {
  return (
    <div role={rol} className="flex items-start gap-3 rounded-lg border bg-surface p-4">
      <Icon name={icono} className="mt-0.5 h-5 w-5 shrink-0 text-secondary" />
      <div className="flex min-w-0 flex-1 flex-col gap-3 break-words text-body">{children}</div>
    </div>
  )
}

// Estado general del deportista: vencido > pendiente > al día (texto + ícono)
function EstadoDeportista({ d }) {
  if (d.totalVencido > 0) return <Badge tone="danger">Vencido</Badge>
  if (d.totalPendiente > 0) return <Badge tone="warning">Pendiente</Badge>
  return <Badge tone="success">Al día</Badge>
}

const nombreDe = (deportista) => [deportista.nombre, deportista.apellido].filter(Boolean).join(' ')

/**
 * @param cargando      mientras llega el estado de cuenta
 * @param mensajeError  texto del error de carga (o null)
 * @param estado        { deportistas, totalPendiente, totalVencido }
 * @param accionesDe    (pago) => botones de la fila; con ella el texto "pago en línea próximamente" no aparece
 * @param confirmando   texto de "Estamos confirmando tu pago" mientras se espera el webhook (o null)
 */
export default function ContenidoEstadoCuenta({
  cargando, mensajeError, onReintentar, estado, filtro, onFiltro, conTabla, fotos = {}, accionesDe, confirmando = null,
}) {
  if (cargando) {
    return (
      <Mensaje icono="clock">
        <p role="status">Cargando tu estado de cuenta…</p>
      </Mensaje>
    )
  }
  if (mensajeError) {
    return (
      <Mensaje icono="alert" rol="alert">
        <p>{mensajeError}</p>
        {onReintentar && <Button variant="secondary" onClick={onReintentar} className="sm:self-start">Reintentar</Button>}
      </Mensaje>
    )
  }
  const deportistas = estado?.deportistas ?? []
  if (deportistas.length === 0) {
    return (
      <Mensaje>
        <p>Todavía no tienes deportistas asociados a tu cuenta, así que no hay pagos que mostrar.</p>
      </Mensaje>
    )
  }
  return (
    <>
      {confirmando && (
        <p role="status" className="flex items-start gap-2 rounded-md bg-warning p-3 text-body-medium text-warning">
          <Icon name="clock" className="mt-0.5 h-5 w-5 shrink-0" />
          <span className="min-w-0 break-words">{confirmando}</span>
        </p>
      )}
      <section aria-labelledby="titulo-resumen" className="flex flex-col gap-3 rounded-lg border bg-surface p-4">
        <h2 id="titulo-resumen" className="text-small font-normal text-secondary">Total pendiente</h2>
        <p className="break-words text-kpi">{formatearCOP(estado.totalPendiente)}</p>
        {estado.totalVencido > 0 ? (
          <p className="flex items-start gap-2 rounded-md bg-danger p-3 text-body-medium text-danger">
            <Icon name="alert" className="mt-0.5 h-5 w-5 shrink-0" />
            <span className="min-w-0 break-words">Tienes {formatearCOP(estado.totalVencido)} vencidos.</span>
          </p>
        ) : estado.totalPendiente > 0 ? (
          <p className="flex items-start gap-2 rounded-md bg-warning p-3 text-body-medium text-warning">
            <Icon name="clock" className="mt-0.5 h-5 w-5 shrink-0" />
            <span className="min-w-0 break-words">Tienes pagos pendientes, ninguno vencido.</span>
          </p>
        ) : (
          <p className="flex items-start gap-2 rounded-md bg-success p-3 text-body-medium text-success">
            <Icon name="check" className="mt-0.5 h-5 w-5 shrink-0" />
            <span className="min-w-0 break-words">Estás al día con todos tus pagos.</span>
          </p>
        )}
        {!accionesDe && <p className="break-words text-small text-secondary">El pago en línea estará disponible próximamente.</p>}
      </section>

      <FilterChips label="Estado del pago" options={FILTROS_ESTADO_CUENTA} value={filtro} onChange={onFiltro} />

      <ul className="grid grid-cols-1 gap-6">
        {deportistas.map((d) => {
          const nombre = nombreDe(d.deportista)
          const visibles = d.pagos.filter((p) => cumpleFiltro(filtro, p))
          return (
            <li key={d.deportista.id}>
              <section aria-labelledby={`dep-${d.deportista.id}`} className="flex flex-col gap-4 rounded-lg border bg-surface p-4">
                <div className="flex flex-wrap items-start gap-3">
                  <Avatar name={nombre} foto={fotos[d.deportista.id]} />
                  <div className="flex min-w-0 grow basis-[12rem] flex-col gap-1">
                    <h2 id={`dep-${d.deportista.id}`} className="break-words text-h3">{nombre}</h2>
                    <p className="break-words text-body-medium">Total pendiente: {formatearCOP(d.totalPendiente)}</p>
                    {d.pendienteDesde && <p className="break-words text-body text-secondary">Pendiente desde: {fechaLarga(d.pendienteDesde)}</p>}
                    {d.totalVencido > 0 && <p className="break-words text-body text-danger">Vencido: {formatearCOP(d.totalVencido)}</p>}
                    <div className="flex flex-wrap pt-1"><EstadoDeportista d={d} /></div>
                  </div>
                </div>
                {visibles.length === 0 ? (
                  <p className="text-body text-secondary">No hay pagos con este filtro.</p>
                ) : (
                  <ListaPagos pagos={visibles} nombre={nombre} conTabla={conTabla} fechaLarga={fechaLarga} accionesDe={accionesDe} />
                )}
              </section>
            </li>
          )
        })}
      </ul>
    </>
  )
}
