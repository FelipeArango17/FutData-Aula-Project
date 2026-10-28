import { createContext, useContext } from 'react'

// Entrenadores compartidos por las pantallas de gestión de entrenadores (crear y editar se reflejan en la lista).
export const EntrenadoresContext = createContext(null)

export const useEntrenadores = () => useContext(EntrenadoresContext)
