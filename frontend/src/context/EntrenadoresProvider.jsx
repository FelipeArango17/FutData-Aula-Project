import { useEffect, useMemo, useState } from 'react'
import { EntrenadoresContext } from './EntrenadoresContext.js'
import * as servicio from '../services/entrenadoresService.js'

export default function EntrenadoresProvider({ children }) {
  const [entrenadores, setEntrenadores] = useState([])
  const [cargado, setCargado] = useState(false)
  // aviso = { id, texto }; el id cambia en cada aviso para que la pantalla lo detecte aunque el texto se repita
  const [aviso, setAviso] = useState(null)

  useEffect(() => {
    let activo = true
    servicio.listar().then((datos) => {
      if (!activo) return
      setEntrenadores(datos)
      setCargado(true)
    })
    return () => {
      activo = false
    }
  }, [])

  const value = useMemo(() => {
    const recargar = async () => setEntrenadores(await servicio.listar())
    const avisar = (texto) => setAviso({ id: Date.now(), texto })

    return {
      entrenadores,
      cargado,
      aviso,
      cerrarAviso: () => setAviso(null),
      detectarDuplicado: servicio.detectarDuplicado,
      async crear(datos) {
        await servicio.crear(datos)
        await recargar()
        avisar('Entrenador registrado.')
      },
      async editar(id, datos) {
        await servicio.editar(id, datos)
        await recargar()
        avisar('Entrenador actualizado.')
      },
    }
  }, [entrenadores, cargado, aviso])

  return <EntrenadoresContext.Provider value={value}>{children}</EntrenadoresContext.Provider>
}
