import { useState } from 'react'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'
import FilterChips from '../../components/ui/FilterChips.jsx'

const periodos = ['Septiembre', 'Octubre', 'Trimestre']
const mora = [
  ['Mateo Vélez', '$ 180.000 · 2 pagos'], ['Emmanuel Arias', '$ 90.000 · 1 pago'], ['Nicolás Mejía', '$ 90.000 · 1 pago'],
]

export default function Reportes() {
  // Solo hay datos de ejemplo de octubre; el periodo se guarda para cuando exista el backend.
  const [periodo, setPeriodo] = useState('Octubre')

  return (
    <Page title="Reportes" subtitle="Cartera y recaudo por periodo">
      <FilterChips label="Periodo" options={periodos} value={periodo} onChange={setPeriodo} />
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Card className="flex flex-col gap-2">
          <h2 className="text-label uppercase text-secondary">Recaudado</h2>
          <p className="text-kpi">$ 18,4 M</p>
          <p className="text-small text-secondary">de $ 20,7 M esperados</p>
          <div><Badge tone="success">89 %</Badge></div>
        </Card>
        <Card className="flex flex-col gap-2">
          <h2 className="text-label uppercase text-secondary">Por cobrar</h2>
          <p className="text-kpi">$ 2,3 M</p>
          <p className="text-small text-secondary">14 pagos vencidos</p>
          <div><Badge tone="danger">11 %</Badge></div>
        </Card>
      </div>
      <Card aria-labelledby="mora-titulo" className="flex flex-col gap-3">
        <h2 id="mora-titulo" className="text-h3">Mayor mora</h2>
        <ul className="flex flex-col gap-3">
          {mora.map(([nombre, detalle]) => (
            <li key={nombre} className="flex flex-wrap items-center justify-between gap-2">
              <div className="min-w-0">
                <p className="break-words text-body-medium">{nombre}</p>
                <p className="text-small text-secondary">{detalle}</p>
              </div>
              <Badge tone="danger">Vencido</Badge>
            </li>
          ))}
        </ul>
      </Card>
      <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
        <Button>Exportar PDF</Button>
        <Button variant="secondary">Exportar Excel</Button>
      </div>
    </Page>
  )
}
