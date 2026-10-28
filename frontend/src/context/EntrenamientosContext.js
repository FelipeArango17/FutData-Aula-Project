import { createContext, useContext } from 'react'

// Entrenamientos compartidos por todas las pantallas del rol (crear, editar y eliminar se reflejan en todas).
export const EntrenamientosContext = createContext(null)

export const useEntrenamientos = () => useContext(EntrenamientosContext)
