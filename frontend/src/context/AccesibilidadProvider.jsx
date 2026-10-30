import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { AccesibilidadContext } from './AccesibilidadContext.js'
import {
  POR_DEFECTO,
  aplicarPreferencias,
  guardarPreferencias,
  leerPreferencias,
} from '../utils/preferenciasAccesibilidad.js'

// Las preferencias son del EQUIPO, no de la persona: cerrar sesión no las borra.
export default function AccesibilidadProvider({ children }) {
  const [prefs, setPrefs] = useState(leerPreferencias)
  const [panelAbierto, setPanelAbierto] = useState(false)
  // Quién abrió el panel, anotado al pulsar: si fue un botón del cajón móvil, cuando el panel cierre ya no existirá
  const quienAbrioRef = useRef(null)

  useEffect(() => {
    aplicarPreferencias(prefs)
    guardarPreferencias(prefs)
  }, [prefs])

  const cambiarTema = useCallback((tema) => setPrefs((actual) => ({ ...actual, tema })), [])
  const cambiarEscala = useCallback((escala) => setPrefs((actual) => ({ ...actual, escala })), [])
  const restablecer = useCallback(() => setPrefs({ ...POR_DEFECTO }), [])
  const abrirPanel = useCallback(() => {
    quienAbrioRef.current = document.activeElement
    setPanelAbierto(true)
  }, [])
  const cerrarPanel = useCallback(() => setPanelAbierto(false), [])

  const valor = useMemo(
    () => ({ ...prefs, cambiarTema, cambiarEscala, restablecer, panelAbierto, abrirPanel, cerrarPanel, quienAbrioRef }),
    [prefs, cambiarTema, cambiarEscala, restablecer, panelAbierto, abrirPanel, cerrarPanel],
  )

  return <AccesibilidadContext.Provider value={valor}>{children}</AccesibilidadContext.Provider>
}
