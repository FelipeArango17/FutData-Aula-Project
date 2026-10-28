import { useEffect, useRef } from 'react'
import { Navigate, useNavigate, useParams } from 'react-router-dom'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import FormularioEntrenador from '../../components/entrenadores/FormularioEntrenador.jsx'
import { useEntrenadores } from '../../context/EntrenadoresContext.js'

const VOLVER = '/administrador/usuarios'
const VACIO = {
  nombre: '', apellido: '', tipoDocumento: '', documento: '', telefono: '', correo: '',
  especialidad: '', categorias: [], horarioTrabajo: '',
}

// Registrar (.../entrenadores/nuevo) y editar (.../entrenadores/:id/editar) un entrenador (HU-025).
export default function EntrenadorFormulario() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { entrenadores, cargado, crear, editar, detectarDuplicado } = useEntrenadores()
  const titulo = useRef(null)
  const editando = id ? entrenadores.find((e) => String(e.id) === id) : null
  const listo = !id || Boolean(editando)

  // Al abrir, el foco va al título
  useEffect(() => {
    if (listo) titulo.current?.focus()
  }, [listo, editando?.id])

  // Un id que no existe vuelve a la lista de usuarios
  if (id && cargado && !editando) return <Navigate to={VOLVER} replace />
  if (!listo) return null

  async function guardar(valores) {
    if (editando) await editar(editando.id, valores)
    else await crear(valores)
    navigate(VOLVER)
  }

  return (
    <Page title={editando ? 'Editar entrenador' : 'Registrar entrenador'} tituloRef={titulo}>
      <Card className="w-full max-w-2xl">
        <FormularioEntrenador
          key={editando?.id ?? 'nuevo'}
          inicial={editando ? { ...VACIO, ...editando } : VACIO}
          ignorarId={editando?.id ?? null}
          detectarDuplicado={detectarDuplicado}
          onGuardar={guardar}
          onCancelar={() => navigate(VOLVER)}
        />
      </Card>
    </Page>
  )
}
