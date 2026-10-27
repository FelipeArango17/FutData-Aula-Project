import { useCallback, useEffect, useMemo, useState } from 'react'
import { EntrenamientosContext } from './EntrenamientosContext.js'
import * as servicio from '../services/entrenamientosService.js'
import { lunesDe } from '../utils/fechas.js'

const avisoDe = (accion, notificados) => {
  const jugadores = notificados === 1 ? '1 jugador' : `${notificados} jugadores`
  return `${accion} Se notificará a ${jugadores} y sus acudientes.`
}

export default function EntrenamientosProvider({ children }) {
  const [entrenamientos, setEntrenamientos] = useState([])
  const [cargado, setCargado] = useState(false)
  const [semana, setSemana] = useState(lunesDe(servicio.HOY_EJEMPLO))
  // aviso = { id, texto }; el id cambia en cada aviso para que la pantalla lo detecte aunque el texto se repita
  const [aviso, setAviso] = useState(null)

  const recargar = useCallback(async () => setEntrenamientos(await servicio.listar()), [])

  useEffect(() => {
    let activo = true
    servicio.listar().then((datos) => {
      if (!activo) return
      setEntrenamientos(datos)
      setCargado(true)
    })
    return () => {
      activo = false
    }
  }, [])

  const value = useMemo(() => {
    const avisar = (texto) => setAviso({ id: Date.now(), texto })

    return {
      entrenamientos,
      cargado,
      semana,
      setSemana,
      aviso,
      cerrarAviso: () => setAviso(null),
      detectarConflicto: servicio.detectarConflicto,
      async crear(datos) {
        const { entrenamiento, notificados } = await servicio.crear(datos)
        await recargar()
        setSemana(lunesDe(entrenamiento.fecha))
        avisar(avisoDe('Entrenamiento creado.', notificados))
      },
      async editar(id, datos) {
        const { entrenamiento, notificados } = await servicio.editar(id, datos)
        await recargar()
        setSemana(lunesDe(entrenamiento.fecha))
        avisar(avisoDe('Entrenamiento actualizado.', notificados))
      },
      async eliminar(id) {
        const { notificados } = await servicio.eliminar(id)
        await recargar()
        avisar(avisoDe('Entrenamiento eliminado.', notificados))
      },
    }
  }, [entrenamientos, cargado, semana, aviso, recargar])

  return <EntrenamientosContext.Provider value={value}>{children}</EntrenamientosContext.Provider>
}
