import { useState } from 'react'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Button from '../../components/ui/Button.jsx'
import Field from '../../components/ui/Field.jsx'
import { CATEGORIAS } from '../../services/entrenamientosService.js'

const pasos = ['Jugador', 'Acudiente', 'Confirmar']

export default function Inscribir() {
  const [paso, setPaso] = useState(1)
  const [datos, setDatos] = useState({
    nombres: 'Nicolás Mejía Ruiz', nacimiento: '14 mar. 2016', documento: 'TI 1.032.456.789',
    categoria: 'Semillero', posicion: 'Defensa', eps: 'Sura',
    acudiente: 'Claudia Ruiz', correo: 'claudia.ruiz@correo.com', telefono: '300 123 4567', parentesco: 'Madre',
  })
  const cambiar = (campo) => (e) => setDatos((d) => ({ ...d, [campo]: e.target.value }))
  const siguiente = (e) => { e.preventDefault(); setPaso((p) => Math.min(p + 1, 3)) }

  return (
    <Page title="Inscribir jugador" subtitle={paso === 1 ? 'Ficha completa registrada en secretaría' : `Paso ${paso} de 3 · ${pasos[paso - 1]}`}>
      <ol aria-label="Pasos de la inscripción" className="grid grid-cols-3 gap-2">
        {pasos.map((nombre, i) => {
          const actual = i + 1 === paso
          return (
            <li
              key={nombre}
              aria-current={actual ? 'step' : undefined}
              className={`flex flex-col items-center gap-1 rounded-md border p-2 text-center text-small ${
                actual ? 'border-transparent bg-brand-primary text-on-dark' : 'bg-surface text-secondary'
              }`}
            >
              <span className="text-body-medium">{i + 1}</span>
              <span className="break-words">{nombre}</span>
            </li>
          )
        })}
      </ol>

      {paso === 1 && (
        <form className="flex max-w-xl flex-col gap-4" onSubmit={siguiente}>
          <h2 className="text-h3">Datos del jugador</h2>
          <Field label="Nombres y apellidos" id="nombres" value={datos.nombres} onChange={cambiar('nombres')} />
          <Field label="Fecha de nacimiento" id="nacimiento" value={datos.nacimiento} onChange={cambiar('nacimiento')} />
          <Field label="Documento" id="documento" value={datos.documento} onChange={cambiar('documento')} />
          <Field label="Categoría (según edad)" id="categoria" as="select" value={datos.categoria} onChange={cambiar('categoria')}>
            {CATEGORIAS.map((c) => <option key={c}>{c}</option>)}
          </Field>
          <Field label="Posición" id="posicion" value={datos.posicion} onChange={cambiar('posicion')} />
          <Field label="Entidad de salud (EPS)" id="eps" value={datos.eps} onChange={cambiar('eps')} />
          <p className="text-body text-secondary">La categoría se propone según la edad; secretaría puede ajustarla.</p>
          <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
            <Button type="submit">Continuar</Button>
            <Button variant="secondary" to="/administrador/deportistas">Cancelar</Button>
          </div>
        </form>
      )}

      {paso === 2 && (
        <form className="flex max-w-xl flex-col gap-4" onSubmit={siguiente}>
          <h2 className="text-h3">Datos del acudiente</h2>
          <Field label="Nombre completo" id="acudiente" value={datos.acudiente} onChange={cambiar('acudiente')} />
          <Field label="Correo electrónico" id="correo" type="email" value={datos.correo} onChange={cambiar('correo')} />
          <Field label="Teléfono" id="telefono" type="tel" value={datos.telefono} onChange={cambiar('telefono')} />
          <Field label="Parentesco" id="parentesco" as="select" value={datos.parentesco} onChange={cambiar('parentesco')}>
            <option>Madre</option><option>Padre</option><option>Otro</option>
          </Field>
          <p className="text-body text-secondary">
            Se crea la cuenta del acudiente y se envía un correo para que defina su contraseña.
          </p>
          <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
            <Button type="submit">Crear cuenta y confirmar</Button>
            <Button variant="secondary" onClick={() => setPaso(1)}>Atrás</Button>
          </div>
        </form>
      )}

      {paso === 3 && (
        <div className="flex max-w-xl flex-col gap-4">
          <Card aria-labelledby="resumen-titulo">
            <h2 id="resumen-titulo" className="mb-3 text-h3">Revisa la inscripción</h2>
            <dl className="flex flex-col gap-3">
              {[
                ['Jugador', datos.nombres], ['Categoría', datos.categoria], ['Documento', datos.documento],
                ['Acudiente', `${datos.acudiente} (${datos.parentesco})`], ['Correo', datos.correo],
              ].map(([etiqueta, valor]) => (
                <div key={etiqueta}>
                  <dt className="text-small text-secondary">{etiqueta}</dt>
                  <dd className="break-words text-body-medium">{valor}</dd>
                </div>
              ))}
            </dl>
          </Card>
          <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
            <Button to="/administrador/deportistas">Confirmar inscripción</Button>
            <Button variant="secondary" onClick={() => setPaso(2)}>Atrás</Button>
          </div>
        </div>
      )}
    </Page>
  )
}
