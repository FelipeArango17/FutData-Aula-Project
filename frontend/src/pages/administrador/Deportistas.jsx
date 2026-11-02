import { useEffect, useRef, useState } from 'react'
import Page from '../../components/ui/Page.jsx'
import Button from '../../components/ui/Button.jsx'
import Field from '../../components/ui/Field.jsx'
import FilterChips from '../../components/ui/FilterChips.jsx'
import AvisoEstado from '../../components/ui/AvisoEstado.jsx'
import Icon from '../../components/Icon.jsx'
import Paginacion from '../../components/ui/Paginacion.jsx'
import { TablaDeportistas, TarjetasDeportistas } from '../../components/deportistas/ListaDeportistas.jsx'
import { SIN_CATEGORIA, contarDeportistas, listarCategorias, listarPaginaDeportistas } from '../../services/deportistasService.js'
import { useFotosDeportistas } from '../../hooks/useFotosDeportistas.js'
import { NOTA_CATEGORIA_POR_ANIO } from '../../utils/categorias.js'

// Consulta de deportistas (HU-010, tarea 319): busca por nombre o documento en TODOS los deportistas (activos e inactivos),
// filtra por estado y por categoría (se combinan con AND) y lleva a la ficha. Reemplaza la maqueta con datos fijos; Figma no tiene una pantalla de esta lista.
// Paginación: con la API (módulo "deportistas") la hace el backend (estado y categoría son filtros del servidor); con texto o «sin
// asignar» el servicio recorre las páginas y filtra aquí (la API no busca por texto). Con los datos de ejemplo es del cliente.
// La foto de cada fila viene en el propio deportista (URL firmada); el administrador siempre recibe el documento.
// Privacidad: la lista no muestra teléfono ni dirección; esos datos solo están en la ficha.
const FILTROS = { Todos: 'todos', Activos: 'activos', Inactivos: 'inactivos' }
const TODAS = '' // valor del selector de categoría para «Todas las categorías»
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
  const [lista, setLista] = useState({ clave: null, resultado: null, error: false })

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
    contarDeportistas()
      .then(({ total, activos }) => activo && setBase({ intento, total, activos, error: false }))
      .catch(() => activo && setBase({ intento, total: 0, activos: 0, error: true }))
    return () => {
      activo = false
    }
  }, [intento])

  const busqueda = texto.trim()
  const estado = FILTROS[filtro]
  const filtros = `${estado}|${categoria}|${busqueda}`
  const pagina = elegida.filtros === filtros ? elegida.pagina : 1
  const clave = `${intento}|${filtros}|${pagina}`

  // Resultados de la página; mientras llegan los nuevos se conservan los anteriores
  useEffect(() => {
    let activo = true
    listarPaginaDeportistas({ texto: busqueda, estado, idCategoria: categoria, pagina })
      .then((resultado) => activo && setLista({ clave, resultado, error: false }))
      .catch((falla) => activo && setLista({ clave, resultado: null, error: true, mensaje: falla?.estado ? falla.mensaje : null }))
    return () => {
      activo = false
    }
  }, [busqueda, estado, categoria, pagina, clave])

  const cargandoBase = base.intento !== intento
  const actualizando = lista.clave !== clave
  const error = base.error || (!actualizando && lista.error)
  const vista = lista.resultado
  const items = vista?.items ?? null
  const fotosDeLaPagina = { ...fotos, ...Object.fromEntries((items ?? []).filter((d) => d.foto).map((d) => [d.id, d.foto])) }
  const nombresCategoria = Object.fromEntries(categorias.map((c) => [c.id, c.nombre]))
  // «Sin asignar» o el nombre de la categoría elegida (vacío si el filtro es «Todas las categorías»)
  const nombreFiltro = categoria === SIN_CATEGORIA ? 'Sin asignar' : nombresCategoria[categoria] ?? ''
  const filtroActivo = Boolean(busqueda) || filtro !== 'Todos' || categoria !== TODAS
  const limpiar = () => { setTexto(''); setFiltro('Todos'); setCategoria(TODAS) }
  const sufijoCategoria = nombreFiltro ? ` · Categoría: ${nombreFiltro}` : ''
  const detalleEstado = filtro === 'Todos' ? '' : filtro.toLowerCase()
  const detalleCategoria = !categoria ? '' : categoria === SIN_CATEGORIA ? 'sin categoría asignada' : `de la categoría ${nombreFiltro}`

  // Con la API, los 400/403 del backend se muestran con su mensaje
  const aviso = error ? { texto: lista.mensaje ?? 'No se pudo cargar la lista de deportistas.', accion: { texto: 'Reintentar', onClick: () => setIntento((n) => n + 1) } } : null
  // Mientras llegan resultados nuevos no se cuenta: el total anterior no corresponde a la categoría ya elegida
  const cuenta = vista === null || error || actualizando ? '' : vista.total === 0 ? `Sin resultados${sufijoCategoria}` : `${vista.total} ${vista.total === 1 ? 'resultado' : 'resultados'}${sufijoCategoria}`
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
  } else if (vista.total === 0) {
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
    contenido = <TablaDeportistas deportistas={vista.items} fotos={fotosDeLaPagina} nombresCategoria={nombresCategoria} actualizando={actualizando} />
  } else {
    contenido = <TarjetasDeportistas deportistas={vista.items} fotos={fotosDeLaPagina} nombresCategoria={nombresCategoria} actualizando={actualizando} />
  }

  return (
    <Page title="Deportistas" subtitle={subtitulo} tituloRef={tituloRef}>
      <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap md:self-start">
        <Button to="/administrador/deportistas/inscribir">Inscribir jugador</Button>
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
      {vista && !error && vista.total > 0 && (
        <Paginacion {...vista} onCambiar={(pagina) => setElegida({ filtros, pagina })} />
      )}
    </Page>
  )
}
