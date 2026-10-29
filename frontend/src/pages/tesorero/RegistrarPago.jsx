import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Button from '../../components/ui/Button.jsx'
import Field from '../../components/ui/Field.jsx'
import AvisoEstado from '../../components/ui/AvisoEstado.jsx'
import DialogoConfirmacion from '../../components/ui/DialogoConfirmacion.jsx'
import Icon from '../../components/Icon.jsx'
import { useActor } from '../../context/useActor.js'
import { HOY_EJEMPLO, listarDeportistas } from '../../services/deportistasService.js'
import {
  MAX_NOTA, MAX_REFERENCIA, METODOS_PAGO_MANUAL, consultarCobroMensualidad, mesesParaPago, registrarPagoManual, validarDatosPagoManual,
} from '../../services/pagosService.js'
import { fechaLargaConAnio, mesEnTexto } from '../../utils/fechas.js'
import { formatearCOP } from '../../utils/dinero.js'

// Registro manual de un pago en efectivo o por transferencia (HU-016, tarea 324). La usan el tesorero y el administrador
// (/tesorero/pagos/registrar y /administrador/pagos/registrar); `base` pone las rutas del rol. Reemplaza la maqueta anterior.
// Figma (T2b · Registrar pago) trae Concepto, Valor, Método y Fecha de pago, y un texto "Al guardar se genera el comprobante y se
// notifica al acudiente por correo", más el botón "Guardar y generar comprobante": el comprobante y la notificación no están en
// esta tarea, así que no se incluyen. Se agregan buscador de deportista, mes, referencia y nota interna.
// PENDIENTE: la tarifa de la mensualidad, abonos y anular o corregir un pago (por eso se pide confirmar antes de registrar).
// Privacidad: la pantalla solo muestra nombre y documento del deportista (nada de teléfono ni dirección).
const MES_ACTUAL = HOY_EJEMPLO.slice(0, 7)
const INICIAL = { mes: MES_ACTUAL, monto: '', fechaPago: HOY_EJEMPLO, metodo: '', referencia: '', nota: '' }
const MAX_RESULTADOS = 6
const nombreCompleto = (d) => `${d.nombre} ${d.apellido}`

const claseChip = (marcado) =>
  `flex min-h-11 cursor-pointer flex-col items-center justify-center gap-0.5 rounded-md border px-1 text-center text-body-medium focus-within:ring-2 focus-within:ring-focus focus-within:ring-offset-2 sm:flex-row sm:gap-1.5 sm:px-3 ${
    marcado ? 'border-transparent bg-brand-primary font-semibold text-on-dark' : 'bg-surface text-primary hover:bg-app'
  }`

