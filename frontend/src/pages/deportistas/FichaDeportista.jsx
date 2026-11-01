import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Button from '../../components/ui/Button.jsx'
import AvisoEstado from '../../components/ui/AvisoEstado.jsx'
import HistorialCambios from '../../components/ui/HistorialCambios.jsx'
import Icon from '../../components/Icon.jsx'
import DocumentosDeportista from '../../components/deportistas/DocumentosDeportista.jsx'
import SubidaFoto from '../../components/ui/SubidaFoto.jsx'
import EstadoDeportista from '../../components/deportistas/EstadoDeportista.jsx'
import FormularioDeportista from '../../components/deportistas/FormularioDeportista.jsx'
import { useActor } from '../../context/useActor.js'
import { actualizarDeportista, calcularEdad, listarCategorias, obtenerFichaDeportista } from '../../services/deportistasService.js'
import { guardarFoto, obtenerFoto, quitarFoto } from '../../services/fotosService.js'
import { fechaLargaConAnio, formatHora, momentoLegible, partesMomento } from '../../utils/fechas.js'

// Ficha del deportista (HU-010, tarea 319): lectura y edición. Ruta /administrador/deportistas/:id. Solo el administrador.
// Figma no tiene una ficha del administrador (la del acudiente es otra pantalla), así que usa los componentes del proyecto.
// En lectura tiene la foto (arriba) y los documentos (debajo de los datos personales); el formulario de edición no cambia.
// PENDIENTE: pasar al deportista a activo/inactivo desde la ficha no se hizo (se decide con Juan). Permisos: el servicio simula
// RF008; en producción lo valida el backend con el JWT.
const VOLVER = '/administrador/deportistas'

const ETIQUETAS = {
  nombre: 'Nombres', apellido: 'Apellidos', tipoDocumento: 'Tipo de documento', documento: 'Número de documento',
  fechaNacimiento: 'Fecha de nacimiento', telefono: 'Teléfono de contacto', direccion: 'Dirección', idCategoria: 'Categoría',
}

function Mensaje({ icono = 'info', rol, children }) {
  return (
    <div role={rol} className="flex items-start gap-3 rounded-lg border bg-surface p-4">
      <Icon name={icono} className="mt-0.5 h-5 w-5 shrink-0 text-secondary" />
      <div className="flex min-w-0 flex-1 flex-col gap-3 break-words text-body">{children}</div>
    </div>
  )
}

function Dato({ etiqueta, children }) {
  return (
    <div className="flex min-w-0 flex-col gap-0.5">
      <dt className="text-small text-secondary">{etiqueta}</dt>
      <dd className="break-words text-body">{children}</dd>
    </div>
  )
}

// El id de la ruta cambia la ficha completa (clave): así el modo y los avisos no se arrastran entre deportistas
export default function FichaDeportista() {
  const { id } = useParams()
  return <Ficha key={id} id={id} />
}

