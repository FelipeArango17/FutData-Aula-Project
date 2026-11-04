import Card from '../ui/Card.jsx'
import Button from '../ui/Button.jsx'
import IndicadoresCartera from './IndicadoresCartera.jsx'
import ResumenPagos from './ResumenPagos.jsx'
import { formatearCOP } from '../../utils/dinero.js'
import { descargarTexto } from '../../utils/descargas.js'
import { hoy } from '../../utils/hoy.js'
import { etiquetaMes, generarCsvCobros, resumenCartera } from '../../utils/resumenCartera.js'

// Tabla de recaudo de los últimos 6 meses; la barra es decorativa (aria-hidden), el valor va en texto.
export function RecaudoPorMes({ meses }) {
  const maximo = Math.max(...meses.map((m) => m.total), 1)
  return (
    <Card aria-labelledby="recaudo-titulo" className="flex flex-col gap-3">
      <h2 id="recaudo-titulo" className="text-h3">Recaudo de los últimos 6 meses</h2>
      <table className="w-full border-collapse text-left text-body">
        <caption className="sr-only">Pagos aprobados por mes, según la fecha de pago</caption>
        <thead>
          <tr className="text-label uppercase text-secondary">
            <th scope="col" className="py-2 pr-3">Mes</th>
            <th scope="col" className="py-2 pr-3 text-right">Recaudo</th>
            <th scope="col" className="hidden py-2 sm:table-cell"><span className="sr-only">Proporción</span></th>
          </tr>
        </thead>
        <tbody>
          {meses.map((m) => (
            <tr key={m.mes} className="border-t">
              <th scope="row" className="py-2 pr-3 font-normal">{etiquetaMes(m.mes)}</th>
              <td className="py-2 pr-3 text-right">{formatearCOP(m.total)}</td>
              <td className="hidden w-1/2 py-2 sm:table-cell" aria-hidden="true">
                <div className="h-3 rounded-full bg-brand-primary" style={{ width: `${Math.round((m.total / maximo) * 100)}%` }} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </Card>
  )
}

export function VencidosPorDeportista({ filas }) {
  return (
    <Card aria-labelledby="vencidos-titulo" className="flex flex-col gap-3">
      <h2 id="vencidos-titulo" className="text-h3">Mayor cartera vencida</h2>
      {filas.length === 0 ? (
        <p className="text-body text-secondary">No hay cobros vencidos.</p>
      ) : (
        <ul className="flex flex-col gap-3">
          {filas.map((f) => (
            <li key={f.idDeportista} className="flex flex-wrap items-center justify-between gap-2">
              <p className="min-w-0 break-words text-body-medium">{f.nombre}</p>
              <p className="text-body">{formatearCOP(f.total)} · {f.cantidad === 1 ? '1 cobro' : `${f.cantidad} cobros`}</p>
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}

function Contenido({ cobros }) {
  const resumen = resumenCartera(cobros, hoy())
  // Exporta los cobros que la pantalla tiene cargados (CSV generado en el navegador)
  const exportar = () => descargarTexto(`cobros-${hoy()}.csv`, generarCsvCobros(cobros), 'text/csv;charset=utf-8', { conBom: true })
  return (
    <>
      <IndicadoresCartera resumen={resumen} />
      <RecaudoPorMes meses={resumen.recaudoPorMes} />
      <VencidosPorDeportista filas={resumen.vencidosPorDeportista} />
      <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
        <Button onClick={exportar}>Exportar CSV</Button>
      </div>
    </>
  )
}

// Reportes de cartera del tesorero y del administrador: leen la misma fuente (GET /api/pagos), por eso comparten módulo "reportes".
export default function ReporteCartera() {
  return <ResumenPagos>{({ cobros }) => <Contenido cobros={cobros} />}</ResumenPagos>
}
