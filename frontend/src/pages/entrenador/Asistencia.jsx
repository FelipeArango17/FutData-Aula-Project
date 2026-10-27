import { useState } from 'react'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Button from '../../components/ui/Button.jsx'
import Field from '../../components/ui/Field.jsx'

const estados = ['Presente', 'Ausente', 'Justificado']
const inicial = [
  { nombre: 'Santiago Ríos', estado: 'Presente' },
  { nombre: 'Mateo Vélez', nota: 'Sin aviso', estado: 'Ausente' },
  { nombre: 'Juan Pablo Cano', estado: 'Presente' },
  { nombre: 'Emmanuel Arias', nota: 'Cita médica', estado: 'Justificado' },
  { nombre: 'Samuel Londoño', nota: 'Llegó tarde (10 min)', estado: 'Presente' },
  { nombre: 'Tomás Giraldo', estado: 'Presente' },
  { nombre: 'Nicolás Mejía', estado: 'Presente' },
]

export default function Asistencia() {
  const [jugadores, setJugadores] = useState(inicial)
  const marcar = (nombre, estado) =>
    setJugadores((lista) => lista.map((j) => (j.nombre === nombre ? { ...j, estado } : j)))
  const marcarTodos = () => setJugadores((lista) => lista.map((j) => ({ ...j, estado: 'Presente' })))
  const cuenta = (estado) => jugadores.filter((j) => j.estado === estado).length

  return (
    <Page title="Control de asistencia" subtitle="Registra la asistencia del entrenamiento de hoy">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Field label="Fecha" id="fecha" defaultValue="Jue 22 oct. 2026" readOnly />
        <Field label="Categoría" id="categoria" as="select" defaultValue="Sub-10">
          <option>Sub-10</option>
          <option>Sub-12</option>
        </Field>
      </div>
      <p className="text-body text-secondary">Sub-10 · Cancha 1 · 4:00 p. m.</p>
      <Button variant="secondary" onClick={marcarTodos} className="md:self-start">Marcar todos presentes</Button>
      <ul className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {jugadores.map((j, i) => (
          <Card as="li" key={j.nombre} className="flex flex-col gap-3">
            <fieldset className="flex min-w-0 flex-col gap-3">
              <legend className="mb-1 break-words text-body-medium">
                {i + 1}. {j.nombre}
                {j.nota && <span className="block text-small font-normal text-secondary">{j.nota}</span>}
              </legend>
              {/* En pantallas angostas o con letra grande las opciones se apilan */}
              <div className="grid grid-cols-[repeat(auto-fit,minmax(5.75rem,1fr))] gap-2">
                {estados.map((estado) => (
                  <label
                    key={estado}
                    className={`flex min-h-11 cursor-pointer items-center justify-center rounded-md border px-2 text-center text-body-medium focus-within:ring-2 focus-within:ring-focus focus-within:ring-offset-2 ${
                      j.estado === estado ? 'border-transparent bg-brand-primary text-on-dark' : 'bg-surface text-primary'
                    }`}
                  >
                    <input
                      type="radio"
                      name={`asistencia-${j.nombre}`}
                      value={estado}
                      checked={j.estado === estado}
                      onChange={() => marcar(j.nombre, estado)}
                      className="sr-only"
                    />
                    {estado}
                  </label>
                ))}
              </div>
            </fieldset>
          </Card>
        ))}
      </ul>
      <p className="text-body text-secondary">¿Un jugador no aparece en la lista? Repórtalo al administrador.</p>
      <Card aria-labelledby="resumen-titulo" className="flex flex-col gap-3">
        <h2 id="resumen-titulo" className="text-h3">Resumen de hoy</h2>
        <dl className="grid grid-cols-3 gap-2 text-center">
          {estados.map((estado) => (
            <div key={estado}>
              <dd className="text-kpi">{cuenta(estado)}</dd>
              <dt className="break-words text-small text-secondary">{estado}s</dt>
            </div>
          ))}
        </dl>
        <p className="text-small text-secondary">Al guardar se registra fecha, hora y observaciones.</p>
      </Card>
      <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
        <Button>Guardar asistencia</Button>
        <Button variant="secondary">Exportar PDF</Button>
      </div>
    </Page>
  )
}
