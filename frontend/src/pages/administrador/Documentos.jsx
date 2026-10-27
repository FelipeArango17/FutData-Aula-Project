import { useState } from 'react'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'
import FilterChips from '../../components/ui/FilterChips.jsx'

const filtros = ['Pendientes', 'Aprobados', 'Por vencer']
const documentos = [
  { quien: 'Juan Pablo Cano', detalle: 'Ficha médica · subida hoy', estado: 'Por revisar', tone: 'warning' },
  { quien: 'Mateo Vélez', detalle: 'Registro civil · subido ayer', estado: 'Por revisar', tone: 'warning' },
  { quien: 'Valeria Ríos', detalle: 'Ficha médica · vence el 05 nov.', estado: 'Por vencer', tone: 'warning' },
  { quien: 'Tomás Giraldo', detalle: 'Seguro estudiantil · subido hace 2 días', estado: 'Por revisar', tone: 'warning' },
]

export default function Documentos() {
  const [filtro, setFiltro] = useState('Pendientes')
  const visibles = documentos.filter((d) =>
    filtro === 'Pendientes' ? true : filtro === 'Por vencer' ? d.estado === 'Por vencer' : false,
  )

  return (
    <Page title="Documentación" subtitle="Revisión de documentos subidos por acudientes">
      <FilterChips label="Filtrar documentos" options={filtros} value={filtro} onChange={setFiltro} />
      {visibles.length === 0 && <p className="text-body text-secondary">No hay documentos en este grupo.</p>}
      <ul className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {visibles.map((d) => (
          <Card as="li" key={d.quien + d.detalle} className="flex flex-col gap-3">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div className="min-w-0">
                <h2 className="break-words text-h3">{d.quien}</h2>
                <p className="text-body text-secondary">{d.detalle}</p>
              </div>
              <Badge tone={d.tone}>{d.estado}</Badge>
            </div>
            <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
              <Button>Aprobar</Button>
              <Button variant="secondary">Rechazar</Button>
            </div>
          </Card>
        ))}
      </ul>
    </Page>
  )
}
