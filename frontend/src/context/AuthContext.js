import { createContext, useContext } from 'react'

// Sesión de la persona: estado = 'sin-sesion' | 'pendiente' (credenciales válidas, falta el código 2FA) | 'verificada'.
export const AuthContext = createContext(null)

export const useAuth = () => useContext(AuthContext)
