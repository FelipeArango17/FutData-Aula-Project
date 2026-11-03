import { useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import Page from '../../components/ui/Page.jsx'
import FormularioUsuario from '../../components/usuarios/FormularioUsuario.jsx'
import { useActor } from '../../context/useActor.js'
import { crearUsuario, usuariosPorApi } from '../../services/usuariosService.js'

const VOLVER = '/administrador/usuarios'

// Nuevo usuario (HU-004). Con la API real, el backend crea la cuenta en Supabase Auth e invita por correo: el aviso lo afirma solo
// cuando la API respondió 201 (crearUsuario no resuelve antes). En modo ejemplo NO se envía invitación y el aviso no lo afirma.
export default function UsuarioNuevo() {
  const actor = useActor()
  const navigate = useNavigate()
  const titulo = useRef(null)

  useEffect(() => {
    titulo.current?.focus()
  }, [])

  async function guardar(valores) {
    const { usuario } = await crearUsuario(valores, actor)
    const nombre = `${usuario.nombre} ${usuario.apellido}`
    const aviso = usuariosPorApi()
      ? `Usuario ${nombre} creado. Se envió una invitación a ${usuario.correo}.`
      : `Usuario ${nombre} creado. Todavía no se envía ninguna invitación por correo.`
    navigate(VOLVER, { state: { aviso } })
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
