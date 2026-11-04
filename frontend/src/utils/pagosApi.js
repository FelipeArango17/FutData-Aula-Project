// Funciones PURAS para hablar con la API de pagos (backend: "Pagos con Wompi" en backend/README.md): adaptan las respuestas a la
// forma que usan las pantallas, arman la URL del checkout y convierten errores en mensajes. Sin red ni estado.

export const URL_CHECKOUT_WOMPI = 'https://checkout.wompi.co/p/'

// Los medios que guarda el backend (pago.metodo) con el texto que se muestra
const ETIQUETAS_METODO = {
  EFECTIVO: 'Efectivo',
  TRANSFERENCIA: 'Transferencia',
  PSE: 'PSE',
  NEQUI: 'Nequi',
  TARJETA: 'Tarjeta',
  QR: 'QR Bancolombia',
  BOTON_BANCOLOMBIA: 'Botón Bancolombia',
  WOMPI_OTRO: 'Wompi (otro medio)',
}
export const etiquetaMetodo = (metodo) => (metodo ? (ETIQUETAS_METODO[metodo] ?? metodo) : null)

export const TIPOS_COBRO = ['MENSUALIDAD', 'INSCRIPCION', 'TORNEO', 'UNIFORME']
export const ETIQUETAS_TIPO = { MENSUALIDAD: 'Mensualidad', INSCRIPCION: 'Inscripción', TORNEO: 'Torneo', UNIFORME: 'Uniforme' }
export const ESTADOS_COBRO = ['PENDIENTE', 'APROBADO', 'RECHAZADO']
export const METODOS_MANUALES = ['EFECTIVO', 'TRANSFERENCIA']

// Estado que ve la persona: pagado | pendiente | vencido | rechazado. "Vencido" no se guarda: viene de la vista del backend
// (PENDIENTE con vencimiento anterior a hoy).
export function estadoParaPantalla({ estado, vencido }) {
  if (estado === 'APROBADO') return 'pagado'
  if (estado === 'RECHAZADO') return 'rechazado'
  return vencido ? 'vencido' : 'pendiente'
}

// Arma la URL del Web Checkout de Wompi con lo que devuelve POST /api/pagos/{id}/iniciar-wompi. Los valores se codifican, salvo
// el nombre "signature:integrity", que lleva ':' sin codificar. La redirección es opcional y solo informa: el pago lo confirma el webhook.
export function construirUrlCheckout({ llavePublica, moneda, montoEnCentavos, referencia, firmaIntegridad, urlRedireccion }) {
  const codificar = encodeURIComponent
  const partes = [
    `public-key=${codificar(llavePublica)}`,
    `currency=${codificar(moneda)}`,
    `amount-in-cents=${codificar(montoEnCentavos)}`,
    `reference=${codificar(referencia)}`,
    `signature:integrity=${codificar(firmaIntegridad)}`,
  ]
  if (urlRedireccion) partes.push(`redirect-url=${codificar(urlRedireccion)}`)
  return `${URL_CHECKOUT_WOMPI}?${partes.join('&')}`
}

// MiPagoResponseDTO -> pago del estado de cuenta (la forma que ya usa ListaPagos). Los ids son UUID (texto).
export function adaptarMiPago(p) {
  return {
    id: p.id,
    idDeportista: p.idDeportista,
    tipo: p.tipo,
    concepto: p.concepto,
    monto: p.monto,
    fechaVencimiento: p.fechaVencimiento,
    fechaPago: p.fechaPago ?? null,
    metodo: etiquetaMetodo(p.metodo),
    estado: estadoParaPantalla(p),
    tieneComprobante: Boolean(p.tieneComprobante),
  }
}

const suma = (lista) => lista.reduce((total, p) => total + p.monto, 0)
// Un cobro rechazado no se suma a lo pendiente: al reintentarlo el backend crea otro cobro PENDIENTE igual, y se contaría dos veces.
const sinPagar = (p) => p.estado === 'pendiente' || p.estado === 'vencido'

// GET /api/pagos/mis-pagos -> { deportistas: [{ deportista, totalPendiente, totalVencido, pendienteDesde, pagos }], totalPendiente, totalVencido }
export function adaptarMisPagos(lista) {
  const porDeportista = new Map()
  for (const dto of lista ?? []) {
    if (!porDeportista.has(dto.idDeportista)) porDeportista.set(dto.idDeportista, { nombre: dto.nombreDeportista ?? '', pagos: [] })
    porDeportista.get(dto.idDeportista).pagos.push(adaptarMiPago(dto))
  }
  const deportistas = [...porDeportista].map(([id, { nombre, pagos }]) => {
    const ordenados = [...pagos].sort((a, b) => b.fechaVencimiento.localeCompare(a.fechaVencimiento) || String(b.id).localeCompare(String(a.id)))
    const pendientes = ordenados.filter(sinPagar)
    return {
      deportista: { id, nombre, apellido: '' }, // el backend manda el nombre completo en un solo texto
      totalPendiente: suma(pendientes),
      totalVencido: suma(ordenados.filter((p) => p.estado === 'vencido')),
      pendienteDesde: pendientes.length ? pendientes.map((p) => p.fechaVencimiento).sort()[0] : null,
      pagos: ordenados,
    }
  })
  return {
    deportistas,
    totalPendiente: deportistas.reduce((t, d) => t + d.totalPendiente, 0),
    totalVencido: deportistas.reduce((t, d) => t + d.totalVencido, 0),
  }
}

