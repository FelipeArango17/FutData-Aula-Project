import { useEffect, useRef, useState } from 'react'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Button from '../../components/ui/Button.jsx'
import Field from '../../components/ui/Field.jsx'
import AvisoEstado from '../../components/ui/AvisoEstado.jsx'
import DialogoConfirmacion from '../../components/ui/DialogoConfirmacion.jsx'
import Icon from '../../components/Icon.jsx'
import FormularioFranja from '../../components/horario/FormularioFranja.jsx'
import VistaSemanalClub from '../../components/horario/VistaSemanalClub.jsx'
import { categoriaConGrupo, rangoHoras } from '../../components/horario/formatoFranja.js'
import * as servicio from '../../services/horarioClubService.js'
import { ENTRENADORES_CLUB, NOMBRES_DIAS, SEDES } from '../../services/horarioClubData.js'
import { NOMBRES_CATEGORIAS } from '../../utils/categorias.js'

// Horario semanal del club (plantilla fija de lunes a sábado). Solo el administrador crea, edita y elimina franjas.
// Es distinto de "Entrenamientos y horarios" (/administrador/horarios), que programa entrenamientos con fecha.
// Figma no tiene esta pantalla: usa los componentes del proyecto. PENDIENTE: permisos y datos los valida el backend con el JWT.
const SIN_FILTRO = ''
const POR_TURNOS = 'todos'

const aTexto = (f) => ({
  idCategoria: String(f.idCategoria), dia: String(f.dia), horaInicio: f.horaInicio, horaFin: f.horaFin, sede: f.sede,
  idEntrenador: f.idEntrenador === null ? '' : String(f.idEntrenador), grupo: f.grupo ?? '',
})
const VACIA = { idCategoria: '', dia: '', horaInicio: '', horaFin: '', sede: '', idEntrenador: '', grupo: '' }

function Mensaje({ icono = 'info', rol, children }) {
  return (
    <div role={rol} className="flex items-start gap-3 rounded-lg border bg-surface p-4">
      <Icon name={icono} className="mt-0.5 h-5 w-5 shrink-0 text-secondary" />
      <div className="flex min-w-0 flex-1 flex-col gap-3 break-words text-body">{children}</div>
    </div>
  )
}

