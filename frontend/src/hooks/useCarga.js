import { useEffect, useState } from 'react'

// Carga asíncrona con estados { cargando, datos, error } y `recargar()` para el botón "Reintentar".
// `cargar` debe ser una función ESTABLE (definida fuera del componente), porque es dependencia del efecto.
// `activo: false` no carga nada (por ejemplo, el módulo de la API apagado).
export default function useCarga(cargar, activo = true) {
  const [intento, setIntento] = useState(0)
  const [resultado, setResultado] = useState({ intento: null, datos: null, error: null })

  useEffect(() => {
    if (!activo) return undefined
    let vigente = true
    cargar()
      .then((datos) => vigente && setResultado({ intento, datos, error: null }))
      .catch((error) => vigente && setResultado({ intento, datos: null, error }))
    return () => {
      vigente = false
    }
  }, [cargar, activo, intento])

  return {
    cargando: activo && resultado.intento !== intento,
    datos: resultado.intento === intento ? resultado.datos : null,
    error: resultado.intento === intento ? resultado.error : null,
    recargar: () => setIntento((n) => n + 1),
  }
}
