import { useState } from 'react'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Avatar from '../../components/ui/Avatar.jsx'
import Field from '../../components/ui/Field.jsx'
import FilterChips from '../../components/ui/FilterChips.jsx'
import EstadoDeCarga from '../../components/pagos/EstadoDeCarga.jsx'
import useCarga from '../../hooks/useCarga.js'
import { cargarMisJugadores, misJugadoresPorApi } from '../../services/entrenadorService.js'
import { textoCategoriaConGrupo } from '../../utils/categorias.js'
import {
  ORDENES_JUGADORES, TEXTO_SIN_POSICION, TODAS_LAS_CATEGORIAS, filtrarPorCategoria, mensajeDeErrorEntrenador, ordenarJugadores,
  textoEdad, textoJugadores,
} from '../../utils/entrenadorApi.js'

// Mis jugadores (entrenador): los deportistas activos de sus categorías, con datos reales de la API. Solo se dibuja lo que el
// entrenador recibe: foto, nombre, edad, posición, categoría y grupo (nunca documento, acudiente, teléfono ni estado de inscripción).
// La foto es una URL firmada temporal: si no carga, el Avatar muestra las iniciales.
const SIN_VINCULO = 'Tu cuenta todavía no está vinculada a un registro de entrenador. Pide al administrador que la revise.'
const SIN_CATEGORIAS = 'Todavía no tienes categorías asignadas. Cuando el administrador te asigne una, tus jugadores aparecerán aquí.'

function TarjetaJugador({ jugador }) {
  const nombre = `${jugador.nombre} ${jugador.apellido}`
  return (
    <Card as="li" className="flex items-center gap-3">
      <Avatar name={nombre} foto={jugador.foto} className="h-12 w-12" />
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <p className="break-words text-body-medium">{nombre}</p>
        <p className="break-words text-small text-secondary">
          {textoEdad(jugador.edad)} · {jugador.posicion ?? TEXTO_SIN_POSICION}
        </p>
        <div><Badge>{textoCategoriaConGrupo(jugador.categoria, jugador.grupoEntrenamiento)}</Badge></div>
      </div>
    </Card>
  )
}

function Contenido({ datos }) {
  const [categoria, setCategoria] = useState(TODAS_LAS_CATEGORIAS)
  const [orden, setOrden] = useState('apellido')
  const nombres = datos.categorias.map((c) => c.nombre)
  const activa = nombres.includes(categoria) ? categoria : TODAS_LAS_CATEGORIAS
  const visibles = ordenarJugadores(filtrarPorCategoria(datos.jugadores, activa), orden, nombres)

  let lista
  if (visibles.length > 0) {
    lista = (
      <ul aria-label="Lista de jugadores" className="grid grid-cols-1 gap-3 md:grid-cols-2">
        {visibles.map((j) => <TarjetaJugador key={j.id} jugador={j} />)}
      </ul>
    )
  } else {
    lista = (
      <EstadoDeCarga
        vacio={activa === TODAS_LAS_CATEGORIAS ? 'Tus categorías todavía no tienen jugadores activos.' : `No hay jugadores activos en ${activa}.`}
      />
    )
  }

  return (
    <>
      <div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        {nombres.length > 1 && <FilterChips label="Filtrar por categoría" options={[TODAS_LAS_CATEGORIAS, ...nombres]} value={activa} onChange={setCategoria} />}
        <div className="md:w-64">
          <Field label="Ordenar por" id="orden-jugadores" as="select" value={orden} onChange={(e) => setOrden(e.target.value)}>
            {ORDENES_JUGADORES.map((o) => <option key={o.valor} value={o.valor}>{o.etiqueta}</option>)}
          </Field>
        </div>
      </div>
      <p role="status" className="text-body text-secondary">
        {activa === TODAS_LAS_CATEGORIAS ? `${textoJugadores(visibles.length)} en tus categorías` : `${textoJugadores(visibles.length)} en ${activa}`}
      </p>
      {lista}
    </>
  )
}

// Vista pura (sin red): recibe el estado de la carga. La usan el contenedor de abajo y las pruebas.
export function VistaMisJugadores({ conectado, cargando, datos, error, onReintentar }) {
  let vacio = null
  if (!conectado) vacio = 'Esta pantalla muestra datos reales y el módulo todavía no está conectado a la API.'
  else if (datos && !datos.perfil) vacio = SIN_VINCULO
  else if (datos && datos.categorias.length === 0) vacio = SIN_CATEGORIAS

  return (
    <Page title="Mis jugadores" subtitle="Jugadores activos de las categorías que diriges">
      <EstadoDeCarga
        cargando={cargando}
        mensajeError={error ? mensajeDeErrorEntrenador(error, 'No se pudieron cargar tus jugadores.') : null}
        onReintentar={onReintentar}
        vacio={vacio}
        texto="Cargando tus jugadores…"
      >
        {datos && <Contenido datos={datos} />}
      </EstadoDeCarga>
    </Page>
  )
}

export default function MisJugadores() {
  const conectado = misJugadoresPorApi()
  const { cargando, datos, error, recargar } = useCarga(cargarMisJugadores, conectado)
  return <VistaMisJugadores conectado={conectado} cargando={cargando} datos={datos} error={error} onReintentar={recargar} />
}