export default function HorarioClub() {
  const tituloFormulario = useRef(null)
  const zona = useRef(null)
  const [intento, setIntento] = useState(0)
  // Resultado con el intento que lo produjo: si no coincide, está cargando
  const [carga, setCarga] = useState({ intento: -1, franjas: [], error: false })
  const [categoria, setCategoria] = useState(SIN_FILTRO)
  const [entrenador, setEntrenador] = useState(SIN_FILTRO)
  const [sede, setSede] = useState(SIN_FILTRO)
  const [formulario, setFormulario] = useState(null) // null | { franja } (franja null = nueva)
  const [porEliminar, setPorEliminar] = useState(null)
  const [aviso, setAviso] = useState(null)
  const [errorAccion, setErrorAccion] = useState(null)
  const [compacta, setCompacta] = useState(false)

  useEffect(() => {
    let activo = true
    servicio.listar()
      .then((franjas) => activo && setCarga({ intento, franjas, error: false }))
      .catch(() => activo && setCarga({ intento, franjas: [], error: true }))
    return () => {
      activo = false
    }
  }, [intento])

  // La cuadrícula necesita unos 48 rem de ancho. Si la zona es más angosta (móvil, tablet vertical, texto grande)
  // se usa la lista por día. Se mide en rem, así que depende del tamaño de letra y no solo de los píxeles.
  useEffect(() => {
    const observador = new ResizeObserver(([entrada]) => {
      const rem = parseFloat(getComputedStyle(document.documentElement).fontSize)
      setCompacta(entrada.contentRect.width / rem < 48)
    })
    observador.observe(zona.current)
    return () => observador.disconnect()
  }, [])

  useEffect(() => {
    if (formulario) tituloFormulario.current?.focus()
  }, [formulario])

  const cargando = carga.intento !== intento
  const franjas = carga.franjas
  const visibles = franjas.filter(
    (f) => (!categoria || f.idCategoria === Number(categoria)) && (!sede || f.sede === sede)
      && (!entrenador || (entrenador === POR_TURNOS ? f.idEntrenador === null : f.idEntrenador === Number(entrenador))),
  )
  const filtrando = Boolean(categoria || entrenador || sede)

  async function guardar(valores) {
    const editando = formulario.franja
    if (editando) await servicio.editar(editando.id, valores)
    else await servicio.crear(valores)
    setAviso({ texto: editando ? 'Franja actualizada.' : 'Franja creada.' })
    setFormulario(null)
    setIntento((n) => n + 1)
  }

  async function confirmarEliminar() {
    const franja = porEliminar
    setPorEliminar(null)
    setErrorAccion(null)
    try {
      await servicio.eliminar(franja.id)
      setFormulario(null)
      setAviso({ texto: 'Franja eliminada.' })
      setIntento((n) => n + 1)
    } catch {
      setErrorAccion({ texto: 'No se pudo eliminar la franja. Intenta de nuevo.' })
    }
  }

  const abrir = (franja) => {
    setAviso(null)
    setErrorAccion(null)
    setFormulario({ franja, clave: franja ? `f-${franja.id}` : 'nueva' })
  }

  let contenido
  if (carga.error && !cargando) {
    contenido = (
      <AvisoEstado
        aviso={{ texto: 'No se pudo cargar el horario del club.', accion: { texto: 'Reintentar', onClick: () => setIntento((n) => n + 1) } }}
        rol="alert"
        tono="danger"
        enfocar={false}
      />
    )
  } else if (cargando && franjas.length === 0) {
    contenido = <Mensaje icono="clock"><p role="status">Cargando el horario…</p></Mensaje>
  } else if (visibles.length === 0) {
    contenido = (
      <Mensaje>
        <p>{filtrando ? 'No hay franjas con esos filtros.' : 'Todavía no hay franjas en el horario.'}</p>
        {filtrando && <Button variant="secondary" onClick={() => { setCategoria(SIN_FILTRO); setEntrenador(SIN_FILTRO); setSede(SIN_FILTRO) }} className="sm:self-start">Limpiar filtros</Button>}
      </Mensaje>
    )
  } else {
    contenido = <VistaSemanalClub franjas={visibles} modo={compacta ? 'lista' : 'cuadricula'} onEditar={abrir} onEliminar={setPorEliminar} />
  }

  return (
    <Page title="Horario del club" subtitle="Plantilla semanal fija, de lunes a sábado">
      <AvisoEstado aviso={aviso} onCerrar={() => setAviso(null)} />
      <AvisoEstado aviso={errorAccion} rol="alert" tono="danger" onCerrar={() => setErrorAccion(null)} />
      <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap md:self-start">
        <Button onClick={() => abrir(null)}>Nueva franja</Button>
      </div>

      {formulario && (
        <Card aria-labelledby="titulo-franja" className="flex w-full max-w-3xl flex-col gap-4">
          <h2 id="titulo-franja" ref={tituloFormulario} tabIndex={-1} className="break-words text-h3 outline-none">
            {formulario.franja ? 'Editar franja' : 'Nueva franja'}
          </h2>
          <FormularioFranja
            key={formulario.clave}
            inicial={formulario.franja ? aTexto(formulario.franja) : VACIA}
            franjas={franjas}
            ignorarId={formulario.franja?.id ?? null}
            onGuardar={guardar}
            onCancelar={() => setFormulario(null)}
          />
          {formulario.franja && (
            <Button variant="danger" onClick={() => setPorEliminar(formulario.franja)} className="sm:self-start">
              <Icon name="trash" className="mr-2 h-5 w-5 shrink-0" />
              Eliminar franja
            </Button>
          )}
        </Card>
      )}

      <div className="grid grid-cols-[repeat(auto-fit,minmax(min(100%,12rem),1fr))] gap-4">
        <Field label="Categoría" id="filtro-categoria" as="select" value={categoria} onChange={(e) => setCategoria(e.target.value)}>
          <option value={SIN_FILTRO}>Todas las categorías</option>
          {NOMBRES_CATEGORIAS.map((c, i) => <option key={c} value={String(i + 1)}>{c}</option>)}
        </Field>
        <Field label="Entrenador" id="filtro-entrenador" as="select" value={entrenador} onChange={(e) => setEntrenador(e.target.value)}>
          <option value={SIN_FILTRO}>Todos</option>
          {ENTRENADORES_CLUB.map((e) => <option key={e.id} value={String(e.id)}>{e.nombre}</option>)}
          <option value={POR_TURNOS}>Por turnos</option>
        </Field>
        <Field label="Sede" id="filtro-sede" as="select" value={sede} onChange={(e) => setSede(e.target.value)}>
          <option value={SIN_FILTRO}>Todas las sedes</option>
          {SEDES.map((s) => <option key={s} value={s}>{s}</option>)}
        </Field>
      </div>
      <p role="status" className="min-h-[1.125rem] text-small text-secondary">
        {cargando && franjas.length === 0 ? '' : `${visibles.length} ${visibles.length === 1 ? 'franja' : 'franjas'}${filtrando ? ' con los filtros elegidos' : ''}`}
      </p>

      <div ref={zona} className="min-w-0">{contenido}</div>

      <DialogoConfirmacion
        open={Boolean(porEliminar)}
        titulo="¿Eliminar esta franja?"
        descripcion={porEliminar ? `${categoriaConGrupo(porEliminar)} · ${NOMBRES_DIAS[porEliminar.dia]} · ${rangoHoras(porEliminar)} · ${porEliminar.sede}. Esta acción no se puede deshacer.` : ''}
        textoConfirmar="Sí, eliminar"
        onConfirmar={confirmarEliminar}
        onCancelar={() => setPorEliminar(null)}
      />
    </Page>
  )
}
