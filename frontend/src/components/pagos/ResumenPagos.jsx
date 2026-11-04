import EstadoDeCarga from './EstadoDeCarga.jsx'
import useCarga from '../../hooks/useCarga.js'
import { listarTodosLosCobros, MAX_PAGINAS_RESUMEN, pagosPorApi } from '../../services/pagosService.js'
import { mensajeDeErrorPagos } from '../../utils/pagosApi.js'

const cargarCobros = () => listarTodosLosCobros()
export const TEXTO_SIN_PAGOS = 'Aún no hay pagos registrados.'

// Carga todos los cobros (GET /api/pagos, todas las páginas) y llama a children({ cobros }) con ellos. Maneja los estados:
// módulo de pagos apagado, cargando, error con Reintentar y "sin cobros". Si se llegó al tope de páginas lo avisa
// (los totales serían parciales). Lo usan el inicio del tesorero, el dashboard del administrador y los reportes.
export default function ResumenPagos({ children }) {
  const conectado = pagosPorApi()
  const { cargando, datos, error, recargar } = useCarga(cargarCobros, conectado)

  if (!conectado) {
    return <EstadoDeCarga vacio="Este resumen se calcula con los pagos del club y el módulo de pagos todavía no está conectado a la API." />
  }
  const vacio = datos && datos.items.length === 0 ? TEXTO_SIN_PAGOS : null
  return (
    <EstadoDeCarga
      cargando={cargando}
      mensajeError={error ? mensajeDeErrorPagos(error, 'No se pudo cargar el resumen de pagos.') : null}
      onReintentar={recargar}
      vacio={vacio}
      texto="Cargando el resumen de pagos…"
    >
      {datos && (
        <>
          {datos.truncado && (
            <p role="status" className="rounded-lg bg-warning p-4 text-body-medium text-warning">
              Hay más de {MAX_PAGINAS_RESUMEN * 100} cobros y solo se leyeron los más recientes: los totales son parciales.
            </p>
          )}
          {children({ cobros: datos.items })}
        </>
      )}
    </EstadoDeCarga>
  )
}