function Ficha({ id }) {
  const actor = useActor()
  const tituloRef = useRef(null)
  const [intento, setIntento] = useState(0)
  // Resultado con la clave de la petición que lo produjo: si no coincide, está cargando
  const [carga, setCarga] = useState({ intento: -1, ficha: null, categorias: [], error: null })
  const [modo, setModo] = useState('lectura') // 'lectura' | 'edicion'
  const [sesionEdicion, setSesionEdicion] = useState(0)
  const [exito, setExito] = useState(null)
  const [foco, setFoco] = useState(null)
  const [foto, setFoto] = useState(null) // URL de la foto guardada (en memoria por ahora); si no carga, se muestra sin foto

  useEffect(() => {
    tituloRef.current?.focus()
  }, [])

  useEffect(() => {
    let activo = true
    Promise.all([obtenerFichaDeportista(id, actor), listarCategorias()])
      .then(([ficha, categorias]) => activo && setCarga({ intento, ficha, categorias, error: null }))
      .catch((error) => activo && setCarga({ intento, ficha: null, categorias: [], error }))
    return () => {
      activo = false
    }
  }, [id, actor, intento])

  useEffect(() => {
    let activo = true
    obtenerFoto(id)
      .then((url) => activo && setFoto(url))
      .catch(() => activo && setFoto(null))
    return () => {
      activo = false
    }
  }, [id])

  useEffect(() => {
    if (foco) document.getElementById(foco.id)?.focus()
  }, [foco])

  const cargando = carga.intento !== intento
  const ficha = carga.ficha
  const deportista = ficha?.deportista
  const nombreCategoria = (idCategoria) => carga.categorias.find((c) => c.id === idCategoria)?.nombre ?? null

  function editar() {
    setExito(null)
    setSesionEdicion((n) => n + 1)
    setModo('edicion')
    setFoco({ id: 'nombre' })
  }

  function cancelar() {
    setModo('lectura')
    setFoco({ id: 'boton-editar-ficha' })
  }

  async function guardar(valores) {
    const resultado = await actualizarDeportista(id, valores, actor)
    setCarga((previa) => ({ ...previa, ficha: resultado }))
    setModo('lectura')
    setExito({ texto: 'Ficha actualizada.' })
  }

  async function guardarLaFoto(archivo) {
    setFoto(await guardarFoto(id, archivo, actor))
    setExito({ texto: 'Foto actualizada.' })
  }

  async function quitarLaFoto() {
    await quitarFoto(id, actor)
    setFoto(null)
    setExito({ texto: 'Foto eliminada.' })
  }

  // Historial: más reciente primero, con nombres legibles (la categoría se guarda como id)
  const verValor = (campo, valor) => {
    if (campo === 'idCategoria') return valor === null ? 'Sin asignar' : (nombreCategoria(valor) ?? `Categoría ${valor}`)
    if (valor === null || valor === '') return '—'
    return campo === 'fechaNacimiento' ? fechaLargaConAnio(valor) : valor
  }
  const entradas = (ficha?.historial ?? []).map((entrada, i) => ({
    clave: `${entrada.fechaHora}-${i}`,
    encabezado: `${momentoLegible(entrada.fechaHora)} · ${entrada.responsable ?? 'Sin responsable'}`,
    lineas: entrada.cambios.map((c) => ({ clave: c.campo, texto: `${ETIQUETAS[c.campo]}: ${verValor(c.campo, c.antes)} → ${verValor(c.campo, c.despues)}` })),
  })).reverse()

  const volver = <Button to={VOLVER} variant="secondary" className="sm:self-start">Volver a deportistas</Button>
  const noExiste = carga.error?.codigo === 'DEPORTISTA_NO_EXISTE'
  const sinPermiso = carga.error?.codigo === 'SIN_PERMISO'

  let contenido
  if (cargando) {
    contenido = (
      <Mensaje icono="clock">
        <p role="status">Cargando la ficha…</p>
      </Mensaje>
    )
  } else if (noExiste || sinPermiso) {
    contenido = (
      <Mensaje icono="alert" rol="alert">
        <p>{noExiste ? 'No encontramos este deportista.' : 'No tienes permiso para ver esta ficha.'}</p>
        {volver}
      </Mensaje>
    )
  } else if (carga.error) {
    contenido = (
      <AvisoEstado
        aviso={{ texto: 'No se pudo cargar la ficha del deportista.', accion: { texto: 'Reintentar', onClick: () => setIntento((n) => n + 1) } }}
        rol="alert"
        tono="danger"
        enfocar={false}
      />
    )
  } else if (modo === 'edicion') {
    // Las categorías vigentes y, si ya no está vigente, la actual del deportista (para no perderla al editar)
    const opciones = carga.categorias
      .filter((c) => c.activa || c.id === deportista.idCategoria)
      .map((c) => (c.activa ? c : { ...c, nombre: `${c.nombre} (no vigente)` }))
    const inicial = {
      nombre: deportista.nombre, apellido: deportista.apellido, tipoDocumento: deportista.tipoDocumento, documento: deportista.documento,
      fechaNacimiento: deportista.fechaNacimiento, telefono: deportista.telefono, direccion: deportista.direccion,
      idCategoria: deportista.idCategoria === null ? '' : String(deportista.idCategoria),
    }
    contenido = (
      <Card className="w-full max-w-2xl">
        <FormularioDeportista key={sesionEdicion} inicial={inicial} categorias={opciones} textoGuardar="Guardar cambios" onGuardar={guardar} onCancelar={cancelar} />
      </Card>
    )
  } else {
    const edad = calcularEdad(deportista.fechaNacimiento)
    const registro = deportista.creadoPor && deportista.creadoEn ? partesMomento(deportista.creadoEn) : null
    contenido = (
      <>
        <Card aria-labelledby="foto-deportista" className="flex w-full max-w-2xl flex-col gap-4">
          <h2 id="foto-deportista" className="break-words text-h3">Foto</h2>
          <SubidaFoto nombre={`${deportista.nombre} ${deportista.apellido}`} foto={foto} onGuardar={guardarLaFoto} onQuitar={quitarLaFoto} />
        </Card>
        <Card aria-labelledby="datos-personales" className="flex w-full max-w-2xl flex-col gap-4">
          <h2 id="datos-personales" className="break-words text-h3">Datos personales</h2>
          <dl className="grid grid-cols-[repeat(auto-fit,minmax(min(100%,14rem),1fr))] gap-4">
            <Dato etiqueta="Tipo de documento">{deportista.tipoDocumento}</Dato>
            <Dato etiqueta="Número de documento">{deportista.documento}</Dato>
            <Dato etiqueta="Fecha de nacimiento">{fechaLargaConAnio(deportista.fechaNacimiento)}</Dato>
            <Dato etiqueta="Edad">{edad} {edad === 1 ? 'año' : 'años'}</Dato>
            <Dato etiqueta="Teléfono de contacto">{deportista.telefono}</Dato>
            <Dato etiqueta="Dirección">{deportista.direccion}</Dato>
            <Dato etiqueta="Categoría">{nombreCategoria(deportista.idCategoria) ?? 'Sin asignar'}</Dato>
            <Dato etiqueta="Estado"><EstadoDeportista activo={deportista.activo} /></Dato>
            {/* Solo los inscritos en línea traen origen y contacto del acudiente (los registros del administrador no) */}
            {deportista.origen && <Dato etiqueta="Origen">{deportista.origen}</Dato>}
            {deportista.contactoAcudiente && (
              <Dato etiqueta="Contacto del acudiente">
                {deportista.contactoAcudiente.nombre} {deportista.contactoAcudiente.apellido}
                <span className="block">{deportista.contactoAcudiente.correo}</span>
                <span className="block">{deportista.contactoAcudiente.telefono}</span>
              </Dato>
            )}
          </dl>
          {registro && (
            <p className="break-words text-small text-secondary">
              Registrado por {deportista.creadoPor} el {fechaLargaConAnio(registro.fecha)} a las {formatHora(registro.hora)}
            </p>
          )}
          <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
            <Button id="boton-editar-ficha" onClick={editar}>Editar ficha</Button>
            <Button to={VOLVER} variant="secondary">Volver a deportistas</Button>
          </div>
        </Card>
        <DocumentosDeportista idDeportista={deportista.id} actor={actor} />
        <div className="w-full max-w-2xl">
          <HistorialCambios entradas={entradas} vacio="Todavía no hay cambios registrados." />
        </div>
      </>
    )
  }

  return (
    <Page
      title={deportista ? `${deportista.nombre} ${deportista.apellido}` : 'Ficha del deportista'}
      subtitle={deportista ? (modo === 'edicion' ? 'Editando la ficha' : 'Ficha del deportista') : undefined}
      tituloRef={tituloRef}
    >
      <AvisoEstado aviso={exito} onCerrar={() => setExito(null)} />
      {contenido}
    </Page>
  )
}
