import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Button from '../../components/ui/Button.jsx'
import ResumenPagos from '../../components/pagos/ResumenPagos.jsx'
import IndicadoresCartera, { UltimosPagos } from '../../components/pagos/IndicadoresCartera.jsx'
import { useAuth } from '../../context/AuthContext.js'
import { autenticacionReal } from '../../services/supabaseClient.js'
import { modulosConApi } from '../../services/usarApi.js'
import { opcionVisible } from '../../utils/menusVisibles.js'
import { hoy } from '../../utils/hoy.js'
import { resumenCartera } from '../../utils/resumenCartera.js'
import { mesYAnio, saludoDe } from '../../utils/saludo.js'

// Cada acceso rápido va con el módulo de la pantalla a la que lleva: solo se muestra si ese módulo está visible.
const ACCESOS = [
  { label: 'Inscribir jugador', to: '/administrador/deportistas/inscribir', modulo: 'deportistas', variant: 'primary' },
  { label: 'Crear torneo', to: '/administrador/torneos', modulo: 'torneos', variant: 'secondary' },
]

export default function Dashboard() {
  const { usuario } = useAuth()
  const modulos = modulosConApi()
  const accesos = ACCESOS.filter((a) => opcionVisible(a, { autenticacionReal, modulos }))
  return (
    <Page title={saludoDe(usuario)} subtitle={`Resumen del club · ${mesYAnio(hoy())}`}>
      <ResumenPagos>
        {({ cobros }) => {
          const resumen = resumenCartera(cobros, hoy())
          return (
            <>
              <IndicadoresCartera resumen={resumen} />
              <UltimosPagos pagos={resumen.ultimosPagos} />
            </>
          )
        }}
      </ResumenPagos>
      {accesos.length > 0 && (
        <Card aria-labelledby="accesos-titulo" className="flex flex-col gap-3">
          <h2 id="accesos-titulo" className="text-h3">Accesos rápidos</h2>
          <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
            {accesos.map((a) => <Button key={a.to} variant={a.variant} to={a.to}>{a.label}</Button>)}
          </div>
        </Card>
      )}
    </Page>
  )
}
