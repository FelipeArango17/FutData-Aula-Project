import { useEffect, useMemo, useRef, useState } from 'react'
import Page from '../../components/ui/Page.jsx'
import Button from '../../components/ui/Button.jsx'
import Field from '../../components/ui/Field.jsx'
import FilterChips from '../../components/ui/FilterChips.jsx'
import AvisoEstado from '../../components/ui/AvisoEstado.jsx'
import DialogoConfirmacion from '../../components/ui/DialogoConfirmacion.jsx'
import Icon from '../../components/Icon.jsx'
import SesionesSemana from '../../components/horario/SesionesSemana.jsx'
import { categoriaDeSesion, rangoDeSesion } from '../../components/horario/formatoSesion.js'
import { DialogoCambiarEntrenador, DialogoCancelar, DialogoExtra } from '../../components/horario/DialogosHorario.jsx'
import { useRole } from '../../context/RoleContext.js'
import * as servicio from '../../services/semanaService.js'
import { perfilEntrenador } from '../../services/entrenadorService.js'
import { entrenadorDeCategoria } from '../../services/horarioClubData.js'
import { diaLargo, diasDeSemana, lunesDe, rangoSemana, sumarDias } from '../../utils/fechas.js'
import { hoy } from '../../utils/hoy.js'
import { VISTA_CLUB, VISTA_MIS, esDeMisCategorias, estadoPerfil, opcionesDeCategoria } from '../../utils/horarioEntrenador.js'
import { SEDES_CLUB, accionesDe, categoriasCreables, puedeCrearExtra, semanaEnCurso, textoAviso } from '../../utils/semana.js'


// Horarios: UNA pantalla para acudiente, entrenador y administrador (el tesorero no la tiene en el menú).
// Las franjas son fijas todo el año: aquí no se crean ni se borran. Lo que sí se hace, según el rol y la capacidad del servicio:
// cancelar el entrenamiento de una fecha (y reactivarlo), agregar entrenamientos extra (editarlos y eliminarlos) y, solo el
// administrador, cambiar el entrenador de una franja para todas las semanas. Lo que el servicio no ofrece (modo API sin el
// contrato 4B) NO se muestra. El acudiente ve solo la semana en curso; administrador y entrenador navegan semanas.
const TODAS = 'Todas'
const MIS_CATEGORIAS = 'Mis categorías'
const SIN_SEDE = ''

const textoError = (error, general) => (error?.estado !== undefined && error.mensaje ? error.mensaje : general)

function Mensaje({ icono = 'info', rol, children }) {
  return (
    <div role={rol} className="flex items-start gap-3 rounded-lg border bg-surface p-4">
      <Icon name={icono} className="mt-0.5 h-5 w-5 shrink-0 text-secondary" />
      <div className="flex min-w-0 flex-1 flex-col gap-3 break-words text-body">{children}</div>
    </div>
  )
}

