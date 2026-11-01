import { useEffect, useRef, useState } from 'react'
import { Navigate, useLocation, useNavigate, useParams } from 'react-router-dom'
import Page from '../../components/ui/Page.jsx'
import Button from '../../components/ui/Button.jsx'
import FilterChips from '../../components/ui/FilterChips.jsx'
import DialogoConfirmacion from '../../components/ui/DialogoConfirmacion.jsx'
import AvisoEstado from '../../components/ui/AvisoEstado.jsx'
import CuadriculaSemanal from '../../components/entrenamientos/CuadriculaSemanal.jsx'
import ListaPorDia from '../../components/entrenamientos/ListaPorDia.jsx'
import FormularioEntrenamiento from '../../components/entrenamientos/FormularioEntrenamiento.jsx'
import { useRole } from '../../context/RoleContext.js'
import { useEntrenamientos } from '../../context/EntrenamientosContext.js'
import { categoriasPorRol, ID_ENTRENADOR_EJEMPLO } from '../../services/entrenamientosService.js'
import { diaLargo, diasDeSemana, formatHora, rangoSemana, sumarDias } from '../../utils/fechas.js'

// Programación de entrenamientos para entrenador y administrador (HU-026).
// Una sola pantalla para tres rutas (.../horarios, .../horarios/nuevo, .../horarios/:id/editar):
// - escritorio: cuadrícula semanal y, en nuevo/editar, el formulario en un panel al lado;
// - móvil: lista por día; nuevo/editar se ven como pantalla propia (la lista se oculta).
export default function Programacion() {
  const { role } = useRole()
  const { id } = useParams()
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const { entrenamientos, cargado, semana, setSemana, aviso, cerrarAviso, crear, editar, eliminar, detectarConflicto } = useEntrenamientos()
  const [filtro, setFiltro] = useState('Todas')
  const [porEliminar, setPorEliminar] = useState(null)
  const tituloMovil = useRef(null)
  const tituloPanel = useRef(null)
  const columnaPrincipal = useRef(null)
  const [compacta, setCompacta] = useState(false)

  const base = `/${role}/horarios`
  const categorias = categoriasPorRol[role]
  const modo = pathname.endsWith('/nuevo') ? 'nuevo' : id ? 'editar' : 'lista'
  const editando = modo === 'editar' ? entrenamientos.find((e) => String(e.id) === id && categorias.includes(e.categoria)) : null
  const formularioAbierto = modo === 'nuevo' || Boolean(editando)

  // Al abrir el formulario el foco va a su título (el visible según el tamaño de pantalla)
  useEffect(() => {
    if (!formularioAbierto) return
    const visible = [tituloMovil.current, tituloPanel.current].find((el) => el?.offsetParent)
    visible?.focus()
  }, [formularioAbierto, editando?.id])

  // La cuadrícula necesita unos 38 rem de ancho. Si la columna es más angosta (texto grande, tablet vertical)
  // se usa la lista por día. Se mide en rem, así que depende del tamaño de letra y no solo de los píxeles.
  useEffect(() => {
    const columna = columnaPrincipal.current
    if (!columna) return
    const observador = new ResizeObserver(([entrada]) => {
      const rem = parseFloat(getComputedStyle(document.documentElement).fontSize)
      setCompacta(entrada.contentRect.width / rem < 38)
    })
    observador.observe(columna)
    return () => observador.disconnect()
  }, [])

  // Una ruta de edición con un id que no existe (o de otra categoría) vuelve a la lista
  if (modo === 'editar' && cargado && !editando) return <Navigate to={base} replace />

  const dias = diasDeSemana(semana)
  const delRol = entrenamientos.filter((e) => categorias.includes(e.categoria))
  const enSemana = delRol.filter((e) => dias.includes(e.fecha) && (filtro === 'Todas' || e.categoria === filtro))

  const inicial = editando
    ? { ...editando, idEntrenador: editando.idEntrenador === null ? '' : String(editando.idEntrenador), grupo: editando.grupo ?? '' }
    : {
        categoria: categorias.length === 1 ? categorias[0] : '',
        idEntrenador: role === 'entrenador' ? String(ID_ENTRENADOR_EJEMPLO) : '',
        fecha: '',
        hora: '',
        horaFin: '',
        sede: '',
        grupo: '',
      }

  async function guardar(valores) {
    if (editando) await editar(editando.id, valores)
    else await crear(valores)
    navigate(base)
  }

  async function confirmarEliminar() {
    await eliminar(porEliminar.id)
    setPorEliminar(null)
  }

  const titulo = editando ? 'Editar entrenamiento' : 'Nuevo entrenamiento'

  return (
    <Page
      title={role === 'administrador' ? 'Entrenamientos y horarios' : 'Horarios'}
      subtitle={`${role === 'administrador' ? 'Todas las categorías · semana' : 'Semana'} del ${rangoSemana(semana)}`}
      ocultarEnMovil={formularioAbierto}
    >
      <AvisoEstado aviso={aviso} onCerrar={cerrarAviso} />
      <div className="flex flex-col gap-6 md:flex-row md:flex-wrap md:items-start">
        <div ref={columnaPrincipal} className={`${formularioAbierto ? 'hidden md:flex' : 'flex'} min-w-0 flex-col gap-4 md:flex-1 md:basis-[40rem]`}>
          <div className="flex flex-wrap gap-2">
            <Button to={`${base}/nuevo`} className="w-full md:w-auto">Nuevo entrenamiento</Button>
            <Button variant="secondary" onClick={() => setSemana(sumarDias(semana, -7))} className="grow md:grow-0">Semana anterior</Button>
            <Button variant="secondary" onClick={() => setSemana(sumarDias(semana, 7))} className="grow md:grow-0">Semana siguiente</Button>
          </div>
          <FilterChips label="Categoría" options={['Todas', ...categorias]} value={filtro} onChange={setFiltro} />
          {enSemana.length === 0 ? (
            <p className="text-body text-secondary">No hay entrenamientos programados esta semana.</p>
          ) : (
            <>
              <div className={compacta ? 'hidden' : 'hidden md:block'}>
                <CuadriculaSemanal dias={dias} entrenamientos={enSemana} base={base} editandoId={editando?.id} />
              </div>
              <div className={compacta ? '' : 'md:hidden'}>
                <ListaPorDia dias={dias} entrenamientos={enSemana} base={base} onEliminar={setPorEliminar} />
              </div>
            </>
          )}
        </div>

        {formularioAbierto && (
          <div className="flex flex-col gap-4 md:min-w-0 md:shrink md:grow-0 md:basis-80">
            <div className="flex flex-col gap-1 md:hidden">
              <h1 ref={tituloMovil} tabIndex={-1} className="break-words text-display outline-none">{titulo}</h1>
              <p className="text-body text-secondary">Define la categoría, el entrenador, la fecha, la hora y la sede</p>
            </div>
            <section aria-labelledby="titulo-panel" className="rounded-lg border bg-surface p-4">
              <h2 id="titulo-panel" ref={tituloPanel} tabIndex={-1} className="mb-4 hidden break-words text-h3 outline-none md:block">{titulo}</h2>
              <FormularioEntrenamiento
                key={editando?.id ?? 'nuevo'}
                inicial={inicial}
                categorias={categorias}
                ignorarId={editando?.id ?? null}
                detectarConflicto={detectarConflicto}
                onGuardar={guardar}
                onCancelar={() => navigate(base)}
                onEliminar={editando ? () => setPorEliminar(editando) : undefined}
              />
            </section>
          </div>
        )}
      </div>

      <DialogoConfirmacion
        open={Boolean(porEliminar)}
        titulo="¿Eliminar este entrenamiento?"
        descripcion={
          porEliminar
            ? `${porEliminar.categoria}${porEliminar.grupo ? ` (grupo ${porEliminar.grupo})` : ''} · ${porEliminar.sede} · ${diaLargo(porEliminar.fecha)}, ${formatHora(porEliminar.hora)}. Esta acción no se puede deshacer y se avisará a los jugadores y sus acudientes.`
            : ''
        }
        textoConfirmar="Sí, eliminar"
        onConfirmar={confirmarEliminar}
        onCancelar={() => setPorEliminar(null)}
      />
    </Page>
  )
}
