import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import AuthLayout from '../../components/auth/AuthLayout.jsx'
import Button from '../../components/ui/Button.jsx'
import AvisoEstado from '../../components/ui/AvisoEstado.jsx'
import FormularioInscripcion from '../../components/inscripcion/FormularioInscripcion.jsx'
import { crearInscripcion } from '../../services/inscripcionService.js'

// Inscripción en línea (HU-009, tarea 316). Pantalla PÚBLICA /inscripcion: sin sesión y fuera del AppShell (sin menú).
// Figma no tiene una pantalla de inscripción en línea del acudiente: su ficha dice que "la inscripción se hace en secretaría",
// lo que choca con esta historia. Se construyó con los componentes del proyecto.
// La respuesta es siempre la misma, esté o no registrado el documento (no se revela qué hay registrado).
export default function Inscripcion() {
  const navigate = useNavigate()
  const titulo = useRef(null)
  const [recibida, setRecibida] = useState(null) // { aviso } cuando ya se envió
  const [formulario, setFormulario] = useState(0) // cambia la clave para empezar un formulario en blanco

  // Al abrir, y al empezar otra inscripción, el foco va al título
  useEffect(() => {
    titulo.current?.focus()
  }, [formulario])

  async function guardar(valores) {
    const { mensaje } = await crearInscripcion(valores)
    setRecibida({ aviso: { texto: mensaje } })
  }

  function inscribirOtro() {
    setRecibida(null)
    setFormulario((n) => n + 1)
  }

  return (
    <AuthLayout variante="centrado" ancho="amplio">
      <div className="flex flex-col gap-6 py-4">
        <div className="flex flex-col gap-1">
          <h1 ref={titulo} tabIndex={-1} className="break-words text-display focus:outline-none">Inscripción en línea</h1>
          <p className="break-words text-body text-secondary">
            Inscribe a un deportista sin ir al club. Completa los datos y el club se comunicará contigo.
          </p>
        </div>
        {recibida ? (
          <div className="flex flex-col gap-4">
            <AvisoEstado aviso={recibida.aviso} />
            <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
              <Button onClick={inscribirOtro}>Inscribir a otro deportista</Button>
              <Button to="/login" variant="secondary">Ir al inicio de sesión</Button>
            </div>
          </div>
        ) : (
          <FormularioInscripcion key={formulario} onGuardar={guardar} onCancelar={() => navigate('/login')} />
        )}
      </div>
    </AuthLayout>
  )
}
