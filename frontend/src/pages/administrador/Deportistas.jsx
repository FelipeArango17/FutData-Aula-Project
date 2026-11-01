import { useEffect, useRef, useState } from 'react'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Button from '../../components/ui/Button.jsx'
import Avatar from '../../components/ui/Avatar.jsx'
import Field from '../../components/ui/Field.jsx'
import FilterChips from '../../components/ui/FilterChips.jsx'
import AvisoEstado from '../../components/ui/AvisoEstado.jsx'
import Icon from '../../components/Icon.jsx'
import Paginacion from '../../components/ui/Paginacion.jsx'
import EstadoDeportista from '../../components/deportistas/EstadoDeportista.jsx'
import { SIN_CATEGORIA, calcularEdad, listarCategorias, listarDeportistas } from '../../services/deportistasService.js'
import { useFotosDeportistas } from '../../hooks/useFotosDeportistas.js'
import { NOTA_CATEGORIA_POR_ANIO } from '../../utils/categorias.js'
import { paginar } from '../../utils/paginacion.js'

// Consulta de deportistas (HU-010, tarea 319): busca por nombre o documento en TODOS los deportistas (activos e inactivos),
// filtra por estado y por categoría (se combinan con AND) y lleva a la ficha. Reemplaza la maqueta con datos fijos; Figma no tiene una pantalla de esta lista.
// Paginación: del lado del cliente, sobre la lista ya filtrada. GET /api/deportistas hoy devuelve la lista completa (sin
// parámetros de página), así que al conectarlo seguirá siendo del cliente hasta que el backend añada paginación.
// Privacidad: la lista no muestra teléfono ni dirección; esos datos solo están en la ficha.
const FILTROS = { Todos: 'todos', Activos: 'activos', Inactivos: 'inactivos' }
const TODAS = '' // valor del selector de categoría para «Todas las categorías»
const nombreCompleto = (d) => `${d.nombre} ${d.apellido}`
const textoEdad = (d) => {
  const edad = calcularEdad(d.fechaNacimiento)
  return `${edad} ${edad === 1 ? 'año' : 'años'}`
}

function Mensaje({ icono = 'info', children }) {
  return (
    // flex-wrap: con letra muy grande el ícono queda encima del texto en vez de quitarle el ancho y partir las palabras
    <div className="flex flex-wrap items-start gap-3 rounded-lg border bg-surface p-4">
      <Icon name={icono} className="mt-0.5 h-5 w-5 shrink-0 text-secondary" />
      <div className="flex min-w-0 flex-1 basis-40 flex-col gap-3 break-words text-body">{children}</div>
    </div>
  )
}

