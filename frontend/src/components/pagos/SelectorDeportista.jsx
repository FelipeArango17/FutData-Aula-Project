import { useEffect, useId, useRef, useState } from 'react'
import Button from '../ui/Button.jsx'
import Field from '../ui/Field.jsx'
import { MAX_RESULTADOS, buscarDeportistas, opcionAlPulsarEnter } from '../../utils/buscarDeportistas.js'

// Combobox para elegir un deportista escribiendo parte del nombre o del documento (patrón ARIA "combobox con listbox").
// El filtrado es en el navegador sobre la lista ya cargada (utils/buscarDeportistas.js). La lista se despliega EN LÍNEA (no flota),
// así nunca se sale de la pantalla en móvil. Teclado: flechas mueven, Enter elige (sin flechas, solo si hay una única opción), Escape cierra, Tab sale.
// props: deportistas (lista adaptada), value (id elegido o ''), onChange(id), cargando, error (mensaje de validación),
//        label, id, errorDeCarga (true si la lista no se pudo cargar: el aviso con «Reintentar» lo dibuja quien lo usa)
const nombreCompleto = (d) => `${d.nombre} ${d.apellido}`
const textoDocumento = (d) => (d.documento ? `${d.tipoDocumento} ${d.documento}` : 'Sin documento')
const textoCategoria = (d) => d.categoria ?? 'Sin categoría'

export default function SelectorDeportista({ deportistas, value, onChange, cargando = false, error, errorDeCarga = false, label = 'Deportista', id = 'idDeportista' }) {
  const listaId = useId()
  const ayudaId = `${id}-ayuda`
  const entrada = useRef(null)
  const enfocarAlCambiar = useRef(false)
  const [texto, setTexto] = useState('')
  const [abierto, setAbierto] = useState(false)
  const [activa, setActiva] = useState(-1)
  const elegido = value ? deportistas.find((d) => d.id === value) : null
  const { resultados, total } = buscarDeportistas(deportistas, texto)
  const opcionId = (i) => `${listaId}-op-${i}`

  useEffect(() => {
    if (activa >= 0) document.getElementById(`${listaId}-op-${activa}`)?.scrollIntoView?.({ block: 'nearest' })
  }, [activa, listaId])
  useEffect(() => {
    if (!elegido && enfocarAlCambiar.current) {
      enfocarAlCambiar.current = false
      entrada.current?.focus()
    }
  }, [elegido])

  if (elegido) {
    return (
      <div className="flex min-w-0 flex-col gap-3">
        <Field label={label} id={id} requerido readOnly value={nombreCompleto(elegido)} error={error} />
        <div className="flex min-w-0 flex-wrap items-start gap-3 rounded-lg border bg-app p-3" aria-label="Deportista elegido" role="group">
          <dl className="grid min-w-0 flex-1 basis-48 grid-cols-1 gap-2">
            <div className="min-w-0"><dt className="text-small text-secondary">Nombre</dt><dd className="break-words text-body-medium">{nombreCompleto(elegido)}</dd></div>
            <div className="min-w-0"><dt className="text-small text-secondary">Documento</dt><dd className="break-words text-body">{textoDocumento(elegido)}</dd></div>
            <div className="min-w-0"><dt className="text-small text-secondary">Categoría</dt><dd className="break-words text-body">{textoCategoria(elegido)}</dd></div>
          </dl>
          <Button variant="secondary" aria-label={`Cambiar el deportista: ${nombreCompleto(elegido)}`} onClick={() => { enfocarAlCambiar.current = true; setTexto(''); setActiva(-1); onChange('') }}>Cambiar</Button>
        </div>
      </div>
    )
  }

  const sinLista = cargando || deportistas.length === 0
  const hayLista = abierto && !sinLista
  const estado = cargando ? 'Cargando deportistas…' : errorDeCarga ? null : deportistas.length === 0 ? 'No hay deportistas activos' : null

  function elegir(d) {
    onChange(d.id)
    setTexto('')
    setAbierto(false)
    setActiva(-1)
  }

  function alTeclear(event) {
    if (sinLista) return
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      if (!abierto) return setAbierto(true)
      setActiva((i) => Math.min(i + 1, resultados.length - 1))
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      if (!abierto) return setAbierto(true)
      setActiva((i) => Math.max(i - 1, 0))
    } else if (event.key === 'Enter' && abierto) {
      event.preventDefault() // no envía el formulario
      const opcion = opcionAlPulsarEnter(resultados, activa)
      if (opcion) elegir(opcion)
    } else if (event.key === 'Escape' && abierto) {
      event.preventDefault()
      setAbierto(false)
      setActiva(-1)
    }
  }

  return (
    <div className="flex min-w-0 flex-col gap-1">
      <Field
        label={label}
        id={id}
        requerido
        ref={entrada}
        role="combobox"
        aria-expanded={hayLista}
        aria-controls={hayLista ? listaId : undefined}
        aria-autocomplete="list"
        aria-activedescendant={hayLista && activa >= 0 ? opcionId(activa) : undefined}
        describedBy={ayudaId}
        autoComplete="off"
        disabled={sinLista}
        placeholder={cargando ? 'Cargando deportistas…' : 'Escribe el nombre o el documento'}
        value={texto}
        error={error}
        onChange={(e) => { setTexto(e.target.value); setAbierto(true); setActiva(-1) }}
        onFocus={() => setAbierto(true)}
        onBlur={() => { setAbierto(false); setActiva(-1) }}
        onKeyDown={alTeclear}
      />
      <p id={ayudaId} className="break-words text-small text-secondary">Busca por nombre, apellido o número de documento.</p>
      {estado && <p role="status" className="break-words text-body">{estado}</p>}
      {hayLista && (
        <div className="min-w-0 rounded-md border bg-surface">
          <ul id={listaId} role="listbox" aria-label="Deportistas que coinciden" className="max-h-72 overflow-y-auto">
            {resultados.map((d, i) => (
              <li
                key={d.id}
                id={opcionId(i)}
                role="option"
                aria-selected={i === activa}
                // mousedown no le quita el foco al campo: el clic llega antes de que se cierre la lista
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => elegir(d)}
                className={`flex min-h-11 min-w-0 cursor-pointer flex-col justify-center gap-0.5 border-b px-3 py-2 last:border-b-0 ${i === activa ? 'bg-app' : ''}`}
              >
                <span className="break-words text-body-medium">{nombreCompleto(d)}</span>
                <span className="break-words text-small text-secondary">{textoDocumento(d)} · {textoCategoria(d)}</span>
              </li>
            ))}
          </ul>
          {total === 0 && <p role="status" className="break-words p-3 text-body">No hay deportistas con ese nombre o documento</p>}
          {total > MAX_RESULTADOS && <p role="status" className="break-words border-t p-3 text-small text-secondary">Mostrando {MAX_RESULTADOS} de {total}. Escribe más para afinar</p>}
        </div>
      )}
    </div>
  )
}
