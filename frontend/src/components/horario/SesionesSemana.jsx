import Badge from '../ui/Badge.jsx'
import Button from '../ui/Button.jsx'
import Card from '../ui/Card.jsx'
import { diaLargo, formatHora } from '../../utils/fechas.js'
import { fechaConMes } from '../../utils/semana.js'
import { categoriaDeSesion, entrenadorDeSesion, rangoDeSesion } from './formatoSesion.js'

// La semana del club, de lunes a sábado, con fechas. Dos presentaciones con los mismos datos:
// - cuadricula (escritorio): días como columnas y una fila por cada hora de inicio;
// - lista (móvil, tablet vertical o letra grande): una sección por día, sin scroll horizontal.
// Cada sesión muestra categoría (con grupo), hora, sede y entrenador ("Por turnos" si no hay). El estado se dice con TEXTO, no solo
// con color: "Cancelado" (texto tachado y atenuado, con el motivo si existe) y "Extra".
// props: dias ("AAAA-MM-DD" de lunes a sábado), sesiones, hoy, modo ('cuadricula' | 'lista'),
//        accionesDe(sesion) -> { cancelar, reactivar, rotar, editarExtra, eliminarExtra } (sin ella la vista es de solo lectura),
//        onCancelar, onReactivar, onRotar, onEditarExtra, onEliminarExtra (reciben la sesión)
const descripcion = (s) => `${categoriaDeSesion(s)}, ${diaLargo(s.fecha).toLowerCase()}, ${rangoDeSesion(s)}`

function Estado({ sesion }) {
  if (sesion.estado !== 'CANCELADO' && sesion.tipo !== 'EXTRA') return null
  return (
    <span className="flex flex-wrap items-center gap-1">
      {sesion.estado === 'CANCELADO' && <Badge tone="danger">Cancelado</Badge>}
      {sesion.tipo === 'EXTRA' && <Badge tone="warning">Extra</Badge>}
    </span>
  )
}

function Datos({ sesion, grande = false }) {
  const cancelada = sesion.estado === 'CANCELADO'
  const tachado = cancelada ? 'line-through opacity-70' : ''
  return (
    <>
      <Estado sesion={sesion} />
      <span className={`block break-words font-semibold ${grande ? 'text-body-medium' : ''} ${tachado}`}>{categoriaDeSesion(sesion)}</span>
      <span className={`block break-words ${tachado}`}>{rangoDeSesion(sesion)}</span>
      <span className={`block break-words ${tachado}`}>{sesion.sede}</span>
      <span className={`block break-words ${tachado}`}>Entrenador: {entrenadorDeSesion(sesion)}</span>
      {sesion.motivo && <span className="block break-words">Motivo: {sesion.motivo}</span>}
    </>
  )
}

function Acciones({ sesion, acciones, manejadores }) {
  const d = descripcion(sesion)
  const { onCancelar, onReactivar, onRotar, onEditarExtra, onEliminarExtra } = manejadores
  if (!acciones || !Object.values(acciones).some(Boolean)) return null
  return (
    <div className="flex flex-wrap gap-2">
      {acciones.cancelar && <Button variant="secondary" onClick={() => onCancelar(sesion)} aria-label={`Cancelar entrenamiento: ${d}`} className="grow">Cancelar</Button>}
      {acciones.reactivar && <Button variant="secondary" onClick={() => onReactivar(sesion)} aria-label={`Reactivar entrenamiento: ${d}`} className="grow">Reactivar</Button>}
      {acciones.rotar && <Button variant="secondary" onClick={() => onRotar(sesion)} aria-label={`Cambiar entrenador de todas las semanas: ${d}`} className="grow">Cambiar entrenador (todas las semanas)</Button>}
      {acciones.editarExtra && <Button variant="secondary" onClick={() => onEditarExtra(sesion)} aria-label={`Editar entrenamiento extra: ${d}`} className="grow">Editar</Button>}
      {acciones.eliminarExtra && <Button variant="danger" onClick={() => onEliminarExtra(sesion)} aria-label={`Eliminar entrenamiento extra: ${d}`} className="grow">Eliminar</Button>}
    </div>
  )
}

