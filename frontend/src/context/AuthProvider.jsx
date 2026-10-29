import { useMemo, useState } from 'react'
import { AuthContext } from './AuthContext.js'
import { useRole } from './RoleContext.js'
import { VACIA, guardarSesion, leerSesion, limpiarSesion } from './sesion.js'
import * as servicio from '../services/authService.js'

export default function AuthProvider({ children }) {
  const { setRole } = useRole()
  const [sesion, setSesion] = useState(leerSesion)

  const value = useMemo(() => {
    const fijar = (nueva) => {
      setSesion(nueva)
      guardarSesion(nueva)
    }
    // Cierra la sesión de este equipo. NO borra el equipo de confianza (eso es "Dejar de recordar" / cierre global).
    // Tampoco limpia el rol de desarrollo (futdata.rol): ese valor solo alimenta el selector de rol de desarrollo
    // (RoleSwitcher), que debe seguir funcionando sin sesión, y el próximo inicio de sesión lo vuelve a fijar con el
    // rol real de la cuenta. No es un dato de la persona ni un secreto.
    const cerrarSesion = () => {
      servicio.cancelarSolicitud()
      setSesion(limpiarSesion())
    }
    return {
      estado: sesion.estado,
      usuario: sesion.usuario,
      // Paso 1. Si sale bien queda pendiente del código; si este equipo es de confianza, queda verificada de una vez.
      // Devuelve el estado resultante ('pendiente' | 'verificada').
      async iniciarSesion(correo, contrasena) {
        const { verificada, ...usuario } = await servicio.iniciarSesion(correo, contrasena)
        if (verificada) {
          setRole(usuario.rol)
          fijar({ estado: 'verificada', usuario })
          return 'verificada'
        }
        fijar({ estado: 'pendiente', usuario })
        return 'pendiente'
      },
      // Paso 2. Si sale bien la sesión queda verificada y se fija el rol con el mecanismo existente (futdata.rol).
      async verificarCodigo(codigo, recordar) {
        const usuario = await servicio.verificarCodigo(codigo, { recordar })
        setRole(usuario.rol)
        fijar({ estado: 'verificada', usuario })
        return usuario
      },
      estadoCodigo: servicio.estadoCodigo,
      reenviarCodigo: servicio.reenviarCodigo,
      volverAlLogin() {
        servicio.cancelarSolicitud()
        fijar(VACIA)
      },
      cerrarSesion,
      // Cierre global: revoca los equipos de confianza del correo y luego cierra la sesión de este equipo.
      async cerrarSesionGlobal(correo) {
        await servicio.cerrarSesionGlobal(correo)
        cerrarSesion()
      },
      estadoEquipo: servicio.estadoEquipo,
      dejarDeRecordarEquipo: servicio.dejarDeRecordarEquipo,
    }
  }, [sesion, setRole])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
