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
import DatosDeportista from '../../components/deportistas/DatosDeportista.jsx'
import FormularioDeportista from '../../components/deportistas/FormularioDeportista.jsx'
import { useActor } from '../../context/useActor.js'
import { actualizarDeportista, deportistasPorApi, listarCategorias, obtenerFichaDeportista } from '../../services/deportistasService.js'
import { documentosPorApi } from '../../services/documentosService.js'
import { guardarFoto, obtenerFoto, quitarFoto } from '../../services/fotosService.js'
import { ETIQUETA_GRUPO } from '../../utils/categorias.js'
import { fechaLargaConAnio, formatHora, momentoLegible, partesMomento } from '../../utils/fechas.js'

// Ficha del deportista (HU-010, tarea 319): lectura y edición. Ruta /administrador/deportistas/:id. Solo el administrador.
// Figma no tiene una ficha del administrador (la del acudiente es otra pantalla), así que usa los componentes del proyecto.
// En lectura tiene la foto (arriba) y los documentos (debajo de los datos personales); el formulario de edición no cambia.
// Con la API (módulo "deportistas"): la foto y los datos salen de GET /api/deportistas/{id} (la foto es una URL firmada), la foto se
// sube y se quita con el backend, y NO se muestra el historial de cambios (el backend todavía no lo tiene). Los documentos se muestran con el módulo "documentos" (API) o sin la API.
// Los 400/403/404/409 se muestran con el mensaje del backend. La categoría y el estado de la inscripción los calcula el backend.
// PENDIENTE: pasar al deportista a activo/inactivo desde la ficha no se hizo (se decide con Juan). Permisos: el servicio simula
// RF008; en producción lo valida el backend con el JWT.
const VOLVER = '/administrador/deportistas'

const ETIQUETAS = {
  nombre: 'Nombres', apellido: 'Apellidos', tipoDocumento: 'Tipo de documento', documento: 'Número de documento',
  fechaNacimiento: 'Fecha de nacimiento', telefono: 'Teléfono de contacto', direccion: 'Dirección', idCategoria: 'Categoría',
  grupoEntrenamiento: ETIQUETA_GRUPO, posicion: 'Posición',
}

function Mensaje({ icono = 'info', rol, children }) {
  return (
    <div role={rol} className="flex items-start gap-3 rounded-lg border bg-surface p-4">
      <Icon name={icono} className="mt-0.5 h-5 w-5 shrink-0 text-secondary" />
      <div className="flex min-w-0 flex-1 flex-col gap-3 break-words text-body">{children}</div>
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
  const porApi = deportistasPorApi()
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
    // Con la API las categorías solo sirven para el formulario: si fallan, la ficha igual se muestra (trae el nombre de su categoría)
    Promise.all([obtenerFichaDeportista(id, actor), porApi ? listarCategorias().catch(() => []) : listarCategorias()])
      .then(([ficha, categorias]) => {
        if (!activo) return
        setCarga({ intento, ficha, categorias, error: null })
        if (porApi) setFoto(ficha.deportista.foto ?? null)
      })
      .catch((error) => activo && setCarga({ intento, ficha: null, categorias: [], error }))
    return () => {
      activo = false
    }
  }, [id, actor, intento, porApi])

  useEffect(() => {
    if (porApi) return undefined // la foto llega con la ficha
    let activo = true
    obtenerFoto(id)
      .then((url) => activo && setFoto(url))
      .catch(() => activo && setFoto(null))
    return () => {
      activo = false
    }
  }, [id, porApi])

  useEffect(() => {
    if (foco) document.getElementById(foco.id)?.focus()
  }, [foco])

  const cargando = carga.intento !== intento
  const ficha = carga.ficha
  const deportista = ficha?.deportista
  const nombreCategoria = (idCategoria) => carga.categorias.find((c) => c.id === idCategoria)?.nombre ?? deportista?.categoria ?? null

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
    const { activo, ...datos } = valores
    const resultado = await actualizarDeportista(id, activo === undefined ? datos : { ...datos, activo: activo === 'activo' }, actor)
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
        <p>{carga.error.mensaje ?? (noExiste ? 'No encontramos este deportista.' : 'No tienes permiso para ver esta ficha.')}</p>
        {volver}
      </Mensaje>
    )
  } else if (carga.error) {
    contenido = (
      <AvisoEstado
        aviso={{ texto: carga.error.estado === 400 ? carga.error.mensaje : 'No se pudo cargar la ficha del deportista.', accion: { texto: 'Reintentar', onClick: () => setIntento((n) => n + 1) } }}
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
      grupoEntrenamiento: deportista.grupoEntrenamiento ?? '', posicion: deportista.posicion ?? '',
      ...(porApi ? { activo: deportista.activo ? 'activo' : 'inactivo' } : {}),
    }
    contenido = (
      <Card className="w-full max-w-2xl">
        <FormularioDeportista key={sesionEdicion} inicial={inicial} categorias={opciones} textoGuardar="Guardar cambios" onGuardar={guardar} onCancelar={cancelar} />
      </Card>
    )
  } else {
    const registro = deportista.creadoPor && deportista.creadoEn ? partesMomento(deportista.creadoEn) : null
    contenido = (
      <>
        <Card aria-labelledby="foto-deportista" className="flex w-full max-w-2xl flex-col gap-4">
          <h2 id="foto-deportista" className="break-words text-h3">Foto</h2>
          <SubidaFoto nombre={`${deportista.nombre} ${deportista.apellido}`} foto={foto} onGuardar={guardarLaFoto} onQuitar={quitarLaFoto} />
        </Card>
        <Card aria-labelledby="datos-personales" className="flex w-full max-w-2xl flex-col gap-4">
          <h2 id="datos-personales" className="break-words text-h3">Datos personales</h2>
          <DatosDeportista deportista={deportista} categoria={nombreCategoria(deportista.idCategoria)} />
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
        {(!porApi || documentosPorApi()) && <DocumentosDeportista idDeportista={deportista.id} actor={actor} tieneFoto={Boolean(foto)} />}
        {!porApi && (
          <div className="w-full max-w-2xl">
            <HistorialCambios entradas={entradas} vacio="Todavía no hay cambios registrados." />
          </div>
        )}
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