function Cuadricula({ dias, sesiones, hoy, accionesDe, manejadores }) {
  const horas = [...new Set(sesiones.map((s) => s.hora))].sort()
  return (
    <div className="overflow-hidden rounded-lg border bg-surface">
      <table className="w-full table-fixed border-collapse text-small">
        <caption className="sr-only">Horario de la semana, por día y hora de inicio</caption>
        <thead>
          <tr>
            <th scope="col" className="w-20 border-b p-2 text-left font-semibold"><span className="sr-only">Hora de inicio</span></th>
            {dias.map((dia) => (
              <th key={dia} scope="col" aria-current={dia === hoy ? 'date' : undefined} className={`border-b border-l p-2 text-left font-semibold ${dia === hoy ? 'bg-app underline' : ''}`}>
                {fechaConMes(dia)}
                {dia === hoy && <span className="block text-small font-normal">Hoy</span>}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {horas.map((hora) => (
            <tr key={hora}>
              <th scope="row" className="border-b p-2 text-left align-top font-normal text-secondary">{formatHora(hora)}</th>
              {dias.map((dia) => (
                <td key={dia} className={`border-b border-l p-1 align-top ${dia === hoy ? 'bg-app' : ''}`}>
                  <div className="flex flex-col gap-1">
                    {sesiones.filter((s) => s.fecha === dia && s.hora === hora).map((s) => (
                      <div key={s.clave} role="group" aria-label={`${descripcion(s)}, ${s.sede}, entrenador: ${entrenadorDeSesion(s)}${s.estado === 'CANCELADO' ? ', cancelado' : ''}${s.tipo === 'EXTRA' ? ', extra' : ''}`} className={`flex flex-col gap-1 rounded-md p-2 ${s.estado === 'CANCELADO' ? 'border bg-app text-primary' : 'bg-brand-primary text-on-dark'}`}>
                        <Datos sesion={s} />
                        <Acciones sesion={s} acciones={accionesDe?.(s)} manejadores={manejadores} />
                      </div>
                    ))}
                  </div>
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function Lista({ dias, sesiones, hoy, accionesDe, manejadores }) {
  return (
    <div className="flex flex-col gap-4">
      {dias.map((dia) => {
        const delDia = sesiones.filter((s) => s.fecha === dia)
        if (delDia.length === 0) return null
        return (
          <section key={dia} aria-labelledby={`dia-${dia}`} className="flex flex-col gap-2">
            <h2 id={`dia-${dia}`} className={`text-label uppercase ${dia === hoy ? 'text-primary underline' : 'text-secondary'}`}>
              {diaLargo(dia)}{dia === hoy ? ' · Hoy' : ''}
            </h2>
            <ul className="flex flex-col gap-2">
              {delDia.map((s) => (
                <Card as="li" key={s.clave} className="flex flex-col gap-3">
                  <div className="flex min-w-0 flex-col gap-1 text-small">
                    <Datos sesion={s} grande />
                  </div>
                  <Acciones sesion={s} acciones={accionesDe?.(s)} manejadores={manejadores} />
                </Card>
              ))}
            </ul>
          </section>
        )
      })}
    </div>
  )
}

export default function SesionesSemana({ dias, sesiones, hoy, modo = 'cuadricula', accionesDe, onCancelar, onReactivar, onRotar, onEditarExtra, onEliminarExtra }) {
  const manejadores = { onCancelar, onReactivar, onRotar, onEditarExtra, onEliminarExtra }
  const Vista = modo === 'cuadricula' ? Cuadricula : Lista
  return <Vista dias={dias} sesiones={sesiones} hoy={hoy} accionesDe={accionesDe} manejadores={manejadores} />
}
