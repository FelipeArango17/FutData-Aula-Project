import { useState } from 'react'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'
import FilterChips from '../../components/ui/FilterChips.jsx'

const periodos = ['Septiembre', 'Octubre', 'Trimestre']
const menor = [
  ['Mateo Vélez', '6 faltas · 62 %', 'danger', 'Bajo'],
  ['Tomás Giraldo', '4 faltas · 71 %', 'warning', 'Atención'],
  ['Nicolás Mejía', '3 faltas · 76 %', 'warning', 'Atención'],
]

export default function Reportes() {
  // Solo hay datos de ejemplo de octubre; el periodo queda listo para el backend.
  const [periodo, setPeriodo] = useState('Octubre')

  return (
    <Page title="Reportes" subtitle="Asistencia y cartera por periodo">
      <FilterChips label="Periodo" options={periodos} value={periodo} onChange={setPeriodo} />
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Card className="flex flex-col gap-2">
          <h2 className="text-label uppercase text-secondary">Asistencia promedio</h2>
          <p className="text-kpi">86 %</p>
          <p className="text-small text-secondary">Últimas 6 semanas</p>
          <div><Badge tone="success">En meta</Badge></div>
        </Card>
        <Card className="flex flex-col gap-2">
          <h2 className="text-label uppercase text-secondary">Recaudado</h2>
          <p className="text-kpi">$ 18,4 M</p>
          <p className="text-small text-secondary">89 % de lo esperado</p>
          <div><Badge tone="success">En meta</Badge></div>
        </Card>
      </div>
      <Card aria-labelledby="menor-titulo" className="flex flex-col gap-3">
        <h2 id="menor-titulo" className="text-h3">Menor asistencia</h2>
        <ul className="flex flex-col gap-3">
          {menor.map(([nombre, detalle, tone, texto]) => (
            <li key={nombre} className="flex flex-wrap items-center justify-between gap-2">
              <div className="min-w-0">
                <p className="break-words text-body-medium">{nombre}</p>
                <p className="text-small text-secondary">{detalle}</p>
              </div>
              <Badge tone={tone}>{texto}</Badge>
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
