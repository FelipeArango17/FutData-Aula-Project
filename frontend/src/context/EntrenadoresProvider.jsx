import { useEffect, useMemo, useState } from 'react'
import { EntrenadoresContext } from './EntrenadoresContext.js'
import * as servicio from '../services/entrenadoresService.js'

// Entrenadores y categorías de la API. Registrar y editar son operaciones separadas de las categorías (la API las
// asigna con su propia llamada): así la pantalla puede reintentar solo la asignación si falla. Cada cambio recarga la lista.
export default function EntrenadoresProvider({ children }) {
  const [entrenadores, setEntrenadores] = useState([])
  const [categorias, setCategorias] = useState([])
  // estado: 'cargando' | 'listo' | 'error' (con el error de la API en `error`)
  const [carga, setCarga] = useState({ estado: 'cargando', error: null })
  const [intento, setIntento] = useState(0)
  // aviso = { id, texto }; el id cambia en cada aviso para que la pantalla lo detecte aunque el texto se repita
  const [aviso, setAviso] = useState(null)

  useEffect(() => {
    let activo = true
    servicio.listar().then(
      (datos) => {
        if (!activo) return
        setEntrenadores(datos)
        setCarga({ estado: 'listo', error: null })
      },
      (error) => activo && setCarga({ estado: 'error', error }),
    )
    // Las categorías solo sirven para mostrar quién dirige cada una: si no cargan, el formulario funciona igual
    servicio.listarCategorias().then((datos) => activo && setCategorias(datos), () => {})
    return () => {
      activo = false
    }
  }, [intento])

  const value = useMemo(() => {
    const recargar = async () => {
      const [lista, cats] = await Promise.all([servicio.listar(), servicio.listarCategorias().catch(() => null)])
      setEntrenadores(lista)
      if (cats) setCategorias(cats)
    }
    const avisar = (texto) => setAviso({ id: Date.now(), texto })
    // Ejecuta la operación y recarga las listas, aunque la operación falle (puede haber guardado algo antes de fallar)
    const conRecarga = async (operacion) => {
      try {
        return await operacion()
      } finally {
        await recargar().catch(() => {})
      }
    }

    return {
      entrenadores,
      categorias,
      cargado: carga.estado === 'listo',
      estadoCarga: carga.estado,
      errorCarga: carga.error,
      reintentar() {
        setCarga({ estado: 'cargando', error: null })
        setIntento((n) => n + 1)
      },
      aviso,
      avisar,
      cerrarAviso: () => setAviso(null),
      detectarDuplicado: servicio.detectarDuplicado,
      // Solo los datos personales; devuelven el entrenador guardado. Las categorías van con asignarCategorias.
      crear: (datos) => conRecarga(() => servicio.crearDatos(datos)),
      editar: (id, datos) => conRecarga(() => servicio.editarDatos(id, datos)),
      asignarCategorias: (id, nombres, reasignar = false) => conRecarga(() => servicio.asignarCategorias(id, nombres, reasignar)),
      cambiarEstado: (id, activo) => conRecarga(() => servicio.cambiarEstado(id, activo)),
    }
  }, [entrenadores, categorias, carga, aviso])

  return <EntrenadoresContext.Provider value={value}>{children}</EntrenadoresContext.Provider>
}
