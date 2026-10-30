import { createContext, useContext } from 'react'

// Preferencias de accesibilidad del equipo (tema y tamaño del texto) y estado del panel que las cambia.
export const AccesibilidadContext = createContext(null)

export const useAccesibilidad = () => useContext(AccesibilidadContext)