export default function Deportistas() {
  const tituloRef = useRef(null)
  const fotos = useFotosDeportistas()
  const zonaLista = useRef(null)
  const [intento, setIntento] = useState(0)
  const [texto, setTexto] = useState('')
  const [filtro, setFiltro] = useState('Todos')
  const [categoria, setCategoria] = useState(TODAS)
  const [conTabla, setConTabla] = useState(false)
  // Página elegida y los filtros con los que se eligió: si los filtros cambian, vuelve a la página 1 (sin setState en un efecto)
  const [elegida, setElegida] = useState({ filtros: '', pagina: 1 })
  // Categorías para el selector. Se cargan aparte: si fallan, el selector queda solo con «Todas las categorías» y la lista sigue
  const [categorias, setCategorias] = useState([])
  // Resultados con la clave de la petición que los produjo: si no coincide, está cargando (sin setState síncrono en el efecto)
  const [base, setBase] = useState({ intento: -1, total: 0, activos: 0, error: false })
  const [lista, setLista] = useState({ clave: null, items: null, error: false })

  useEffect(() => {
    tituloRef.current?.focus()
  }, [])

  // La tabla necesita unos 40 rem de ancho. Si la zona es más angosta (móvil, tablet vertical, texto grande)
  // se usan tarjetas. Se mide en rem, así que depende del tamaño de letra y no solo de los píxeles.
  useEffect(() => {
    const zona = zonaLista.current
    const observador = new ResizeObserver(([entrada]) => {
      const rem = parseFloat(getComputedStyle(document.documentElement).fontSize)
      setConTabla(entrada.contentRect.width / rem >= 40)
    })
    observador.observe(zona)
    return () => observador.disconnect()
  }, [])

  useEffect(() => {
    let activo = true
    listarCategorias()
      .then((lista) => activo && setCategorias(lista))
      .catch(() => activo && setCategorias([]))
    return () => {
      activo = false
    }
  }, [intento])

  // Totales reales (para el subtítulo)
  useEffect(() => {
    let activo = true
    listarDeportistas()
      .then((todos) => activo && setBase({ intento, total: todos.length, activos: todos.filter((d) => d.activo).length, error: false }))
      .catch(() => activo && setBase({ intento, total: 0, activos: 0, error: true }))
    return () => {
      activo = false
    }
  }, [intento])

  const busqueda = texto.trim()
  const estado = FILTROS[filtro]
  const clave = `${intento}|${estado}|${categoria}|${busqueda}`

  // Resultados de la búsqueda; mientras llegan los nuevos se conservan los anteriores
  useEffect(() => {
    let activo = true
    listarDeportistas({ texto: busqueda, estado, idCategoria: categoria })
      .then((items) => activo && setLista({ clave, items, error: false }))
      .catch(() => activo && setLista({ clave, items: null, error: true }))
    return () => {
      activo = false
    }
  }, [busqueda, estado, categoria, clave])

  const cargandoBase = base.intento !== intento
  const actualizando = lista.clave !== clave
  const error = base.error || (!actualizando && lista.error)
  const items = lista.items
  const filtros = `${estado}|${categoria}|${busqueda}`
  const vista = items ? paginar(items, elegida.filtros === filtros ? elegida.pagina : 1) : null
  const nombresCategoria = Object.fromEntries(categorias.map((c) => [c.id, c.nombre]))
  const categoriaDe = (d) => (d.idCategoria === null ? 'Sin asignar' : nombresCategoria[d.idCategoria] ?? 'No disponible')
  // «Sin asignar» o el nombre de la categoría elegida (vacío si el filtro es «Todas las categorías»)
  const nombreFiltro = categoria === SIN_CATEGORIA ? 'Sin asignar' : nombresCategoria[categoria] ?? ''
  const filtroActivo = Boolean(busqueda) || filtro !== 'Todos' || categoria !== TODAS
  const limpiar = () => { setTexto(''); setFiltro('Todos'); setCategoria(TODAS) }
  const sufijoCategoria = nombreFiltro ? ` · Categoría: ${nombreFiltro}` : ''
  const detalleEstado = filtro === 'Todos' ? '' : filtro.toLowerCase()
  const detalleCategoria = !categoria ? '' : categoria === SIN_CATEGORIA ? 'sin categoría asignada' : `de la categoría ${nombreFiltro}`

  const aviso = error ? { texto: 'No se pudo cargar la lista de deportistas.', accion: { texto: 'Reintentar', onClick: () => setIntento((n) => n + 1) } } : null
  // Mientras llegan resultados nuevos no se cuenta: el total anterior no corresponde a la categoría ya elegida
  const cuenta = items === null || error || actualizando ? '' : items.length === 0 ? `Sin resultados${sufijoCategoria}` : `${items.length} ${items.length === 1 ? 'resultado' : 'resultados'}${sufijoCategoria}`
  const subtitulo = cargandoBase || base.error ? 'Consulta y edita la ficha de cada deportista.' : `${base.total} ${base.total === 1 ? 'deportista registrado' : 'deportistas registrados'} · ${base.activos} ${base.activos === 1 ? 'activo' : 'activos'}`

  let contenido
  if (error) {
    contenido = null
  } else if (items === null) {
    contenido = (
      <Mensaje icono="clock">
        <p role="status">Cargando deportistas…</p>
      </Mensaje>
    )
  } else if (items.length === 0) {
    contenido = (
      <Mensaje>
        <p>
          {busqueda
            ? `No hay deportistas que coincidan con «${busqueda}»${detalleEstado ? ` entre los ${detalleEstado}` : ''}${detalleCategoria ? ` ${detalleCategoria}` : ''}.`
            : `No hay deportistas${detalleEstado ? ` ${detalleEstado}` : ''}${detalleCategoria ? ` ${detalleCategoria}` : ''}.`}
        </p>
        {filtroActivo && (
          <Button variant="secondary" onClick={limpiar} className="sm:self-start">Limpiar búsqueda y filtro</Button>
        )}
      </Mensaje>
    )
  } else if (conTabla) {
    contenido = (
      <table aria-busy={actualizando} className="w-full table-fixed border-collapse rounded-lg border bg-surface text-left text-body">
        <caption className="sr-only">Deportistas registrados</caption>
        <thead>
          <tr className="border-b bg-app">
            <th scope="col" className="p-3 text-body-medium">Deportista</th>
            <th scope="col" className="w-[5.5rem] p-3 text-body-medium">Edad</th>
            <th scope="col" className="w-[8.5rem] p-3 text-body-medium">Documento</th>
            <th scope="col" className="w-[6.5rem] p-3 text-body-medium">Categoría</th>
            <th scope="col" className="w-[7.5rem] p-3 text-body-medium">Estado</th>
            <th scope="col" className="w-[8rem] p-3 text-body-medium"><span className="sr-only">Acciones</span></th>
          </tr>
        </thead>
        <tbody>
          {vista.items.map((d) => (
            <tr key={d.id} className="border-b last:border-b-0">
              <th scope="row" className="p-3 font-normal">
                <div className="flex min-w-0 items-center gap-3">
                  <Avatar name={nombreCompleto(d)} foto={fotos[d.id]} />
                  <span className="min-w-0 break-words text-body-medium">{nombreCompleto(d)}</span>
                </div>
              </th>
              <td className="p-3">{textoEdad(d)}</td>
              <td className="break-words p-3">{d.documento}</td>
              <td className="break-words p-3">{categoriaDe(d)}</td>
              <td className="p-3"><EstadoDeportista activo={d.activo} /></td>
              <td className="p-3">
                <Button to={`/administrador/deportistas/${d.id}`} variant="secondary" aria-label={`Ver ficha de ${nombreCompleto(d)}`}>Ver ficha</Button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    )
  } else {
    contenido = (
      <ul aria-busy={actualizando} aria-label="Deportistas registrados" className="grid grid-cols-1 gap-4">
        {vista.items.map((d) => (
          <Card as="li" key={d.id} className="flex flex-col gap-3">
            <div className="flex flex-wrap items-start gap-3">
              <Avatar name={nombreCompleto(d)} foto={fotos[d.id]} />
              <div className="flex min-w-0 grow basis-[12rem] flex-col gap-1">
                <h2 className="break-words text-h3">{nombreCompleto(d)}</h2>
                <p className="break-words text-small text-secondary">{textoEdad(d)} · Documento: {d.documento}</p>
                <p className="break-words text-small text-secondary">Categoría: {categoriaDe(d)}</p>
                <div className="flex flex-wrap pt-1"><EstadoDeportista activo={d.activo} /></div>
              </div>
            </div>
            <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
              <Button to={`/administrador/deportistas/${d.id}`} variant="secondary" aria-label={`Ver ficha de ${nombreCompleto(d)}`}>Ver ficha</Button>
            </div>
          </Card>
        ))}
      </ul>
    )
  }

  return (
    <Page title="Deportistas" subtitle={subtitulo} tituloRef={tituloRef}>
      <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap md:self-start">
        <Button to="/administrador/deportistas/inscribir">Inscribir jugador</Button>
        <Button to="/administrador/deportistas/nuevo" variant="secondary">Nuevo deportista</Button>
      </div>
      <AvisoEstado aviso={aviso} rol="alert" tono="danger" enfocar={false} />
      <div className="grid grid-cols-1 gap-4">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Field label="Buscar por nombre o documento" id="buscar" type="search" autoComplete="off" value={texto} onChange={(e) => setTexto(e.target.value)} />
          <Field as="select" label="Categoría" id="categoria" value={categoria} onChange={(e) => setCategoria(e.target.value)}>
            <option value={TODAS}>Todas las categorías</option>
            {categorias.map((c) => (
              <option key={c.id} value={c.id}>{c.activa ? c.nombre : `${c.nombre} (no vigente)`}</option>
            ))}
            <option value={SIN_CATEGORIA}>Sin asignar</option>
          </Field>
        </div>
        <FilterChips label="Estado" options={Object.keys(FILTROS)} value={filtro} onChange={setFiltro} />
        <p role="status" className="min-h-[1.125rem] text-small text-secondary">{cuenta}</p>
        <p className="break-words text-small text-secondary">{NOTA_CATEGORIA_POR_ANIO}</p>
      </div>
      <div ref={zonaLista} className="min-w-0">{contenido}</div>
      {vista && !error && items.length > 0 && (
        <Paginacion {...vista} onCambiar={(pagina) => setElegida({ filtros, pagina })} />
      )}
    </Page>
  )
}