// PagoResponseDTO (administrador y tesorero) -> cobro de la lista de gestión
export function adaptarCobro(p) {
  return {
    id: p.id,
    idDeportista: p.idDeportista,
    nombreDeportista: p.nombreDeportista ?? '',
    tipo: p.tipo,
    concepto: p.concepto,
    monto: p.monto,
    periodo: p.periodo ?? null,
    fechaVencimiento: p.fechaVencimiento,
    fechaPago: p.fechaPago ?? null,
    metodo: etiquetaMetodo(p.metodo),
    estadoApi: p.estado,
    estado: estadoParaPantalla(p),
    numeroComprobante: p.numeroComprobante ?? null,
    tieneComprobante: p.estado === 'APROBADO' && Boolean(p.numeroComprobante),
    iniciadoEnWompi: Boolean(p.referenciaWompi), // con referencia, un pago en línea puede llegar: no se aprueba a mano
    notaInterna: p.notaInterna ?? null,
  }
}

// Qué acciones tiene un cobro de la lista de gestión (tesorero y administrador), según su estado:
//   manual    registrar pago manual (PENDIENTE, o RECHAZADO que nunca se inició en Wompi)
//   rechazar  solo PENDIENTE
//   conciliar solo si el cobro ya se inició en Wompi y no está aprobado (el webhook pudo no llegar)
//   comprobante solo APROBADO
export function accionesDeCobro(cobro) {
  const abierto = cobro.estadoApi === 'PENDIENTE'
  return {
    manual: abierto || (cobro.estadoApi === 'RECHAZADO' && !cobro.iniciadoEnWompi),
    rechazar: abierto,
    conciliar: cobro.iniciadoEnWompi && cobro.estadoApi !== 'APROBADO',
    comprobante: cobro.tieneComprobante,
  }
}

// Convierte un error de apiClient ({estado, mensaje, campos, codigo}) en el texto que ve la persona. Los errores de los servicios de
// ejemplo (sin estado HTTP) usan su propio mensaje. `mensaje` del 409 y del 400 viene del backend en español ("campo: texto").
export const MENSAJE_PAGOS_NO_CONFIGURADOS = 'Los pagos en línea no están disponibles por ahora.'
export function mensajeDeErrorPagos(error, porDefecto = 'No se pudo completar la operación. Intenta de nuevo.') {
  const estado = error?.estado
  const quitarCampo = (texto) => String(texto ?? '').replace(/^[A-Za-z]+: /, '')
  const conMayuscula = (texto) => texto.charAt(0).toUpperCase() + texto.slice(1)
  if (error?.codigo === 'PAGOS_NO_CONFIGURADOS') return MENSAJE_PAGOS_NO_CONFIGURADOS
  if (estado === 0 || error?.codigo === 'RED') return 'No pudimos conectar con el servidor. Intenta de nuevo.'
  if (estado === 401) return 'Tu sesión venció. Inicia sesión de nuevo.'
  if (estado === 403) return 'No tienes permiso para hacer esta acción.'
  if (estado === 404) return 'No encontramos ese pago. Puede que ya no exista o que no sea de tus deportistas.'
  if (estado === 409) return conMayuscula(quitarCampo(error.mensaje)) || 'La operación choca con el estado actual del pago.'
  if (estado === 503) return 'El servicio de pagos no está disponible en este momento. Intenta de nuevo más tarde.'
  if (estado === 400) return conMayuscula(quitarCampo(error.mensaje)) || porDefecto
  return error?.message && !estado ? error.message : porDefecto
}

// ¿Hay que seguir consultando tras volver del checkout? El webhook confirma de forma asíncrona: se reconsulta cada 3 s hasta 30 s
// mientras el cobro siga pendiente (o vencido).
export const INTERVALO_SONDEO_MS = 3000
export const LIMITE_SONDEO_MS = 30000
export function evaluarSondeo(pago, transcurridoMs) {
  if (!pago) return 'terminado' // el cobro ya no aparece: nada que esperar
  if (pago.estado === 'pagado') return 'aprobado'
  if (pago.estado === 'rechazado') return 'rechazado'
  return transcurridoMs >= LIMITE_SONDEO_MS ? 'agotado' : 'esperando'
}

export function buscarPago(estadoCuenta, idPago) {
  for (const d of estadoCuenta?.deportistas ?? []) {
    const pago = d.pagos.find((p) => p.id === idPago)
    if (pago) return pago
  }
  return null
}

// Filtros del estado de cuenta del acudiente. "Pendientes y vencidos" incluye los rechazados: son cobros que todavía no se pagan.
export const FILTROS_ESTADO_CUENTA = ['Todos', 'Pendientes y vencidos', 'Pagados']
export const cumpleFiltro = (filtro, pago) => filtro === 'Todos' || (filtro === 'Pagados' ? pago.estado === 'pagado' : pago.estado !== 'pagado')

