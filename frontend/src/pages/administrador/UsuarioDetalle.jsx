import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Button from '../../components/ui/Button.jsx'
import AvisoEstado from '../../components/ui/AvisoEstado.jsx'
import DialogoConfirmacion from '../../components/ui/DialogoConfirmacion.jsx'
import HistorialCambios from '../../components/ui/HistorialCambios.jsx'
import EstadoUsuario from '../../components/usuarios/EstadoUsuario.jsx'
import RolUsuario from '../../components/usuarios/RolUsuario.jsx'
import GrupoRoles from '../../components/usuarios/GrupoRoles.jsx'
import Correo from '../../components/usuarios/Correo.jsx'
import Mensaje from '../../components/usuarios/Mensaje.jsx'
import { useActor } from '../../context/useActor.js'
import { ETIQUETAS_ROL, cambiarRol, obtenerUsuario, restablecerAcceso, revocarAcceso, usuariosPorApi } from '../../services/usuariosService.js'
import { formatHora, fechaLargaConAnio, momentoLegible, partesMomento } from '../../utils/fechas.js'

// Gestión de un usuario (HU-004): datos, cambio de rol, revocar y restablecer acceso, e historial. Ruta /administrador/usuarios/:id.
// Las rutas fijas (/nuevo y /entrenadores) se declaran antes en pages/index.js; aun así, un id que no es un número se
// trata como "no existe" (el servicio lo rechaza con USUARIO_NO_EXISTE).
// Solo nombre, correo, rol y estado: sin teléfono ni documentos.
const VOLVER = '/administrador/usuarios'
const porApi = usuariosPorApi()
const ETIQUETAS_CAMPO = { rol: 'Rol', activo: 'Estado del acceso' }

function Dato({ etiqueta, children }) {
  return (
    <div className="flex min-w-0 flex-col gap-0.5">
      <dt className="text-small text-secondary">{etiqueta}</dt>
      <dd className="break-words text-body">{children}</dd>
    </div>
  )
}

// El id de la ruta cambia la pantalla completa (clave): así los avisos y las selecciones no se arrastran entre usuarios
export default function UsuarioDetalle() {
  const { id } = useParams()
  return <Detalle key={id} id={id} />
}

