import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Button from '../../components/ui/Button.jsx'
import AvisoEstado from '../../components/ui/AvisoEstado.jsx'
import Icon from '../../components/Icon.jsx'
import FormularioDeportista from '../../components/deportistas/FormularioDeportista.jsx'
import { useActor } from '../../context/useActor.js'
import { calcularEdad, crearDeportista, listarCategorias } from '../../services/deportistasService.js'

// Registrar un deportista nuevo (HU-008, tarea 313). Ruta /administrador/deportistas/nuevo.
// Figma no tiene una pantalla solo para esto: lo más cercano es el paso 1 de "Inscribir jugador" (Nombres, Apellidos, documento,
// fecha de nacimiento, categoría). Los pasos de acudiente, documentos, posición, EPS y talla son de otras tareas.
// PENDIENTE: el rol "secretaria" no existe todavía; solo el administrador registra (lo valida el servicio y luego el backend con el JWT).
const VOLVER = '/administrador/deportistas'

export default function DeportistaNuevo() {
  const actor = useActor()
  const navigate = useNavigate()
  const tituloRef = useRef(null)
  const [intento, setIntento] = useState(0)
  // Resultado con el intento que lo produjo: si no coincide, está cargando (sin setState síncrono en el efecto)
  const [carga, setCarga] = useState({ intento: -1, categorias: [], error: false })
  const [creado, setCreado] = useState(null) // { deportista, categoria, aviso }
  const [formulario, setFormulario] = useState(0) // cambia la clave para empezar un formulario en blanco

  // Al abrir, y al empezar otro registro, el foco va al título
  useEffect(() => {
    tituloRef.current?.focus()
  }, [formulario])

  useEffect(() => {
    let activo = true
    listarCategorias()
      .then((todas) => activo && setCarga({ intento, categorias: todas.filter((c) => c.activa), error: false }))
      .catch(() => activo && setCarga({ intento, categorias: [], error: true }))
    return () => {
      activo = false
    }
  }, [intento])

  const cargando = carga.intento !== intento

  async function guardar(valores) {
    const deportista = await crearDeportista(valores, actor)
    const categoria = carga.categorias.find((c) => c.id === deportista.idCategoria)?.nombre ?? null
    setCreado({ deportista, categoria, aviso: { texto: 'Deportista registrado.' } })
  }

  function registrarOtro() {
    setCreado(null)
    setFormulario((n) => n + 1)
  }

  let contenido
  if (creado) {
    const { deportista, categoria } = creado
    const edad = calcularEdad(deportista.fechaNacimiento)
    contenido = (
      <>
        <AvisoEstado aviso={creado.aviso} />
        <Card aria-labelledby="deportista-creado" className="flex w-full max-w-2xl flex-col gap-3">
          <h2 id="deportista-creado" className="break-words text-h3">{deportista.nombre} {deportista.apellido}</h2>
          <p className="break-words text-body text-secondary">
            {edad} {edad === 1 ? 'año' : 'años'} · Categoría: {categoria ?? 'Sin asignar'}
          </p>
          <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
            <Button onClick={registrarOtro}>Registrar otro deportista</Button>
            <Button to={VOLVER} variant="secondary">Ver deportistas</Button>
          </div>
        </Card>
      </>
    )
  } else if (cargando) {
    contenido = (
      <div className="flex items-start gap-3 rounded-lg border bg-surface p-4">
        <Icon name="clock" className="mt-0.5 h-5 w-5 shrink-0 text-secondary" />
        <p role="status" className="min-w-0 break-words text-body">Cargando el formulario…</p>
      </div>
    )
  } else if (carga.error) {
    contenido = (
      <AvisoEstado
        aviso={{ texto: 'No se pudieron cargar las categorías.', accion: { texto: 'Reintentar', onClick: () => setIntento((n) => n + 1) } }}
        rol="alert"
        tono="danger"
        enfocar={false}
      />
    )
  } else {
    contenido = (
      <Card className="w-full max-w-2xl">
        <FormularioDeportista key={formulario} categorias={carga.categorias} onGuardar={guardar} onCancelar={() => navigate(VOLVER)} />
      </Card>
    )
  }

  return (
    <Page title="Nuevo deportista" subtitle="Registra los datos personales del deportista." tituloRef={tituloRef}>
      {contenido}
    </Page>
  )
}
