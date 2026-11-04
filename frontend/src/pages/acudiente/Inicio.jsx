import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Button from '../../components/ui/Button.jsx'
import EstadoDeCarga from '../../components/pagos/EstadoDeCarga.jsx'
import { EstadoPago } from '../../components/pagos/ListaPagos.jsx'
import useCarga from '../../hooks/useCarga.js'
import { useAuth } from '../../context/AuthContext.js'
import { obtenerEstadoCuenta, pagosPorApi } from '../../services/pagosService.js'
import { autenticacionReal } from '../../services/supabaseClient.js'
import { modulosConApi } from '../../services/usarApi.js'
import { mensajeDeErrorPagos } from '../../utils/pagosApi.js'
import { opcionVisible } from '../../utils/menusVisibles.js'
import { formatearCOP } from '../../utils/dinero.js'
import { fechaLargaConAnio } from '../../utils/fechas.js'
import { resumenAcudiente } from '../../utils/resumenCartera.js'
import { saludoDe } from '../../utils/saludo.js'

// GET /api/pagos/mis-pagos: el backend filtra por la sesión, no hace falta pasar al acudiente.
const cargarMisPagos = () => obtenerEstadoCuenta({})

function Lista({ id, titulo, pagos, vacio, detalle }) {
  return (
    <Card aria-labelledby={id} className="flex flex-col gap-3">
      <h2 id={id} className="text-h3">{titulo}</h2>
      {pagos.length === 0 ? (
        <p className="text-body text-secondary">{vacio}</p>
      ) : (
        <ul className="flex flex-col gap-3">
          {pagos.map((p) => (
            <li key={p.id} className="flex flex-wrap items-center justify-between gap-2">
              <div className="min-w-0">
                <p className="break-words text-body-medium">{p.nombreDeportista} · {p.concepto}</p>
                <p className="text-small text-secondary">{formatearCOP(p.monto)} · {detalle(p)}</p>
              </div>
              <EstadoPago estado={p.estado} />
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}

export default function Inicio() {
  const { usuario } = useAuth()
  const conectado = pagosPorApi()
  const { cargando, datos, error, recargar } = useCarga(cargarMisPagos, conectado)
  const resumen = datos ? resumenAcudiente(datos) : null
  const verPagos = opcionVisible({ modulo: 'pagos' }, { autenticacionReal, modulos: modulosConApi() })

  return (
    <Page title={saludoDe(usuario)} subtitle="Resumen de tus pagos">
      {!conectado ? (
        <EstadoDeCarga vacio="Este resumen se calcula con tus pagos y el módulo de pagos todavía no está conectado a la API." />
      ) : (
        <EstadoDeCarga
          cargando={cargando}
          mensajeError={error ? mensajeDeErrorPagos(error, 'No se pudieron cargar tus pagos.') : null}
          onReintentar={recargar}
          vacio={resumen && !resumen.hayPagos ? 'Aún no hay pagos registrados.' : null}
          texto="Cargando tus pagos…"
        >
          {resumen && (
            <>
              <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                <Lista
                  id="proximos-titulo" titulo="Próximos pagos" pagos={resumen.proximos} vacio="No tienes pagos pendientes."
                  detalle={(p) => (p.estado === 'vencido' ? 'venció' : 'vence') + ` el ${fechaLargaConAnio(p.fechaVencimiento)}`}
                />
                <Lista
                  id="pagados-titulo" titulo="Últimos pagos" pagos={resumen.ultimosPagados} vacio="Aún no has realizado pagos."
                  detalle={(p) => `pagado el ${fechaLargaConAnio(p.fechaPago)}`}
                />
              </div>
              {verPagos && <div><Button to="/acudiente/pagos">Ver mis pagos</Button></div>}
            </>
          )}
        </EstadoDeCarga>
      )}
    </Page>
  )
}
