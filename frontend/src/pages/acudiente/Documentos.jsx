import { useState } from 'react'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'
import FilterChips from '../../components/ui/FilterChips.jsx'

// Tipos del club (utils/documentosClub.js), con el estado de ejemplo de cada jugador (mismos datos que documentosService).
const documentos = [
  { nombre: 'Documento de identidad', detalle: 'Santiago Ríos · Obligatorio', estado: 'Aprobado', tone: 'success', accion: 'Ver documento', filtro: '' },
  { nombre: 'Certificado de estudio', detalle: 'Santiago Ríos · Obligatorio · Subido el 20 oct.', estado: 'En revisión', tone: 'warning', accion: 'Ver documento', filtro: 'Pendientes' },
  { nombre: 'Habeas Data', detalle: 'Santiago Ríos · Obligatorio · Sin cargar', estado: 'Pendiente', tone: 'warning', accion: 'Subir documento', filtro: 'Pendientes' },
  { nombre: 'Certificado de estudio', detalle: 'Valeria Ríos · Obligatorio · Vence el 05 nov.', estado: 'Por vencer', tone: 'warning', accion: 'Subir nuevo', filtro: 'Por vencer' },
  { nombre: 'Documento de identidad', detalle: 'Valeria Ríos · Obligatorio · Espera una nueva carga', estado: 'Rechazado', tone: 'danger', accion: 'Subir de nuevo', filtro: 'Pendientes' },
]
const filtros = ['Todos', 'Pendientes', 'Por vencer']

export default function Documentos() {
  const [filtro, setFiltro] = useState('Todos')
  const visibles = documentos.filter((d) => filtro === 'Todos' || d.filtro === filtro)

  return (
    <Page title="Documentos" subtitle="Documentos de tus jugadores">
      <FilterChips label="Filtrar documentos" options={filtros} value={filtro} onChange={setFiltro} />
      <ul className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {visibles.map((d) => (
          <Card as="li" key={d.nombre + d.detalle} className="flex flex-col gap-3">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div className="min-w-0">
                <h2 className="break-words text-h3">{d.nombre}</h2>
                <p className="text-body text-secondary">{d.detalle}</p>
              </div>
              <Badge tone={d.tone}>{d.estado}</Badge>
            </div>
            <Button variant={d.tone === 'success' ? 'secondary' : 'primary'}>{d.accion}</Button>
          </Card>
        ))}
      </ul>
      <p className="text-body text-secondary">
        Formatos PDF, JPG o PNG, máximo 5 MB por archivo. Un administrador revisa cada documento.
      </p>
    </Page>
  )
}
