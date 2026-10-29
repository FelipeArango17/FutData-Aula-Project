import { useEffect, useRef, useState } from 'react'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import EstadoDeportista from '../../components/deportistas/EstadoDeportista.jsx'
import Avatar from '../../components/ui/Avatar.jsx'
import Button from '../../components/ui/Button.jsx'
import Field from '../../components/ui/Field.jsx'
import AvisoEstado from '../../components/ui/AvisoEstado.jsx'
import Icon from '../../components/Icon.jsx'
import { useRole } from '../../context/RoleContext.js'
import { calcularEdad, listarCategorias, listarDeportistas } from '../../services/deportistasService.js'
import { obtener as obtenerEntrenador } from '../../services/entrenadoresService.js'
import { ID_ENTRENADOR_EJEMPLO } from '../../services/entrenamientosService.js'

// Lista de deportistas por grupo (categoría) (HU-011, tarea 321). Una sola pantalla para administrador y entrenador.
// - administrador (coordinador deportivo): ve todos los grupos vigentes.
// - entrenador: ve solo las categorías que tiene asignadas en entrenadoresService.
// PENDIENTE (decisión D1): ¿una categoría tiene un solo entrenador o varios? Aquí se filtra por la lista de
// categorías del entrenador, que sirve en ambos casos. Además, entrenamientosService.categoriasPorRol.entrenador
// (Sub-10 y Sub-12) no coincide con las categorías de Andrés Mora en entrenadoresService (Sub-12); aquí manda entrenadoresService.
// No se muestran datos sensibles (ficha médica, teléfono del menor).

const plural = (n) => `${n} ${n === 1 ? 'deportista' : 'deportistas'}`

const nombreCompleto = (d) => `${d.nombre} ${d.apellido}`
const textoEdad = (d) => `${calcularEdad(d.fechaNacimiento)} años`

function Mensaje({ icono, children }) {
  return (
    <div className="flex items-start gap-3 rounded-lg border bg-surface p-4">
      <Icon name={icono} className="mt-0.5 h-5 w-5 shrink-0 text-secondary" />
      <div className="flex min-w-0 flex-1 flex-col gap-3 text-body break-words">{children}</div>
    </div>
  )
}

