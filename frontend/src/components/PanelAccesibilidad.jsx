import { useEffect, useId, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import Button from './ui/Button.jsx'
import Icon from './Icon.jsx'
import { useAccesibilidad } from '../context/AccesibilidadContext.js'
import {
  ESCALAS,
  TEMAS,
  etiquetasEscala,
  etiquetasTema,
} from '../utils/preferenciasAccesibilidad.js'

const visible = (el) => !!el?.isConnected && el.getClientRects().length > 0

// Opción de radio nativo; la elegida lleva ícono de check además del borde, para no depender solo del color.
// El check va dentro del texto (en línea) y no como columna aparte: a 320 px con letra al 200 % una columna más
// dejaba el texto sin espacio y partía las palabras.
function Opcion({ name, value, checked, onChange, children }) {
  return (
    <label
      className={`flex min-h-11 cursor-pointer items-center gap-2 rounded-md border bg-surface px-2 text-body sm:gap-3 sm:px-3 ${
        checked ? 'border-2 border-focus font-semibold' : ''
      }`}
    >
      <input
        type="radio"
        name={name}
        value={value}
        checked={checked}
        onChange={onChange}
        className="h-5 w-5 shrink-0 accent-brand-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2"
      />
      <span className="min-w-0 flex-1 break-words">
        {children}
        {checked && <Icon name="check" className="ml-1 inline h-5 w-5 align-text-bottom" />}
      </span>
    </label>
  )
}

// Panel de accesibilidad (role="dialog"): tema de color y tamaño del texto, con cambios al instante.
// Se renderiza una sola vez desde AppShell. Mientras está abierto, #root queda inert (el foco no se sale) y Esc lo cierra.
// Al cerrar, el foco vuelve a quien lo abrió; si ese botón ya no existe (cajón móvil), a uno de respaldo:
// el botón de menú de la TopBar o, en escritorio, el botón «Accesibilidad» del Sidebar.
export default function PanelAccesibilidad({ menuButtonRef, accesibilidadSidebarRef }) {
  const { tema, escala, cambiarTema, cambiarEscala, restablecer, panelAbierto, cerrarPanel, quienAbrioRef } = useAccesibilidad()
  const tituloId = useId()
  const tituloRef = useRef(null)
  const [aviso, setAviso] = useState('')

  useEffect(() => {
    if (!panelAbierto) return
    const raiz = document.getElementById('root')
    // Los tres se leen al abrir (TopBar y Sidebar siguen montados): quienAbrio se anotó al pulsar, antes de abrir el panel
    const destinos = [quienAbrioRef.current, menuButtonRef.current, accesibilidadSidebarRef.current]
    const onKeyDown = (event) => {
      if (event.key === 'Escape') cerrarPanel()
    }
    document.addEventListener('keydown', onKeyDown)
    raiz.inert = true
    document.body.style.overflow = 'hidden'
    tituloRef.current?.focus()
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      raiz.inert = false // antes de devolver el foco: un elemento inert no puede recibirlo
      document.body.style.overflow = ''
      const destino = destinos.find(visible)
      destino?.focus()
      setAviso('')
    }
  }, [panelAbierto, cerrarPanel, quienAbrioRef, menuButtonRef, accesibilidadSidebarRef])

  if (!panelAbierto) return null

  return createPortal(
    <div className="fixed inset-0 z-50 flex max-h-app items-center justify-center overflow-y-auto p-2 sm:p-4">
      <div className="absolute inset-0 bg-sidebar opacity-60" onClick={cerrarPanel} aria-hidden="true" />
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={tituloId}
        className="relative flex max-h-full w-full max-w-md flex-col gap-4 overflow-y-auto rounded-lg border bg-surface p-3 sm:p-6"
      >
        <h2 id={tituloId} ref={tituloRef} tabIndex={-1} className="text-h3 focus:outline-none">
          Accesibilidad
        </h2>

        <fieldset className="flex min-w-0 flex-col gap-2">
          <legend className="mb-1 break-words text-body-medium">Tema de color</legend>
          {TEMAS.map((valor) => (
            <Opcion
              key={valor}
              name="tema"
              value={valor}
              checked={tema === valor}
              onChange={() => {
                cambiarTema(valor)
                setAviso(`Tema: ${etiquetasTema[valor]}`)
              }}
            >
              {etiquetasTema[valor]}
            </Opcion>
          ))}
        </fieldset>

        <fieldset className="flex min-w-0 flex-col gap-2">
          <legend className="mb-1 break-words text-body-medium">Tamaño del texto</legend>
          {ESCALAS.map((valor) => (
            <Opcion
              key={valor}
              name="escala"
              value={valor}
              checked={escala === valor}
              onChange={() => {
                cambiarEscala(valor)
                setAviso(`Tamaño del texto: ${etiquetasEscala[valor]} ${valor} %`)
              }}
            >
              {etiquetasEscala[valor]} {valor} %
            </Opcion>
          ))}
        </fieldset>

        {/* El contenedor con el rol siempre está en la página; el mensaje aparece dentro */}
        <div role="status" className="min-h-6 break-words text-small text-secondary">
          {aviso}
        </div>

        <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
          <Button
            variant="secondary"
            onClick={() => {
              restablecer()
              setAviso('Preferencias restablecidas: tema Normal, texto 100 %')
            }}
          >
            Restablecer
          </Button>
          <Button onClick={cerrarPanel}>Cerrar</Button>
        </div>
      </div>
    </div>,
    document.body,
  )
}