export default function RegistrarPago({ base = '/tesorero' }) {
  const actor = useActor()
  const navigate = useNavigate()
  const tituloRef = useRef(null)
  const formRef = useRef(null)
  const enVuelo = useRef(false)
  const meses = mesesParaPago()

  const [busqueda, setBusqueda] = useState('')
  const [seleccionado, setSeleccionado] = useState(null) // { id, nombre, documento }
  const [valores, setValores] = useState(INICIAL)
  const [errores, setErrores] = useState({})
  const [errorGeneral, setErrorGeneral] = useState(null)
  const [guardando, setGuardando] = useState(false)
  const [intentos, setIntentos] = useState(0)
  const [confirmar, setConfirmar] = useState(false)
  const [resultado, setResultado] = useState(null) // { aviso, resumen }
  const [formulario, setFormulario] = useState(0)
  const [recarga, setRecarga] = useState(0) // vuelve a consultar el cobro (por ejemplo tras un YA_PAGADO)
  // Resultados con la clave de la petición que los produjo: si no coincide, está cargando (sin setState síncrono en el efecto)
  const [lista, setLista] = useState({ clave: null, items: [], error: false })
  const [cobro, setCobro] = useState({ clave: null, datos: null, error: false })

  // Al abrir, y al empezar otro registro, el foco va al título
  useEffect(() => {
    tituloRef.current?.focus()
  }, [formulario])

  // Tras un envío con errores, el foco va al primer campo inválido
  useEffect(() => {
    if (intentos > 0) formRef.current?.querySelector('[aria-invalid="true"]')?.focus()
  }, [intentos])

  // Buscador: solo deportistas activos, por nombre o documento
  const texto = busqueda.trim()
  const claveLista = texto ? `${formulario}|${texto}` : null
  useEffect(() => {
    if (!claveLista) return
    let activo = true
    listarDeportistas({ texto, estado: 'activos' })
      .then((items) => activo && setLista({ clave: claveLista, items, error: false }))
      .catch(() => activo && setLista({ clave: claveLista, items: [], error: true }))
    return () => {
      activo = false
    }
  }, [claveLista, texto])

  // Cobro de la mensualidad del deportista elegido y el mes elegido: prellena el monto y avisa si ya está pagada
  const claveCobro = seleccionado ? `${seleccionado.id}|${valores.mes}|${recarga}` : null
  useEffect(() => {
    if (!claveCobro) return
    let activo = true
    consultarCobroMensualidad(seleccionado.id, valores.mes, actor)
      .then((datos) => activo && setCobro({ clave: claveCobro, datos, error: false }))
      .catch(() => activo && setCobro({ clave: claveCobro, datos: null, error: true }))
    return () => {
      activo = false
    }
  }, [claveCobro, seleccionado, valores.mes, actor])

  const buscandoLista = claveLista !== null && lista.clave !== claveLista
  const resultados = claveLista && lista.clave === claveLista ? lista.items : []
  // Lo que se dibuja como opciones: los resultados (máx. 6) y, si el deportista elegido ya no sale en la búsqueda, su tarjeta
  // fijada arriba (así la selección nunca queda invisible)
  const visibles = resultados.slice(0, MAX_RESULTADOS).map((d) => ({ id: d.id, nombre: nombreCompleto(d), documento: d.documento }))
  const tarjetas = seleccionado && !visibles.some((d) => d.id === seleccionado.id) ? [seleccionado, ...visibles] : visibles
  const consultandoCobro = claveCobro !== null && cobro.clave !== claveCobro
  const datosCobro = claveCobro && cobro.clave === claveCobro ? cobro.datos : null
  const yaPagado = datosCobro?.existe && datosCobro.estado === 'pagado'
  const montoBloqueado = Boolean(datosCobro?.existe && !yaPagado)
  const montoMostrado = montoBloqueado ? String(datosCobro.monto) : valores.monto
  const conceptoMes = datosCobro?.existe ? datosCobro.concepto : `Mensualidad ${mesEnTexto(`${valores.mes}-01`)}`

  function cambiar(campo, valor) {
    setValores({ ...valores, [campo]: valor })
    if (errores[campo]) setErrores({ ...errores, [campo]: undefined })
    setErrorGeneral(null)
  }

  function elegir(d) {
    setSeleccionado({ id: d.id, nombre: d.nombre, documento: d.documento })
    setErrores({ ...errores, idDeportista: undefined })
    setErrorGeneral(null)
  }

  const datosPago = () => ({ idDeportista: seleccionado?.id ?? '', ...valores, monto: montoMostrado })

  function revisarYConfirmar(event) {
    event.preventDefault()
    if (enVuelo.current || yaPagado) return
    setErrorGeneral(null)
    const encontrados = validarDatosPagoManual(datosPago())
    setErrores(encontrados)
    if (Object.keys(encontrados).length > 0) return setIntentos((n) => n + 1)
    setConfirmar(true)
  }

  async function registrar() {
    setConfirmar(false)
    if (enVuelo.current) return
    enVuelo.current = true
    setGuardando(true)
    setErrorGeneral(null)
    try {
      const { pago, cobroCreado } = await registrarPagoManual(datosPago(), actor)
      setResultado({
        aviso: { texto: 'Pago registrado.' },
        resumen: { deportista: seleccionado.nombre, concepto: pago.concepto, monto: pago.monto, metodo: pago.metodo, referencia: pago.referencia, fechaPago: pago.fechaPago, cobroCreado },
      })
    } catch (error) {
      if (error.codigo === 'VALIDACION') {
        setErrores(error.errores)
        setIntentos((n) => n + 1)
      } else if (error.codigo === 'MONTO_DISTINTO') {
        setErrores({ monto: `El monto debe ser igual al del cobro: ${formatearCOP(error.esperado)}.` })
        setIntentos((n) => n + 1)
      } else if (error.codigo === 'YA_PAGADO') {
        setErrorGeneral({ texto: error.message })
        setRecarga((n) => n + 1)
      } else if (['DEPORTISTA_INACTIVO', 'DEPORTISTA_NO_EXISTE', 'SIN_PERMISO'].includes(error.codigo)) {
        setErrorGeneral({ texto: error.message })
      } else {
        setErrorGeneral({ texto: 'No se pudo registrar el pago. Revisa tu conexión; lo que escribiste no se perdió.', accion: { texto: 'Reintentar', onClick: registrar } })
      }
    } finally {
      enVuelo.current = false
      setGuardando(false)
    }
  }

  function registrarOtro() {
    setResultado(null)
    setBusqueda('')
    setSeleccionado(null)
    setValores(INICIAL)
    setErrores({})
    setErrorGeneral(null)
    setFormulario((n) => n + 1)
  }

  const hayCambios = seleccionado !== null || busqueda !== '' || JSON.stringify(valores) !== JSON.stringify(INICIAL)
  const [confirmarSalir, setConfirmarSalir] = useState(false)
  const cerrarConfirmacion = useCallback(() => setConfirmar(false), [])
  const cerrarSalir = useCallback(() => setConfirmarSalir(false), [])
  const cancelar = () => (hayCambios ? setConfirmarSalir(true) : navigate(`${base}/pagos`))

  const cantidadErrores = Object.values(errores).filter(Boolean).length
  const aviso = cantidadErrores > 0
    ? { texto: `Revisa los campos marcados: ${cantidadErrores} ${cantidadErrores === 1 ? 'campo necesita' : 'campos necesitan'} corrección.` }
    : errorGeneral

  const montoNumero = /^\d+$/.test(montoMostrado.trim()) ? Number(montoMostrado.trim()) : null
  const ayudaMonto = montoNumero ? `Equivale a ${formatearCOP(montoNumero)}.` : 'Escribe solo números, sin puntos ni símbolo $.'

  if (resultado) {
    const r = resultado.resumen
    return (
      <Page title="Registrar pago" subtitle="Pago en efectivo o por transferencia" tituloRef={tituloRef}>
        <AvisoEstado aviso={resultado.aviso} />
        <Card aria-labelledby="resumen-pago" className="flex w-full max-w-2xl flex-col gap-4">
          <h2 id="resumen-pago" className="break-words text-h3">Resumen del pago</h2>
          <dl className="grid grid-cols-[repeat(auto-fit,minmax(min(100%,14rem),1fr))] gap-4">
            {[
              ['Deportista', r.deportista],
              ['Concepto', r.concepto],
              ['Monto', formatearCOP(r.monto)],
              ['Método', r.metodo],
              ['Fecha de pago', fechaLargaConAnio(r.fechaPago)],
              ...(r.referencia ? [['Referencia', r.referencia]] : []),
            ].map(([etiqueta, valor]) => (
              <div key={etiqueta} className="flex min-w-0 flex-col gap-0.5">
                <dt className="text-small text-secondary">{etiqueta}</dt>
                <dd className="break-words text-body">{valor}</dd>
              </div>
            ))}
          </dl>
          {r.cobroCreado && <p className="break-words text-small text-secondary">Ese mes no tenía cobro: se creó al registrar el pago.</p>}
          <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
            <Button onClick={registrarOtro}>Registrar otro pago</Button>
            <Button to={`${base}/pagos`} variant="secondary">Ver pagos</Button>
          </div>
        </Card>
      </Page>
    )
  }

  return (
    <Page title="Registrar pago" subtitle="Pago en efectivo o por transferencia" tituloRef={tituloRef}>
      <Card className="w-full max-w-2xl !p-2 sm:!p-4">
        <form ref={formRef} noValidate onSubmit={revisarYConfirmar} aria-busy={guardando} className="flex flex-col gap-4">
          <AvisoEstado aviso={aviso} rol="alert" tono="danger" enfocar={false} />

          <fieldset className="flex min-w-0 flex-col gap-3 rounded-lg border-0 p-0 sm:border sm:p-4">
            <legend className="break-words text-h3 sm:px-1">Deportista</legend>
            <Field
              label="Buscar deportista por nombre o documento"
              id="buscar"
              type="search"
              autoComplete="off"
              value={busqueda}
              error={errores.idDeportista}
              onChange={(e) => setBusqueda(e.target.value)}
              invalid={Boolean(errores.idDeportista)}
            />
            <p role="status" className="break-words text-small text-secondary">
              {!texto ? 'Escribe un nombre o un documento. Solo aparecen deportistas activos.' : buscandoLista ? 'Buscando…' : lista.error ? '' : resultados.length === 0 ? 'Sin resultados' : `${resultados.length} ${resultados.length === 1 ? 'resultado' : 'resultados'}`}
            </p>
            {texto && lista.error && !buscandoLista && (
              <p className="flex flex-col items-start gap-1 text-small text-danger sm:flex-row"><Icon name="alert" className="h-4 w-4 shrink-0 sm:mt-0.5" />No se pudo buscar. Intenta de nuevo.</p>
            )}
            {tarjetas.length > 0 && (
              <div role="radiogroup" aria-label="Resultados de la búsqueda" className="flex flex-col gap-2">
                {tarjetas.map((d) => {
                  const marcado = seleccionado?.id === d.id
                  return (
                    <label key={d.id} className={`flex min-h-11 cursor-pointer flex-col gap-1 rounded-md border p-2 focus-within:ring-2 focus-within:ring-focus focus-within:ring-offset-2 sm:flex-row sm:flex-wrap sm:items-center sm:gap-x-3 sm:p-3 ${marcado ? 'border-2 border-brand-primary bg-app' : 'bg-surface hover:bg-app'}`}>
                      <input type="radio" name="deportista" value={d.id} checked={marcado} onChange={() => elegir(d)} className="sr-only" />
                      <span className="min-w-0 sm:grow sm:basis-[10rem]">
                        <span className="block break-words text-body-medium">{d.nombre}</span>
                        <span className="block break-words text-small text-secondary">Documento: {d.documento}</span>
                      </span>
                      {marcado && (
                        <span className="flex items-center gap-1 text-small font-semibold text-success">
                          <Icon name="check" className="h-4 w-4 shrink-0" />
                          Seleccionado
                        </span>
                      )}
                    </label>
                  )
                })}
              </div>
            )}
            {resultados.length > MAX_RESULTADOS && <p className="break-words text-small text-secondary">Se muestran {MAX_RESULTADOS} de {resultados.length}. Escribe más para acotar la búsqueda.</p>}
          </fieldset>

          <Field label="Mes que se paga" id="mes" as="select" value={valores.mes} error={errores.mes} onChange={(e) => cambiar('mes', e.target.value)}>
            {meses.map((m) => <option key={m.valor} value={m.valor}>Mensualidad {m.texto}</option>)}
          </Field>

          <div role="status" className="flex flex-col gap-2">
            {seleccionado && consultandoCobro && (
              <p className="flex flex-col items-start gap-1 text-small text-secondary sm:flex-row sm:items-center sm:gap-2"><Icon name="clock" className="h-4 w-4 shrink-0" />Consultando el cobro…</p>
            )}
            {yaPagado && (
              <p id="aviso-pagado" className="flex flex-col items-start gap-1 rounded-md bg-danger p-2 text-body-medium text-danger sm:flex-row sm:gap-2 sm:p-3">
                <Icon name="alert" className="h-5 w-5 shrink-0 sm:mt-0.5" />
                <span className="min-w-0 break-words">
                  {datosCobro.concepto} ya está pagada para este deportista (pagada el {fechaLargaConAnio(datosCobro.fechaPago)}). No se puede registrar otro pago.
                </span>
              </p>
            )}
            {montoBloqueado && (
              <p className="flex flex-col items-start gap-1 rounded-md bg-warning p-2 text-body-medium text-warning sm:flex-row sm:gap-2 sm:p-3">
                <Icon name="info" className="h-5 w-5 shrink-0 sm:mt-0.5" />
                <span className="min-w-0 break-words">Cobro existente: {datosCobro.concepto} · {formatearCOP(datosCobro.monto)} ({datosCobro.estado === 'vencido' ? 'vencido' : 'pendiente'}). El monto viene del cobro y no se puede cambiar.</span>
              </p>
            )}
            {datosCobro && !datosCobro.existe && (
              <p className="flex flex-col items-start gap-1 text-small text-secondary sm:flex-row sm:gap-2">
                <Icon name="info" className="h-4 w-4 shrink-0 sm:mt-0.5" />
                <span className="min-w-0 break-words">Ese mes todavía no tiene cobro: se creará al registrar el pago. Escribe el monto.</span>
              </p>
            )}
          </div>

          <div className="flex flex-col gap-1">
            <Field
              label="Monto (pesos colombianos)"
              id="monto"
              inputMode="numeric"
              autoComplete="off"
              value={montoMostrado}
              error={errores.monto}
              readOnly={montoBloqueado}
              describedBy="monto-ayuda"
              onChange={(e) => cambiar('monto', e.target.value)}
              {...(montoBloqueado ? { className: 'min-h-11 w-full rounded-md border bg-app px-3 text-body text-secondary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus' } : {})}
            />
            <p id="monto-ayuda" className="break-words text-small text-secondary">{ayudaMonto}</p>
          </div>

          <Field label="Fecha de pago" id="fechaPago" type="date" max={HOY_EJEMPLO} value={valores.fechaPago} error={errores.fechaPago} onChange={(e) => cambiar('fechaPago', e.target.value)} />

          <fieldset aria-describedby={errores.metodo ? 'metodo-error' : undefined} className="flex min-w-0 flex-col gap-2">
            <legend className="mb-1 break-words text-body-medium">Método de pago</legend>
            <div className="grid grid-cols-[repeat(auto-fit,minmax(min(100%,9rem),1fr))] gap-2">
              {METODOS_PAGO_MANUAL.map((m) => {
                const marcado = valores.metodo === m
                return (
                  <label key={m} className={`${claseChip(marcado)} ${errores.metodo && !marcado ? 'border-danger' : ''}`}>
                    <input
                      type="radio"
                      name="metodo"
                      value={m}
                      checked={marcado}
                      onChange={() => cambiar('metodo', m)}
                      aria-invalid={errores.metodo ? true : undefined}
                      aria-describedby={errores.metodo ? 'metodo-error' : undefined}
                      className="sr-only"
                    />
                    {marcado && <Icon name="check" className="h-4 w-4 shrink-0" />}
                    <span className="min-w-0 break-words">{m}</span>
                  </label>
                )
              })}
            </div>
            {errores.metodo && (
              <p id="metodo-error" className="flex items-start gap-1 text-small text-danger">
                <Icon name="alert" className="mt-0.5 h-4 w-4 shrink-0" />
                <span className="min-w-0 break-words">{errores.metodo}</span>
              </p>
            )}
          </fieldset>

          <div className="flex flex-col gap-1">
            <Field
              label={`Referencia de la transferencia${valores.metodo === 'Transferencia' ? '' : ' (opcional)'}`}
              id="referencia"
              autoComplete="off"
              maxLength={MAX_REFERENCIA}
              value={valores.referencia}
              error={errores.referencia}
              describedBy="referencia-contador"
              onChange={(e) => cambiar('referencia', e.target.value)}
            />
            <p id="referencia-contador" className="text-small text-secondary">{valores.referencia.length} de {MAX_REFERENCIA} caracteres</p>
          </div>

          <div className="flex flex-col gap-1">
            <Field
              label="Nota interna (opcional)"
              id="nota"
              as="textarea"
              rows={3}
              maxLength={MAX_NOTA}
              value={valores.nota}
              error={errores.nota}
              describedBy="nota-contador"
              onChange={(e) => cambiar('nota', e.target.value)}
              className="min-h-11 w-full rounded-md border bg-surface px-3 py-2 text-body focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
            />
            <p id="nota-contador" className="break-words text-small text-secondary">{valores.nota.length} de {MAX_NOTA} caracteres. Solo la ve el club: el acudiente no la ve.</p>
          </div>

          <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
            <Button type="submit" disabled={guardando || yaPagado} aria-busy={guardando} aria-describedby={yaPagado ? 'aviso-pagado' : undefined} className="disabled:opacity-60">
              {guardando ? 'Registrando…' : 'Registrar pago'}
            </Button>
            <Button variant="secondary" onClick={cancelar} disabled={guardando}>Cancelar</Button>
          </div>

          <DialogoConfirmacion
            open={confirmar}
            titulo="¿Registrar este pago?"
            descripcion={`¿Registrar el pago de ${montoNumero !== null ? formatearCOP(montoNumero) : montoMostrado} de ${conceptoMes} para ${seleccionado?.nombre ?? ''}? Por ahora un pago registrado no se puede anular desde la aplicación.`}
            textoConfirmar="Sí, registrar pago"
            onConfirmar={registrar}
            onCancelar={cerrarConfirmacion}
          />
          <DialogoConfirmacion
            open={confirmarSalir}
            titulo="¿Salir sin registrar el pago?"
            descripcion="Los datos que escribiste no se guardarán."
            textoConfirmar="Sí, salir"
            onConfirmar={() => {
              setConfirmarSalir(false)
              navigate(`${base}/pagos`)
            }}
            onCancelar={cerrarSalir}
          />
        </form>
      </Card>
    </Page>
  )
}
