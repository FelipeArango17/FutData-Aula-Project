import Page from '../../components/ui/Page.jsx'
import ResumenPagos from '../../components/pagos/ResumenPagos.jsx'
import IndicadoresCartera, { UltimosPagos } from '../../components/pagos/IndicadoresCartera.jsx'
import { useAuth } from '../../context/AuthContext.js'
import { hoy } from '../../utils/hoy.js'
import { resumenCartera } from '../../utils/resumenCartera.js'
import { mesYAnio, saludoDe } from '../../utils/saludo.js'

export default function Inicio() {
  const { usuario } = useAuth()
  return (
    <Page title={saludoDe(usuario)} subtitle={`Resumen de pagos · ${mesYAnio(hoy())}`}>
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
    </Page>
  )
}
