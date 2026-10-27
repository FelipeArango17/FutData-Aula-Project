import { useState } from 'react'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'
import FilterChips from '../../components/ui/FilterChips.jsx'

const filtros = ['Todos', 'Vencidos', 'Pendientes', 'Pagados']
const pagos = [
  { quien: 'Mateo Vélez · Ana Vélez', detalle: 'Mensualidad octubre · vence 15 oct.', estado: 'Vencidos', tone: 'danger', texto: 'Vencido', valor: '$ 90.000', accion: 'Enviar recordatorio' },
  { quien: 'Emmanuel Arias · Luis Arias', detalle: 'Uniforme · vence 10 oct.', estado: 'Vencidos', tone: 'danger', texto: 'Vencido', valor: '$ 90.000', accion: 'Enviar recordatorio' },
  { quien: 'Santiago Ríos · María Torres', detalle: 'Mensualidad octubre · vence 30 oct.', estado: 'Pendientes', tone: 'warning', texto: 'Pendiente', valor: '$ 90.000', accion: 'Registrar pago', registrar: true },
  { quien: 'Juan Pablo Cano · Rosa Cano', detalle: 'Inscripción 2026 · vence 30 oct.', estado: 'Pendientes', tone: 'warning', texto: 'Pendiente', valor: '$ 150.000', accion: 'Registrar pago', registrar: true },
  { quien: 'Samuel Londoño · Pedro Londoño', detalle: 'Mensualidad octubre · pagado', estado: 'Pagados', tone: 'success', texto: 'Pagado', valor: '$ 90.000', accion: 'Ver comprobante' },
]

// `base` permite reutilizar la pantalla en /administrador (mismo contenido que el tesorero).
export default function Pagos({ base = '/tesorero' }) {
  const [filtro, setFiltro] = useState('Todos')
  const visibles = pagos.filter((p) => filtro === 'Todos' || p.estado === filtro)

  return (
    <Page title="Pagos y facturación" subtitle="Cartera del club · Octubre 2026">
      <FilterChips label="Filtrar pagos" options={filtros} value={filtro} onChange={setFiltro} />
      <ul className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {visibles.map((p) => (
          <Card as="li" key={p.quien + p.detalle} className="flex flex-col gap-3">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div className="min-w-0">
                <h2 className="break-words text-body-medium">{p.quien}</h2>
                <p className="break-words text-small text-secondary">{p.detalle}</p>
              </div>
              <Badge tone={p.tone}>{p.texto}</Badge>
            </div>
            <p className="text-h3">{p.valor}</p>
            <Button variant={p.tone === 'success' ? 'secondary' : 'primary'} to={p.registrar ? `${base}/pagos/registrar` : undefined}>{p.accion}</Button>
          </Card>
        ))}
      </ul>
    </Page>
  )
}