// ---- Validación de los formularios de gestión (el backend vuelve a validar todo) ----
const FECHA = /^\d{4}-\d{2}-\d{2}$/
const MES = /^\d{4}-(0[1-9]|1[0-2])$/
export const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
export const MAX_MONTO = 999_999_999

// Registrar pago manual: fecha (no futura) y método (EFECTIVO o TRANSFERENCIA). Devuelve { campo: mensaje }.
export function validarAprobacionManual({ fechaPago, metodo }, hoy) {
  const errores = {}
  if (!FECHA.test(String(fechaPago ?? ''))) errores.fechaPago = 'Indica la fecha del pago.'
  else if (fechaPago > hoy) errores.fechaPago = 'La fecha del pago no puede ser posterior a hoy.'
  if (!METODOS_MANUALES.includes(metodo)) errores.metodo = 'Elige efectivo o transferencia.'
  return errores
}

// Crear un cobro (administrador). El id del deportista es un UUID: con deportistas de ejemplo (ids numéricos) no se puede crear.
export function validarNuevoCobro({ idDeportista, tipo, concepto, monto, fechaVencimiento, periodo }) {
  const errores = {}
  if (!UUID.test(String(idDeportista ?? ''))) errores.idDeportista = 'Elige un deportista de la API (el módulo de deportistas debe estar conectado).'
  if (!TIPOS_COBRO.includes(tipo)) errores.tipo = 'Elige el tipo de cobro.'
  const texto = String(concepto ?? '').trim()
  if (!texto && tipo !== 'MENSUALIDAD') errores.concepto = 'Escribe el concepto.'
  else if (texto.length > 250) errores.concepto = 'El concepto debe tener máximo 250 caracteres.'
  const valor = String(monto ?? '').trim()
  if (!/^\d+$/.test(valor) || Number(valor) < 1 || Number(valor) > MAX_MONTO) errores.monto = 'Escribe el monto en pesos, solo números enteros y mayor que 0.'
  if (!FECHA.test(String(fechaVencimiento ?? ''))) errores.fechaVencimiento = 'Indica la fecha de vencimiento.'
  if (tipo === 'MENSUALIDAD' && !MES.test(String(periodo ?? ''))) errores.periodo = 'Elige el mes de la mensualidad.'
  return errores
}

export const validarPeriodo = (periodo) => (MES.test(String(periodo ?? '')) ? null : 'Elige el mes.')

// El periodo (mes) solo existe en las mensualidades: con otro tipo de cobro el filtro no aplica.
export const tipoUsaPeriodo = (tipo) => !tipo || tipo === 'MENSUALIDAD'
export const NOTA_PERIODO_SOLO_MENSUALIDAD = 'El periodo aplica solo a mensualidades'

export const CONFIRMACION_GENERAR_CICLOS = {
  titulo: 'Generar cobros del ciclo',
  descripcion: 'Crea los cobros que le tocan a cada jugador según su fecha de ingreso, hasta 5 días antes de su fecha de cobro. Los que ya existen no se repiten. Esto también se hace solo cada día a las 6:00 a. m. ¿Continuar?',
  boton: 'Generar',
}
export const MENSAJE_MONTO_NO_CONFIGURADO = 'No se pudieron generar los cobros porque el valor de la mensualidad no está configurado. Pide que lo configuren y vuelve a intentarlo.'
const plural = (n, uno, varios) => `${n} ${n === 1 ? uno : varios}`

// Resumen en lenguaje claro de POST /pagos/generar-ciclos
export function resumenGeneracionCiclos(r) {
  const { creadas = 0, yaExistentes = 0, avisosEnviados = 0, avisosFallidos = 0, deportistasConError = 0 } = r ?? {}
  const frases = [
    creadas === 0 ? 'No hizo falta crear cobros nuevos.' : `Se ${creadas === 1 ? 'creó' : 'crearon'} ${plural(creadas, 'cobro nuevo', 'cobros nuevos')}.`,
  ]
  if (yaExistentes > 0) frases.push(`Ya ${yaExistentes === 1 ? 'existía' : 'existían'} ${plural(yaExistentes, 'cobro', 'cobros')}; no se duplicaron.`)
  if (avisosEnviados > 0) frases.push(`Se ${avisosEnviados === 1 ? 'envió' : 'enviaron'} ${plural(avisosEnviados, 'aviso', 'avisos')} por correo.`)
  // Un cobro que ya existe se omite al volver a generar: su aviso NO se reenvía.
  if (avisosFallidos > 0) frases.push(`Los cobros quedaron creados, pero ${plural(avisosFallidos, 'aviso por correo no se pudo enviar y no se reintenta solo', 'avisos por correo no se pudieron enviar y no se reintentan solos')}.`)
  if (deportistasConError > 0) frases.push(`${plural(deportistasConError, 'deportista no se pudo procesar', 'deportistas no se pudieron procesar')}. Vuelve a generar los cobros para completarlos.`)
  return { texto: frases.join(' '), conProblemas: avisosFallidos > 0 || deportistasConError > 0 }
}