function Detalle({ id }) {
  const actor = useActor()
  const tituloRef = useRef(null)
  const enVuelo = useRef(false)
  const [intento, setIntento] = useState(0)
  // Resultado con la clave de la petición que lo produjo: si no coincide, está cargando
  const [carga, setCarga] = useState({ intento: -1, datos: null, error: null })
  const [rolElegido, setRolElegido] = useState(null) // null = el rol actual del usuario
  const [ocupado, setOcupado] = useState(false)
  const [dialogo, setDialogo] = useState(null) // 'revocar' | 'restablecer' | null
  const [exito, setExito] = useState(null)
  const [fallo, setFallo] = useState(null)

  useEffect(() => {
    tituloRef.current?.focus()
  }, [])

  useEffect(() => {
    let activo = true
    obtenerUsuario(id, actor)
      .then((datos) => activo && setCarga({ intento, datos, error: null }))
      .catch((error) => activo && setCarga({ intento, datos: null, error }))
    return () => {
      activo = false
    }
  }, [id, actor, intento])

  const cargando = carga.intento !== intento
  const usuario = carga.datos?.usuario
  const nombre = usuario ? `${usuario.nombre} ${usuario.apellido}` : ''
  const rolActual = rolElegido ?? usuario?.rol

  // Una sola acción a la vez (sin doble envío): éxito -> datos nuevos y aviso; error -> mensaje claro del servicio
  async function ejecutar(accion, textoExito) {
    if (enVuelo.current) return
    enVuelo.current = true
    setOcupado(true)
    setExito(null)
    setFallo(null)
    try {
      const datos = await accion()
      setCarga((previa) => ({ ...previa, datos }))
      setRolElegido(null)
      setExito({ texto: textoExito })
    } catch (error) {
      const claros = ['AUTOACCESO', 'ULTIMO_ADMINISTRADOR', 'SIN_CAMBIOS', 'SIN_PERMISO', 'USUARIO_NO_EXISTE', 'VALIDACION', 'CONFLICTO']
      setFallo({ texto: claros.includes(error.codigo) ? (error.errores?.rol ?? error.message) : 'No se pudo completar la acción. Intenta de nuevo.' })
    } finally {
      enVuelo.current = false
      setOcupado(false)
    }
  }

  const guardarRol = (e) => {
    e.preventDefault()
    return ejecutar(() => cambiarRol(id, rolActual, actor), `Rol cambiado a ${ETIQUETAS_ROL[rolActual]}.`)
  }
  const confirmar = (tipo) => {
    setDialogo(null)
    if (tipo === 'revocar') ejecutar(() => revocarAcceso(id, actor), 'Acceso revocado.')
    else ejecutar(() => restablecerAcceso(id, actor), 'Acceso restablecido.')
  }

  const volver = <Button to={VOLVER} variant="secondary">Volver a usuarios</Button>
  const noExiste = carga.error?.codigo === 'USUARIO_NO_EXISTE'
  const sinPermiso = carga.error?.codigo === 'SIN_PERMISO'

  // Historial: ya viene con el más reciente primero y con roles y estados legibles
  const entradas = (carga.datos?.historial ?? []).map((entrada, i) => ({
    clave: `${entrada.fechaHora}-${i}`,
    encabezado: <>{momentoLegible(entrada.fechaHora)} · {entrada.responsable ? <Correo correo={entrada.responsable} /> : 'Sin responsable'}</>,
    lineas: entrada.cambios.map((c) => ({ clave: c.campo, texto: `${ETIQUETAS_CAMPO[c.campo]}: ${c.antes} → ${c.despues}` })),
  }))

  let contenido
  if (cargando) {
    contenido = (
      <Mensaje icono="clock">
        <p role="status">Cargando el usuario…</p>
      </Mensaje>
    )
  } else if (noExiste || sinPermiso) {
    contenido = (
      <Mensaje icono="alert" rol="alert">
        <p>{noExiste ? 'No encontramos este usuario.' : 'No tienes permiso para gestionar usuarios.'}</p>
        {volver}
      </Mensaje>
    )
  } else if (carga.error) {
    contenido = (
      <AvisoEstado
        aviso={{ texto: 'No se pudo cargar el usuario.', accion: { texto: 'Reintentar', onClick: () => setIntento((n) => n + 1) } }}
        rol="alert"
        tono="danger"
        enfocar={false}
      />
    )
  } else {
    // No revocarse a uno mismo: se compara con la sesión real. Lo demás (último administrador) lo decide el backend con un 409.
    const esYo = porApi && Boolean(actor.correo) && usuario.correo.toLowerCase() === actor.correo.toLowerCase()
    const registro = usuario.creadoPor && usuario.creadoEn ? partesMomento(usuario.creadoEn) : null
    contenido = (
      <>
        <Card aria-labelledby="datos-usuario" className="flex w-full max-w-2xl flex-col gap-4">
          <h2 id="datos-usuario" className="break-words text-h3">Datos</h2>
          <dl className="grid grid-cols-[repeat(auto-fit,minmax(min(100%,14rem),1fr))] gap-4">
            <Dato etiqueta="Nombres">{usuario.nombre}</Dato>
            <Dato etiqueta="Apellidos">{usuario.apellido}</Dato>
            <Dato etiqueta="Correo electrónico"><Correo correo={usuario.correo} /></Dato>
            <Dato etiqueta="Rol"><RolUsuario rol={usuario.rol} /></Dato>
            <Dato etiqueta="Estado del acceso"><EstadoUsuario activo={usuario.activo} /></Dato>
          </dl>
          {registro && (
            <p className="break-words text-small text-secondary">
              Creado por <Correo correo={usuario.creadoPor} /> el {fechaLargaConAnio(registro.fecha)} a las {formatHora(registro.hora)}
            </p>
          )}
          {usuario.rol === 'entrenador' && (
            <Button to="/administrador/usuarios/entrenadores" variant="secondary" className="sm:self-start">Gestionar entrenadores</Button>
          )}
        </Card>

        <Card as="form" noValidate onSubmit={guardarRol} aria-labelledby="titulo-cambiar-rol" className="flex w-full max-w-2xl flex-col gap-4">
          <h2 id="titulo-cambiar-rol" className="break-words text-h3">Cambiar rol</h2>
          <GrupoRoles id="cambiar-rol" legend="Rol del usuario" value={rolActual} onChange={setRolElegido} disabled={ocupado} />
          <Button type="submit" disabled={ocupado || rolActual === usuario.rol} aria-busy={ocupado} className="disabled:opacity-60 sm:self-start">
            {ocupado ? 'Guardando…' : 'Guardar rol'}
          </Button>
        </Card>

        <Card aria-labelledby="titulo-acceso" className="flex w-full max-w-2xl flex-col gap-4">
          <h2 id="titulo-acceso" className="break-words text-h3">Acceso</h2>
          <p className="break-words text-body text-secondary">
            {usuario.activo
              ? 'Al revocar el acceso, el usuario deja de poder iniciar sesión. No se borra nada.'
              : 'Este usuario no puede iniciar sesión. Puedes restablecer su acceso cuando quieras.'}
          </p>
          {usuario.activo && esYo ? (
            <p className="break-words text-small text-secondary">No puedes revocar tu propio acceso.</p>
          ) : usuario.activo ? (
            <Button variant="danger" disabled={ocupado} onClick={() => setDialogo('revocar')} className="disabled:opacity-60 sm:self-start">Revocar acceso</Button>
          ) : (
            <Button disabled={ocupado} onClick={() => setDialogo('restablecer')} className="disabled:opacity-60 sm:self-start">Restablecer acceso</Button>
          )}
        </Card>

        {/* La API no ofrece historial de cambios: en modo API no se muestra */}
        {!porApi && (
          <div className="w-full max-w-2xl">
            <HistorialCambios entradas={entradas} vacio="Todavía no hay cambios registrados." />
          </div>
        )}
      </>
    )
  }

  return (
    <Page title={usuario ? nombre : 'Usuario'} subtitle={usuario ? 'Gestión de usuario' : undefined} tituloRef={tituloRef}>
      <AvisoEstado aviso={exito} onCerrar={() => setExito(null)} />
      <AvisoEstado aviso={fallo} onCerrar={() => setFallo(null)} rol="alert" tono="danger" />
      {contenido}
      {usuario && !cargando && <div className="w-full max-w-2xl">{volver}</div>}

      <DialogoConfirmacion
        open={dialogo === 'revocar'}
        titulo="¿Revocar el acceso?"
        descripcion={porApi
          ? `${nombre} dejará de poder iniciar sesión y se cerrarán todas sus sesiones abiertas. No se borra nada: puedes restablecer su acceso después.`
          : `${nombre} dejará de poder iniciar sesión. No se borra nada: sus datos y su historial se conservan y puedes restablecer su acceso después.`}
        textoConfirmar="Sí, revocar acceso"
        onConfirmar={() => confirmar('revocar')}
        onCancelar={() => setDialogo(null)}
      />
      <DialogoConfirmacion
        open={dialogo === 'restablecer'}
        titulo="¿Restablecer el acceso?"
        descripcion={`${nombre} podrá volver a iniciar sesión como ${usuario ? ETIQUETAS_ROL[usuario.rol] : ''}.${porApi ? '' : ' Esta versión no envía ningún correo.'}`}
        textoConfirmar="Sí, restablecer acceso"
        onConfirmar={() => confirmar('restablecer')}
        onCancelar={() => setDialogo(null)}
      />
    </Page>
  )
}
