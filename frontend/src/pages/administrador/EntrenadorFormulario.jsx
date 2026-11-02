import { useEffect, useRef } from 'react'
import { Navigate, useNavigate, useParams } from 'react-router-dom'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import AvisoEstado from '../../components/ui/AvisoEstado.jsx'
import FormularioEntrenador from '../../components/entrenadores/FormularioEntrenador.jsx'
import Mensaje from '../../components/usuarios/Mensaje.jsx'
import { avisoDeAlta } from '../../utils/avisosEntrenadores.js'
import { useEntrenadores } from '../../context/EntrenadoresContext.js'

const VOLVER = '/administrador/usuarios/entrenadores' // la lista de entrenadores (antes era /administrador/usuarios)
const VACIO = {
  nombre: '', apellido: '', tipoDocumento: '', documento: '', fechaNacimiento: '', telefono: '', correo: '',
  especialidad: '', categorias: [], horarioTrabajo: '', direccion: '',
}

// Registrar (.../entrenadores/nuevo) y editar (.../entrenadores/:id/editar) un entrenador (HU-025).
export default function EntrenadorFormulario() {
  const { id } = useParams()
  const navigate = useNavigate()
  const {
    entrenadores, categorias, cargado, estadoCarga, errorCarga, reintentar,
    crear, editar, asignarCategorias, avisar, detectarDuplicado,
  } = useEntrenadores()
  const titulo = useRef(null)
  const correoEnviado = useRef(null) // resultado del envío del enlace en el alta (true/false); null si no aplica
  const correoCreado = useRef('')
  const editando = id ? entrenadores.find((e) => String(e.id) === id) : null
  const listo = !id || Boolean(editando)

  // Al abrir, el foco va al título
  useEffect(() => {
    if (listo) titulo.current?.focus()
  }, [listo, editando?.id])

  // Un id que no existe vuelve a la lista de entrenadores
  if (id && cargado && !editando) return <Navigate to={VOLVER} replace />
  if (!listo) {
    // Al editar con la lista sin cargar (abierto directo o con error): estado de carga con reintento
    return (
      <Page title="Editar entrenador">
        {estadoCarga === 'error' ? (
          <AvisoEstado
            aviso={{
              id: 'error-carga',
              texto: errorCarga?.estado === 403 ? errorCarga.mensaje : 'No se pudo cargar el entrenador.',
              accion: errorCarga?.estado === 403 ? undefined : { texto: 'Reintentar', onClick: reintentar },
            }}
            rol="alert"
            tono="danger"
            enfocar={false}
          />
        ) : (
          <Mensaje icono="clock">
            <p role="status">Cargando entrenador…</p>
          </Mensaje>
        )}
      </Page>
    )
  }

  const guardar = async (valores) => {
    if (editando) return editar(editando.id, valores)
    const creado = await crear(valores)
    correoEnviado.current = creado.correoEnviado ?? null
    correoCreado.current = creado.correo ?? valores.correo.trim()
    return creado
  }
  const terminar = () => {
    if (editando) avisar('Entrenador actualizado.')
    else {
      const { texto, tono } = avisoDeAlta(correoEnviado.current, correoCreado.current)
      avisar(texto, tono)
    }
    navigate(VOLVER)
  }

  return (
    <Page title={editando ? 'Editar entrenador' : 'Registrar entrenador'} tituloRef={titulo}>
      <Card className="w-full max-w-2xl">
        <FormularioEntrenador
          key={editando?.id ?? 'nuevo'}
          inicial={editando ? { ...VACIO, ...editando } : VACIO}
          ignorarId={editando?.id ?? null}
          categorias={categorias}
          detectarDuplicado={detectarDuplicado}
          onGuardar={guardar}
          onAsignarCategorias={asignarCategorias}
          onTerminar={terminar}
          onCancelar={() => navigate(VOLVER)}
        />
      </Card>
    </Page>
  )
}