export default function ListaPorGrupo() {
  const { role } = useRole()
  const tituloRef = useRef(null)
  const zonaLista = useRef(null)
  const [conTabla, setConTabla] = useState(false)
  const [intento, setIntento] = useState(0)
  const [elegido, setElegido] = useState(null)
  const [texto, setTexto] = useState('')
  const [anuncio, setAnuncio] = useState('')
  // Resultados con la clave de la petición que los produjo: si la clave no coincide, está cargando (sin setState síncrono en el efecto)
  const [carga, setCarga] = useState({ intento: -1 })
  const [lista, setLista] = useState({ clave: null, idGrupo: null, items: [], error: false })

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

  // Grupos visibles para el rol + cuántos deportistas tiene cada uno
  useEffect(() => {
    let activo = true
    async function cargar() {
      try {
        const [categorias, todos, entrenador] = await Promise.all([
          listarCategorias(),
          listarDeportistas(),
          role === 'entrenador' ? obtenerEntrenador(ID_ENTRENADOR_EJEMPLO) : null,
        ])
        const asignadas = entrenador?.categorias
        const grupos = categorias.filter((c) => c.activa && (!asignadas || asignadas.includes(c.nombre)))
        const conteos = Object.fromEntries(grupos.map((g) => [g.id, todos.filter((d) => d.idCategoria === g.id).length]))
        if (activo) setCarga({ intento, grupos, conteos, error: false })
      } catch {
        if (activo) setCarga({ intento, grupos: [], conteos: {}, error: true })
      }
    }
    cargar()
    return () => {
      activo = false
    }
  }, [role, intento])

  const cargandoGrupos = carga.intento !== intento
  const grupos = carga.grupos ?? []
  const grupo = grupos.find((g) => g.id === elegido) ?? grupos[0]
  const idGrupo = grupo?.id
  const busqueda = texto.trim()
  const clave = `${intento}|${idGrupo}|${busqueda}`

  // Deportistas del grupo y la búsqueda; al buscar se conserva la lista anterior mientras llega la nueva
  useEffect(() => {
    if (idGrupo === undefined) return
    let activo = true
    listarDeportistas({ idCategoria: idGrupo, texto: busqueda })
      .then((items) => activo && setLista({ clave, idGrupo, items, error: false }))
      .catch(() => activo && setLista({ clave, idGrupo, items: [], error: true }))
    return () => {
      activo = false
    }
  }, [idGrupo, busqueda, clave])

  const actualizando = lista.clave !== clave
  const error = carga.error || (!actualizando && lista.error)
  // Al buscar se muestra la lista anterior del MISMO grupo mientras llega la nueva; al cambiar de grupo, no
  const items = lista.idGrupo === idGrupo ? lista.items : null

  function cambiarGrupo(e) {
    const id = Number(e.target.value)
    setElegido(id)
    const nuevo = grupos.find((g) => g.id === id)
    setAnuncio(`${plural(carga.conteos[id])} en ${nuevo.nombre}`)
  }

  const aviso = error ? { texto: 'No se pudo cargar la lista de deportistas.', accion: { texto: 'Reintentar', onClick: () => setIntento((n) => n + 1) } } : null

  let contenido
  if (cargandoGrupos) {
    contenido = (
      <Mensaje icono="clock">
        <p role="status">Cargando deportistas…</p>
      </Mensaje>
    )
  } else if (error) {
    contenido = null
  } else if (!grupo) {
    contenido = (
      <Mensaje icono="info">
        <p>{role === 'entrenador' ? 'Todavía no tienes grupos asignados. Pídele al coordinador deportivo que te asigne uno.' : 'No hay grupos vigentes.'}</p>
      </Mensaje>
    )
  } else if (items === null) {
    contenido = (
      <Mensaje icono="clock">
        <p role="status">Cargando deportistas…</p>
      </Mensaje>
    )
  } else if (items.length === 0 && !actualizando) {
    contenido = busqueda ? (
      <Mensaje icono="info">
        <p>No hay deportistas en {grupo.nombre} que coincidan con «{busqueda}».</p>
        <Button variant="secondary" onClick={() => setTexto('')} className="sm:self-start">Limpiar búsqueda</Button>
      </Mensaje>
    ) : (
      <Mensaje icono="info">
        <p>El grupo {grupo.nombre} todavía no tiene deportistas.</p>
      </Mensaje>
    )
  } else {
    contenido = (
      <div aria-busy={actualizando}>
        {conTabla ? (
          <table className="w-full table-fixed border-collapse rounded-lg border bg-surface text-left text-body">
            <caption className="sr-only">Deportistas del grupo {grupo.nombre}</caption>
            <thead>
              <tr className="border-b bg-app">
                <th scope="col" className="p-3 text-body-medium">Deportista</th>
                <th scope="col" className="w-[5.5rem] p-3 text-body-medium">Edad</th>
                <th scope="col" className="w-[8.5rem] p-3 text-body-medium">Documento</th>
                <th scope="col" className="w-[7.5rem] p-3 text-body-medium">Estado</th>
              </tr>
            </thead>
            <tbody>
              {items.map((d) => (
                <tr key={d.id} className="border-b last:border-b-0">
                  <th scope="row" className="p-3 font-normal">
                    <div className="flex min-w-0 items-center gap-3">
                      <Avatar name={nombreCompleto(d)} />
                      <span className="min-w-0 break-words text-body-medium">{nombreCompleto(d)}</span>
                    </div>
                  </th>
                  <td className="p-3">{textoEdad(d)}</td>
                  <td className="break-words p-3">{d.documento}</td>
                  <td className="p-3"><EstadoDeportista activo={d.activo} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
        <ul className="grid grid-cols-1 gap-4" aria-label={`Deportistas del grupo ${grupo.nombre}`}>
          {items.map((d) => (
            <Card as="li" key={d.id} className="flex flex-col gap-3">
              <div className="flex min-w-0 items-center gap-3">
                <Avatar name={nombreCompleto(d)} />
                <p className="min-w-0 break-words text-body-medium">{nombreCompleto(d)}</p>
              </div>
              <p className="break-words text-small text-secondary">{textoEdad(d)} · Documento: {d.documento}</p>
              <div className="flex flex-wrap"><EstadoDeportista activo={d.activo} /></div>
            </Card>
          ))}
        </ul>
        )}
      </div>
    )
  }

  return (
    <Page
      title="Deportistas por grupo"
      subtitle={role === 'entrenador' ? 'Los deportistas de tus grupos' : 'Todos los grupos vigentes del club'}
      tituloRef={tituloRef}
    >
      <p aria-live="polite" className="sr-only">{anuncio}</p>
      <AvisoEstado aviso={aviso} rol="alert" tono="danger" enfocar={false} />
      {grupos.length > 0 && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Field label="Grupo (categoría)" id="grupo" as="select" value={idGrupo ?? ''} onChange={cambiarGrupo}>
            {grupos.map((g) => (
              <option key={g.id} value={g.id}>{g.nombre} · {plural(carga.conteos[g.id])}</option>
            ))}
          </Field>
          <Field
            label="Buscar por nombre o documento"
            id="buscar"
            type="search"
            autoComplete="off"
            value={texto}
            onChange={(e) => setTexto(e.target.value)}
          />
        </div>
      )}
      {grupo && !error && <h2 className="break-words text-h3">{grupo.nombre} · {plural(carga.conteos[grupo.id])}</h2>}
      <div ref={zonaLista} className="min-w-0">{contenido}</div>
    </Page>
  )
}
