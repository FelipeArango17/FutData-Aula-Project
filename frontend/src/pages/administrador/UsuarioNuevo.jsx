import { useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import Page from '../../components/ui/Page.jsx'
import FormularioUsuario from '../../components/usuarios/FormularioUsuario.jsx'
import { useActor } from '../../context/useActor.js'
import { crearUsuario } from '../../services/usuariosService.js'

const VOLVER = '/administrador/usuarios'

// Nuevo usuario (HU-004). PENDIENTE: no se envía invitación (Supabase Auth + correo de n8n), así que el aviso no lo afirma.
export default function UsuarioNuevo() {
  const actor = useActor()
  const navigate = useNavigate()
  const titulo = useRef(null)

  useEffect(() => {
    titulo.current?.focus()
  }, [])

  async function guardar(valores) {
    const { usuario } = await crearUsuario(valores, actor)
    navigate(VOLVER, { state: { aviso: `Usuario ${usuario.nombre} ${usuario.apellido} creado. Todavía no se envía ninguna invitación por correo.` } })
  }

  return (
    <Page title="Nuevo usuario" subtitle="Crea una cuenta y elige qué puede hacer" tituloRef={titulo}>
      {/* En pantallas angostas la tarjeta no lleva borde ni relleno: así el aviso de errores y los campos no anidan relleno */}
      <div className="w-full max-w-2xl sm:rounded-lg sm:border sm:bg-surface sm:p-4">
        <FormularioUsuario onGuardar={guardar} onCancelar={() => navigate(VOLVER)} />
      </div>
    </Page>
  )
}
