import CamposDeportista from '../deportistas/CamposDeportista.jsx'

// Paso 1: los datos del jugador. Reutiliza CamposDeportista tal cual (categoría calculada y de solo lectura, grupo solo en
// Cachorros y Semillero, posición opcional). Formulario en blanco: sin datos escritos de antemano.
// props: jugador (valores), errores ({ campo: mensaje }), onCambio(campo, valor)
export default function PasoJugador({ jugador, errores, onCambio }) {
  const texto = (campo) => ({ value: jugador[campo], error: errores[campo], onChange: (e) => onCambio(campo, e.target.value) })
  return <CamposDeportista texto={texto} fechaNacimiento={jugador.fechaNacimiento} />
}