export default function Horarios() {
  const { role } = useRole()
  const zona = useRef(null)
  const capacidades = useMemo(() => servicio.capacidades(), [])
  const navega = role === 'administrador' || role === 'entrenador'
  const identidad = role === 'entrenador' ? servicio.identidadEntrenador() : null
  // Modo API: el entrenador se identifica con GET /api/usuarios/yo (idEntrenador y categorías con id). En ejemplo, con identidadEntrenador().
  const perfilPorApi = role === 'entrenador' && servicio.semanaPorApi()
  const [intentoPerfil, setIntentoPerfil] = useState(0)
  const [cargaPerfil, setCargaPerfil] = useState({ intento: null, perfil: null, error: false })
  const [vista, setVista] = useState(VISTA_MIS)
  const hoyTexto = hoy()
  const lunesActual = semanaEnCurso(hoyTexto)

  const [semana, setSemana] = useState(lunesActual)
  const lunes = navega ? semana : lunesActual
  const [intento, setIntento] = useState(0)
  const clave = `${lunes}|${intento}`
  const [carga, setCarga] = useState({ clave: null, sesiones: [], error: false })
  const [filtro, setFiltro] = useState(MIS_CATEGORIAS)
  const [sede, setSede] = useState(SIN_SEDE)
  const [entrenadores, setEntrenadores] = useState([])
  const [aviso, setAviso] = useState(null)
  const [errorAccion, setErrorAccion] = useState(null)
  const [compacta, setCompacta] = useState(false)
  const [porCancelar, setPorCancelar] = useState(null)
  const [porRotar, setPorRotar] = useState(null)
  const [extra, setExtra] = useState(null) // null | { sesion: null } (nuevo) | { sesion } (editar)
  const [porEliminar, setPorEliminar] = useState(null)

  useEffect(() => {
    let activo = true
    servicio.listarSemana(lunes)
      .then((sesiones) => activo && setCarga({ clave, sesiones, error: false }))
      .catch(() => activo && setCarga({ clave, sesiones: [], error: true }))
    return () => {
      activo = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- `clave` ya resume lunes e intento
  }, [clave])

  useEffect(() => {
    if (!perfilPorApi) return undefined
    let activo = true
    perfilEntrenador()
      .then((perfil) => activo && setCargaPerfil({ intento: intentoPerfil, perfil, error: false }))
      .catch(() => activo && setCargaPerfil({ intento: intentoPerfil, perfil: null, error: true }))
    return () => {
      activo = false
    }
  }, [perfilPorApi, intentoPerfil])

  // Los entrenadores solo hacen falta para cambiar el entrenador de una franja o asignar un extra. En la API solo el
  // administrador puede listarlos; los demás roles usan lo que trae cada franja.
  const necesitaEntrenadores = role === 'administrador' || (role === 'entrenador' && !servicio.semanaPorApi())
  useEffect(() => {
    if (!necesitaEntrenadores) return undefined
    let activo = true
    servicio.listarEntrenadores()
      .then((lista) => activo && setEntrenadores(lista))
      .catch(() => activo && setErrorAccion({ texto: 'No se pudo cargar la lista de entrenadores. Recarga la página para cambiar entrenadores.' }))
    return () => {
      activo = false
    }
  }, [necesitaEntrenadores])

  // Cuadrícula en pantallas anchas y lista por día en las angostas o con letra grande (se mide en rem)
  useEffect(() => {
    const observador = new ResizeObserver(([entrada]) => {
      const rem = parseFloat(getComputedStyle(document.documentElement).fontSize)
      setCompacta(entrada.contentRect.width / rem < 48)
    })
    observador.observe(zona.current)
    return () => observador.disconnect()
  }, [])

  const recargar = () => setIntento((n) => n + 1)
  const cargandoSemana = carga.clave !== clave
  const dias = diasDeSemana(lunes)
  const cargandoPerfil = perfilPorApi && cargaPerfil.intento !== intentoPerfil
  const cargando = cargandoSemana || cargandoPerfil
  const perfil = perfilPorApi && !cargandoPerfil ? cargaPerfil.perfil : null
  const estado = perfilPorApi ? estadoPerfil({ cargando: cargandoPerfil, error: cargaPerfil.error, perfil }) : null
  const conVista = estado === 'ok'
  const soloMias = conVista && vista === VISTA_MIS
  const propias = perfilPorApi ? (perfil ? perfil.categorias.map((c) => c.nombre) : null) : identidad?.categorias ?? null
  const esPropia = (s) => esDeMisCategorias(s, perfil)
  const conMisCategorias = role === 'entrenador' && !perfilPorApi && Boolean(propias)
  const opcionesCategoria = soloMias ? opcionesDeCategoria({ soloMias, perfil }) : [...(conMisCategorias ? [MIS_CATEGORIAS] : []), ...opcionesDeCategoria({ soloMias: false })]
  const filtroActivo = opcionesCategoria.includes(filtro) ? filtro : TODAS
  const visibles = carga.sesiones.filter(
    (s) => (!soloMias || esPropia(s))
      && (filtroActivo === TODAS || (filtroActivo === MIS_CATEGORIAS ? propias.includes(s.categoria) : s.categoria === filtroActivo))
      && (!sede || s.sede === sede),
  )
  const filtrando = filtroActivo !== TODAS || Boolean(sede)
  const creables = categoriasCreables(role, propias)
  const puedeExtra = puedeCrearExtra(role, capacidades, propias)
  const acciones = (sesion) => accionesDe(sesion, { rol: role, propias, capacidades, hoy: hoyTexto })

  // Ejecuta una acción del servicio: aviso si sale bien, mensaje del backend (403, 404, 409…) si no
  async function ejecutar(accion, textoOk, generico) {
    setAviso(null)
    setErrorAccion(null)
    try {
      await accion()
      setAviso({ texto: textoOk })
      recargar()
    } catch (error) {
      setErrorAccion({ texto: textoError(error, generico) })
      if (error?.estado === 404 || error?.codigo === 'NO_EXISTE') recargar()
    }
  }

  async function confirmarCancelar(motivo) {
    const sesion = porCancelar
    await servicio.cancelar({ fecha: sesion.fecha, idFranja: sesion.idFranja, motivo })
    setPorCancelar(null)
    setAviso({ texto: `Entrenamiento cancelado. ${textoAviso(sesion.categoria)}` })
    recargar()
  }

  async function confirmarRotar(idEntrenador) {
    await servicio.cambiarEntrenadorDeFranja(porRotar.idFranja, idEntrenador)
    setPorRotar(null)
    setAviso({ texto: 'Entrenador cambiado en todas las semanas.' })
    recargar()
  }

  async function guardarExtra(valores) {
    const sesion = extra.sesion
    if (sesion) await servicio.editarExtra(sesion.idExcepcion, valores)
    else await servicio.crearExtra(valores)
    setExtra(null)
    setAviso({ texto: `${sesion ? 'Entrenamiento extra actualizado.' : 'Entrenamiento extra creado.'} ${textoAviso(valores.categoria)}` })
    if (navega) setSemana(lunesDe(valores.fecha))
    recargar()
  }

  function confirmarEliminar() {
    const sesion = porEliminar
    setPorEliminar(null)
    return ejecutar(() => servicio.eliminarExtra(sesion.idExcepcion), 'Entrenamiento extra eliminado.', 'No se pudo eliminar el entrenamiento extra. Intenta de nuevo.')
  }

  const inicialExtra = extra?.sesion
    ? {
        categoria: extra.sesion.categoria, fecha: extra.sesion.fecha, hora: extra.sesion.hora, horaFin: extra.sesion.horaFin, sede: extra.sesion.sede,
        idEntrenador: extra.sesion.idEntrenador === null ? '' : String(extra.sesion.idEntrenador), grupo: extra.sesion.grupo ?? '', motivo: extra.sesion.motivo ?? '',
      }
    : {
        categoria: creables.length === 1 ? creables[0] : '', fecha: '', hora: '', horaFin: '', sede: '',
        idEntrenador: identidad ? String(identidad.id) : '', grupo: '', motivo: '',
      }

  let contenido
  if (carga.error && !cargando) {
    contenido = (
      <AvisoEstado aviso={{ texto: 'No se pudo cargar el horario.', accion: { texto: 'Reintentar', onClick: recargar } }} rol="alert" tono="danger" enfocar={false} />
    )
  } else if (cargando && (carga.sesiones.length === 0 || cargandoPerfil)) {
    contenido = <Mensaje icono="clock"><p role="status">Cargando el horario…</p></Mensaje>
  } else if (visibles.length === 0) {
    contenido = (
      <Mensaje>
        <p>{filtrando ? 'No hay entrenamientos con esos filtros esta semana.' : 'No hay entrenamientos esta semana.'}</p>
        {filtrando && <Button variant="secondary" onClick={() => { setFiltro(TODAS); setSede(SIN_SEDE) }} className="sm:self-start">Limpiar filtros</Button>}
      </Mensaje>
    )
  } else {
    contenido = (
      <SesionesSemana
        dias={dias}
        sesiones={visibles}
        hoy={hoyTexto}
        modo={compacta ? 'lista' : 'cuadricula'}
        accionesDe={acciones}
        miId={perfil?.idEntrenador ?? null}
        esPropia={conVista && vista === VISTA_CLUB ? esPropia : undefined}
        onCancelar={(s) => { setAviso(null); setErrorAccion(null); setPorCancelar(s) }}
        onReactivar={(s) => ejecutar(() => servicio.reactivar(s.idExcepcion), 'Entrenamiento reactivado.', 'No se pudo reactivar el entrenamiento. Intenta de nuevo.')}
        onRotar={(s) => { setAviso(null); setErrorAccion(null); setPorRotar(s) }}
        onEditarExtra={(s) => { setAviso(null); setErrorAccion(null); setExtra({ sesion: s }) }}
        onEliminarExtra={setPorEliminar}
      />
    )
  }

  return (
    <Page title="Horarios" subtitle={navega ? `Semana del ${rangoSemana(lunes)}` : `Semana en curso · del ${rangoSemana(lunes)}`}>
      <AvisoEstado aviso={aviso} onCerrar={() => setAviso(null)} />
      <AvisoEstado aviso={errorAccion} rol="alert" tono="danger" onCerrar={() => setErrorAccion(null)} />

      {(puedeExtra || navega) && (
        <div className="flex flex-wrap gap-2">
          {puedeExtra && <Button onClick={() => { setAviso(null); setErrorAccion(null); setExtra({ sesion: null }) }} className="w-full md:w-auto">Agregar entrenamiento extra</Button>}
          {navega && (
            <>
              <Button variant="secondary" onClick={() => setSemana(sumarDias(lunes, -7))} className="grow md:grow-0">Semana anterior</Button>
              <Button variant="secondary" onClick={() => setSemana(lunesActual)} className="grow md:grow-0">Hoy</Button>
              <Button variant="secondary" onClick={() => setSemana(sumarDias(lunes, 7))} className="grow md:grow-0">Semana siguiente</Button>
            </>
          )}
        </div>
      )}

      {estado === 'error' && (
        <AvisoEstado
          aviso={{ texto: 'No se pudo cargar tu perfil de entrenador, por eso se muestra todo el club.', accion: { texto: 'Reintentar', onClick: () => setIntentoPerfil((n) => n + 1) } }}
          rol="alert"
          tono="danger"
          enfocar={false}
        />
      )}
      {estado === 'sin-categorias' && (
        <AvisoEstado
          aviso={{ texto: perfil ? 'Tu cuenta no tiene categorías asignadas, por eso se muestra todo el club.' : 'Tu cuenta no tiene un entrenador vinculado, por eso se muestra todo el club.' }}
          tono="warning"
          enfocar={false}
        />
      )}
      {conVista && <FilterChips label="Vista del horario" options={[VISTA_MIS, VISTA_CLUB]} value={vista} onChange={setVista} />}
      <FilterChips
        label="Categoría"
        options={opcionesCategoria}
        value={filtroActivo}
        onChange={setFiltro}
      />
      <div className="max-w-xs">
        <Field label="Sede" id="filtro-sede" as="select" value={sede} onChange={(e) => setSede(e.target.value)}>
          <option value={SIN_SEDE}>Todas las sedes</option>
          {SEDES_CLUB.map((s) => <option key={s} value={s}>{s}</option>)}
        </Field>
      </div>
      <p role="status" className="min-h-[1.125rem] text-small text-secondary">
        {cargando && carga.sesiones.length === 0 ? '' : `${visibles.length} ${visibles.length === 1 ? 'entrenamiento' : 'entrenamientos'}${filtrando ? ' con los filtros elegidos' : ''}`}
      </p>

      <div ref={zona} className="min-w-0">{contenido}</div>

      <DialogoCancelar sesion={porCancelar} onConfirmar={confirmarCancelar} onCerrar={() => setPorCancelar(null)} />
      <DialogoCambiarEntrenador sesion={porRotar} entrenadores={entrenadores} onConfirmar={confirmarRotar} onCerrar={() => setPorRotar(null)} />
      <DialogoExtra
        extra={extra}
        inicial={inicialExtra}
        categorias={creables}
        entrenadores={entrenadores}
        entrenadorFijo={identidad ? identidad.id : null}
        hoy={hoyTexto}
        proponerEntrenador={entrenadorDeCategoria}
        detectarConflicto={(valores) => servicio.detectarConflicto(valores, extra?.sesion?.idExcepcion ?? null)}
        onGuardar={guardarExtra}
        onCerrar={() => setExtra(null)}
      />
      <DialogoConfirmacion
        open={Boolean(porEliminar)}
        titulo="¿Eliminar este entrenamiento extra?"
        descripcion={porEliminar ? `${categoriaDeSesion(porEliminar)} · ${porEliminar.sede} · ${diaLargo(porEliminar.fecha)}, ${rangoDeSesion(porEliminar)}. Esta acción no se puede deshacer.` : ''}
        textoConfirmar="Sí, eliminar"
        onConfirmar={confirmarEliminar}
        onCancelar={() => setPorEliminar(null)}
      />
    </Page>
  )
}
