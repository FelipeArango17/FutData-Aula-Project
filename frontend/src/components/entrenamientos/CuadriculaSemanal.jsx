import { Link } from 'react-router-dom'
import { ENTRENADORES } from '../../services/entrenamientosService.js'
import { diaCorto, diaLargo, formatHora } from '../../utils/fechas.js'

const nombreEntrenador = (id) => ENTRENADORES.find((x) => x.id === id)?.nombre ?? 'Por turnos'
const conGrupo = (e) => (e.grupo ? `${e.categoria} · ${e.grupo}` : e.categoria)

// Cuadrícula semanal (escritorio): columnas Lun a Sáb, una fila por cada hora que tiene entrenamientos.
// Cada entrenamiento es un enlace que abre su edición y muestra categoría (y grupo), hora, sede y entrenador.
export default function CuadriculaSemanal({ dias, entrenamientos, base, editandoId }) {
  const horas = [...new Set(entrenamientos.map((e) => e.hora))].sort()

  return (
    <div className="overflow-hidden rounded-lg border bg-surface">
      <table className="w-full table-fixed border-collapse text-small">
        <caption className="sr-only">Entrenamientos de la semana, por día y hora</caption>
        <thead>
          <tr>
            <th scope="col" className="w-20 border-b p-2 text-left font-semibold"><span className="sr-only">Hora</span></th>
            {dias.map((dia) => (
              <th key={dia} scope="col" className="border-b border-l p-2 text-left font-semibold">
                {diaCorto(dia)}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {horas.map((hora) => (
            <tr key={hora}>
              <th scope="row" className="border-b p-2 text-left align-top font-normal text-secondary">{formatHora(hora)}</th>
              {dias.map((dia) => (
                <td key={dia} className="border-b border-l p-1 align-top">
                  <div className="flex flex-col gap-1">
                    {entrenamientos
                      .filter((e) => e.fecha === dia && e.hora === hora)
                      .map((e) => (
                        <Link
                          key={e.id}
                          to={`${base}/${e.id}/editar`}
                          aria-label={`Editar entrenamiento ${conGrupo(e)}, ${e.sede}, entrenador: ${nombreEntrenador(e.idEntrenador)}, ${diaLargo(dia).toLowerCase()}, ${formatHora(hora)}`}
                          aria-current={e.id === editandoId ? 'true' : undefined}
                          className={`block min-h-11 rounded-md p-2 text-on-dark hover:bg-brand-primary-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2 ${
                            e.id === editandoId ? 'bg-brand-primary-hover underline' : 'bg-brand-primary'
                          }`}
                        >
                          <span className="block break-words font-semibold">{conGrupo(e)}</span>
                          <span className="block break-words">{formatHora(e.hora)} – {formatHora(e.horaFin)}</span>
                          <span className="block break-words">{e.sede}</span>
                          <span className="block break-words">{nombreEntrenador(e.idEntrenador)}</span>
                        </Link>
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
