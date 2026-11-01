import { useEffect, useMemo, useRef, useState } from 'react'
import { AuthContext } from './AuthContext.js'
import { VACIA, guardarSesion, leerSesion, limpiarSesion } from './sesion.js'
import { configurarAlNoAutorizado } from '../services/apiClient.js'
import { servicio } from '../services/auth.js'

export default function AuthProvider({ children }) {
  const [sesion, setSesion] = useState(leerSesion)
  // true cuando la sesión de Supabase de una recarga o de otra pestaña vuelve al paso del código (ver Login)
  const [retomarCodigo, setRetomarCodigo] = useState(false)
  const estadoInicial = useRef(sesion.estado)
  // false mientras se comprueba la sesión con el servicio real; las rutas protegidas esperan (no entran ni redirigen antes).
  // El servicio simulado no restaura nada: está listo desde el primer render.
  const [restaurada, setRestaurada] = useState(() => !servicio.restaurarSesion)

  // Solo con el servicio real (Supabase): al cargar, comprueba que la sesión siga vigente; si hay sesión pero esta
  // pestaña no completó el 2FA (o el backend ya no la da por verificada), vuelve al paso del código. Con la sesión
  // verificada, actualiza el perfil (nombre y rol). Un 401 del backend o un cierre de sesión hecho en otra pestaña
  // limpian el estado y las rutas llevan a /login.
  useEffect(() => {
    const cerrar = () => setSesion(limpiarSesion())
    configurarAlNoAutorizado(cerrar)
    const darDeBaja = servicio.alCerrarSesionExterna?.(cerrar)
    let activo = true
    servicio.restaurarSesion?.({ verificada: estadoInicial.current === 'verificada' }).then((restaurada) => {
      if (!activo) return
      if (!restaurada) return cerrar()
      if (restaurada.estado === 'verificada' && restaurada.usuario) {
        const actualizada = { estado: 'verificada', usuario: restaurada.usuario }
        setSesion(actualizada)
        guardarSesion(actualizada)
      }
      if (restaurada.estado === 'pendiente') {
        const nueva = { estado: 'pendiente', usuario: restaurada.usuario }
        setSesion(nueva)
        guardarSesion(nueva)
        setRetomarCodigo(true)
      }
    }, () => {}).finally(() => activo && setRestaurada(true))
    return () => {
      activo = false
      configurarAlNoAutorizado(null)
      darDeBaja?.()
    }
  }, [])

  const value = useMemo(() => {
    const fijar = (nueva) => {
      setSesion(nueva)
      guardarSesion(nueva)
    }
    // Cierra la sesión de este equipo. NO borra el equipo de confianza (eso es "Dejar de recordar" / cierre global).
    // Borra también el rol de desarrollo (futdata.rol): el rol activo sale siempre de la sesión.
    // Con el servicio real avisa al backend (POST /api/auth/logout) antes de cerrar Supabase; no se espera: la pantalla
    // vuelve al login enseguida y un fallo del backend no impide cerrar.
    const cerrarSesion = () => {
      ;(servicio.cerrarSesion ?? servicio.cancelarSolicitud)()
      setSesion(limpiarSesion())
    }
    return {
      estado: sesion.estado,
      restaurada,
      usuario: sesion.usuario,
      retomarCodigo,
      // Paso 1. Si sale bien queda pendiente del código; si este equipo es de confianza, queda verificada de una vez.
      // Devuelve el estado resultante ('pendiente' | 'verificada').
      async iniciarSesion(correo, contrasena) {
        const { verificada, ...usuario } = await servicio.iniciarSesion(correo, contrasena)
        if (verificada) {
          fijar({ estado: 'verificada', usuario })
          return 'verificada'
        }
        fijar({ estado: 'pendiente', usuario })
        return 'pendiente'
      },
      // Paso 2. Si sale bien la sesión queda verificada y el rol activo sale de usuario.rol (RoleProvider).
      async verificarCodigo(codigo, recordar) {
        const usuario = await servicio.verificarCodigo(codigo, { recordar })
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
  }, [sesion, retomarCodigo, restaurada])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
