import { useEffect, useState } from 'react'
import { obtenerFotos } from '../services/fotosService.js'

// Fotos de los deportistas para una lista: { idDeportista: url }. Hace UNA consulta al abrir la pantalla (no una por fila), así
// que al volver de la ficha se ve la foto nueva. Mientras llega, o si falla, el mapa está vacío y el Avatar muestra las iniciales.
export function useFotosDeportistas() {
  const [fotos, setFotos] = useState({})
  useEffect(() => {
    let activo = true
    obtenerFotos()
      .then((mapa) => activo && setFotos(mapa))
      .catch(() => activo && setFotos({}))
    return () => {
      activo = false
    }
  }, [])
  return fotos
}
