import { useState } from 'react'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'
import FilterChips from '../../components/ui/FilterChips.jsx'

const avisos = [
  { titulo: 'Pago vencido: Uniforme (2.º pago)', detalle: 'Santiago · $ 90.000 · venció el 10 oct.', cuando: 'Hace 2 h', nueva: true, tipo: 'Pagos' },
  { titulo: 'Cambio de horario Semillero', detalle: 'Sáb 24 oct pasa de 8:30 a 9:00 a. m.', cuando: 'Ayer', nueva: true, tipo: 'Horarios' },
  { titulo: 'Ficha médica por vencer', detalle: 'Valeria · vence el 05 nov. Sube una nueva.', cuando: 'Hace 2 días', nueva: false, tipo: 'Documentos' },
  { titulo: 'Pago confirmado: Mensualidad septiembre', detalle: 'Comprobante #0482 disponible', cuando: '15 sep.', nueva: false, tipo: 'Pagos' },
  { titulo: 'Santiago fue convocado a la Copa Prado', detalle: 'Sábado 8 nov. · Medellín', cuando: '12 sep.', nueva: false, tipo: 'Horarios' },
]
const filtros = ['Todas', 'Pagos', 'Horarios', 'Documentos']
const preferencias = [
  ['Recordatorios de pago', true], ['Cambios de horario', true],
  ['Documentos por vencer', true], ['Resultados de torneos', false],
]

export default function Notificaciones() {
  const [filtro, setFiltro] = useState('Todas')
  const visibles = avisos.filter((a) => filtro === 'Todas' || a.tipo === filtro)

  return (
    <Page title="Notificaciones" subtitle="Avisos del club para ti y tus jugadores">
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <FilterChips label="Filtrar avisos" options={filtros} value={filtro} onChange={setFiltro} />
        <Button variant="secondary">Marcar todas como leídas</Button>
      </div>
      <ul className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {visibles.map((a) => (
          <Card as="li" key={a.titulo} className="flex flex-col gap-1">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <h2 className="min-w-0 break-words text-body-medium">{a.titulo}</h2>
              {a.nueva && <Badge tone="warning">Nueva</Badge>}
            </div>
            <p className="break-words text-body text-secondary">{a.detalle}</p>
            <p className="text-small text-secondary">{a.cuando}</p>
          </Card>
        ))}
      </ul>
      <Card aria-labelledby="correo-titulo" className="flex flex-col gap-3">
        <h2 id="correo-titulo" className="text-h3">Avisos por correo</h2>
        <ul className="flex flex-col gap-1">
          {preferencias.map(([texto, activo]) => (
            <li key={texto}>
              <label className="flex min-h-11 items-center justify-between gap-3 text-body">
                <span className="min-w-0 break-words">{texto}</span>
                <input type="checkbox" defaultChecked={activo} className="h-5 w-5 shrink-0" />
              </label>
            </li>
          ))}
        </ul>
      </Card>
    </Page>
  )
}
