// Servicio de pagos y estado de cuenta (HU-017, tareas 326 y 329). Por ahora trabaja sobre datos de ejemplo EN MEMORIA.
// Las funciones son async y devuelven lo que devolvería una API REST, para cambiarlas luego por
// Axios (GET /pagos/estado-cuenta) sin tocar las pantallas. Los deportistas salen de deportistasService.
//
// Forma de datos propuesta para el frontend:
//   actor = { rol: 'acudiente', correo }
//   Pago  = { id, idDeportista, concepto, monto, fechaVencimiento, fechaPago | null, metodo | null }
//           (los pagos registrados a mano también guardan referencia, nota, registradoPor y registradoEn; son internos del club)
//     monto             pesos colombianos (COP), entero, SIN decimales
//     fechaVencimiento  "AAAA-MM-DD"
//     fechaPago         "AAAA-MM-DD" o null si no se ha pagado
//   El estado NO se guarda: se deriva de las fechas (ver estadoDelPago).
//
// Errores: Error con `codigo` -> 'SIN_PERMISO' | 'ERROR_DE_RED' | 'VALIDACION' (con `errores`) | 'DEPORTISTA_NO_EXISTE' |
//   'DEPORTISTA_INACTIVO' | 'YA_PAGADO' | 'MONTO_DISTINTO' (con `esperado`).
//
// PENDIENTE: confirmar con Juan / el modelo de datos (docs/info-sprints/sprint-1/diccionario-datos.md, sección 2.5 Pago):
// - La tabla Pago del diccionario aún no cierra estos campos (concepto, fecha de vencimiento, método, estado derivado).
// - "Hoy" es HOY_EJEMPLO. "Desde cuándo" = vencimiento más antiguo entre los pagos sin pagar del deportista.
// - Un acudiente solo ve a SUS deportistas (los que tienen su correo en `acudiente`). Aquí se simula; en producción
//   lo valida el backend con el JWT.
// - El pago en línea (Wompi) NO está en esta tarea.
// - HU-016 (registro manual): solo administrador y tesorero. El cobro de la mensualidad de un mes que todavía no existe se CREA al
//   registrar el pago (PENDIENTE con Juan: el valor de la mensualidad debería salir de una tarifa). No hay abonos: el monto debe
//   ser igual al del cobro (PENDIENTE). No hay anulación ni corrección de pagos (PENDIENTE); por eso la pantalla pide confirmar.
//   El estado de cuenta del acudiente NO muestra quién registró el pago ni la nota interna.
// - La descarga es un CSV provisional generado en el navegador; la versión final será un PDF generado por el
//   backend (GET /pagos/estado-cuenta?formato=pdf).
import { HOY_EJEMPLO } from './entrenamientosService.js'
import { listarDeportistas, obtenerDeportista } from './deportistasService.js'
import { MENSAJES, enLista, maximoCaracteres, requerido, requeridoSi, validarCampos, validarFechaPago, validarMes, validarMonto } from '../utils/validaciones.js'
import { mesEnTexto } from '../utils/fechas.js'

// Datos de demostración, ficticios. Santiago (1) y Valeria (2) son del acudiente demo; Mateo (6) y Sofía (9) NO,
// y sirven para comprobar que sus pagos no se filtran.
let pagos = [
  { id: 1, idDeportista: 1, concepto: 'Inscripción 2026', monto: 150000, fechaVencimiento: '2026-01-15', fechaPago: '2026-01-12', metodo: 'Transferencia' },
  { id: 2, idDeportista: 1, concepto: 'Mensualidad agosto', monto: 90000, fechaVencimiento: '2026-08-31', fechaPago: '2026-08-28', metodo: 'PSE' },
  { id: 3, idDeportista: 1, concepto: 'Mensualidad septiembre', monto: 90000, fechaVencimiento: '2026-09-30', fechaPago: '2026-09-15', metodo: 'Nequi' },
  { id: 4, idDeportista: 1, concepto: 'Uniforme (2.º pago)', monto: 90000, fechaVencimiento: '2026-10-10', fechaPago: null, metodo: null },
  { id: 5, idDeportista: 1, concepto: 'Mensualidad octubre', monto: 90000, fechaVencimiento: '2026-10-30', fechaPago: null, metodo: null },
  { id: 6, idDeportista: 2, concepto: 'Inscripción 2026', monto: 150000, fechaVencimiento: '2026-01-15', fechaPago: '2026-01-14', metodo: 'Transferencia' },
  { id: 7, idDeportista: 2, concepto: 'Mensualidad septiembre', monto: 90000, fechaVencimiento: '2026-09-30', fechaPago: '2026-09-20', metodo: 'Efectivo' },
  { id: 8, idDeportista: 2, concepto: 'Mensualidad octubre', monto: 90000, fechaVencimiento: '2026-10-30', fechaPago: '2026-10-05', metodo: 'PSE' },
  { id: 9, idDeportista: 6, concepto: 'Mensualidad octubre', monto: 95000, fechaVencimiento: '2026-10-05', fechaPago: null, metodo: null },
  { id: 10, idDeportista: 9, concepto: 'Mensualidad octubre', monto: 90000, fechaVencimiento: '2026-10-30', fechaPago: null, metodo: null },
]

// Simulación de red y de reloj: se pueden cambiar (pruebas y verificación manual de errores).
let retardoMs = 250
let fallosPendientes = 0
let hoy = HOY_EJEMPLO
export function configurarSimulacion({ retardo, fallos, hoy: nuevoHoy } = {}) {
  if (retardo !== undefined) retardoMs = retardo
  if (fallos !== undefined) fallosPendientes = fallos
  if (nuevoHoy !== undefined) hoy = nuevoHoy ?? HOY_EJEMPLO
}

const error = (codigo, mensaje) => Object.assign(new Error(mensaje), { codigo })

async function simularRed() {
  await new Promise((resolve) => setTimeout(resolve, retardoMs))
  if (fallosPendientes > 0) {
    fallosPendientes -= 1
    throw error('ERROR_DE_RED', 'No se pudo conectar con el servidor.')
  }
}

// "pagado" si tiene fecha de pago; si no, "vencido" cuando el vencimiento es anterior a hoy y "pendiente" en otro caso
// (vence hoy = todavía pendiente).
export function estadoDelPago(pago, fechaHoy = HOY_EJEMPLO) {
  if (pago.fechaPago) return 'pagado'
  return pago.fechaVencimiento < fechaHoy ? 'vencido' : 'pendiente'
}

const suma = (lista) => lista.reduce((total, p) => total + p.monto, 0)

// Estado de cuenta de TODOS los deportistas del acudiente. Cada deportista trae sus pagos del más reciente al más antiguo.
export async function obtenerEstadoCuenta(actor) {
  await simularRed()
  if (actor?.rol !== 'acudiente' || !actor.correo) throw error('SIN_PERMISO', 'No tienes permiso para ver este estado de cuenta.')
  const correo = actor.correo.trim().toLowerCase()
  const propios = (await listarDeportistas()).filter((d) => d.acudiente && d.acudiente.toLowerCase() === correo)

  const deportistas = propios.map((d) => {
    const lista = pagos
      .filter((p) => p.idDeportista === d.id)
      // Solo los campos que ve el acudiente: nunca quién registró el pago, la referencia ni la nota interna
      .map((p) => ({
        id: p.id, idDeportista: p.idDeportista, concepto: p.concepto, monto: p.monto, fechaVencimiento: p.fechaVencimiento,
        fechaPago: p.fechaPago, metodo: p.metodo, estado: estadoDelPago(p, hoy),
      }))
      .sort((a, b) => b.fechaVencimiento.localeCompare(a.fechaVencimiento) || b.id - a.id)
    const sinPagar = lista.filter((p) => p.estado !== 'pagado')
    return {
      deportista: { id: d.id, nombre: d.nombre, apellido: d.apellido },
      totalPendiente: suma(sinPagar),
      totalVencido: suma(lista.filter((p) => p.estado === 'vencido')),
      pendienteDesde: sinPagar.length ? sinPagar.map((p) => p.fechaVencimiento).sort()[0] : null,
      pagos: lista,
    }
  })

  return {
    deportistas,
    totalPendiente: deportistas.reduce((t, d) => t + d.totalPendiente, 0),
    totalVencido: deportistas.reduce((t, d) => t + d.totalVencido, 0),
  }
}

const COLUMNAS = ['Deportista', 'Concepto', 'Monto (COP)', 'Vencimiento', 'Fecha de pago', 'Estado']
const ETIQUETA_ESTADO = { pagado: 'Pagado', pendiente: 'Pendiente', vencido: 'Vencido' }
// Separador ';': es el que usa Excel con configuración regional de Colombia (la coma es el separador decimal).
const SEPARADOR = ';'

// Celdas que empiezan por = + - @ (o tabulación / retorno) se leerían como fórmula en Excel: llevan un apóstrofo previo.
const neutralizar = (texto) => (/^[=+\-@\t\r]/.test(texto) ? `'${texto}` : texto)
const celda = (texto) => {
  const t = neutralizar(String(texto ?? ''))
  return /[";\r\n]/.test(t) ? `"${t.replace(/"/g, '""')}"` : t
}

// Función pura: devuelve el texto CSV (líneas separadas por CRLF). El BOM UTF-8 se agrega al descargar
// (utils/descargas.js) para que Excel abra bien las tildes. Los montos van como enteros, sin formato.
export function generarCsvEstadoCuenta(estado) {
  const filas = estado.deportistas.flatMap(({ deportista, pagos: lista }) =>
    lista.map((p) => [
      `${deportista.nombre} ${deportista.apellido}`,
      p.concepto,
      String(p.monto),
      p.fechaVencimiento,
      p.fechaPago ?? '',
      ETIQUETA_ESTADO[p.estado],
    ]),
  )
  return [COLUMNAS, ...filas].map((fila) => fila.map(celda).join(SEPARADOR)).join('\r\n') + '\r\n'
}

// Simula la llamada al backend que prepara la descarga (hoy un CSV; luego GET /pagos/estado-cuenta?formato=pdf).
// Vuelve a pedir el estado completo: la descarga incluye todos los deportistas y pagos, no solo el filtro de la pantalla.
export async function prepararDescargaEstadoCuenta(actor) {
  const estado = await obtenerEstadoCuenta(actor)
  return { nombreArchivo: `estado-de-cuenta-${hoy}.csv`, tipo: 'text/csv;charset=utf-8', contenido: generarCsvEstadoCuenta(estado) }
}

// ---- Registro manual de pagos (HU-016): solo administrador y tesorero ----
export const METODOS_PAGO_MANUAL = ['Efectivo', 'Transferencia']
export const MAX_REFERENCIA = 60
export const MAX_NOTA = 200

// Los últimos meses y el actual, calculados con `fechaHoy` (HOY_EJEMPLO): [{ valor: '2026-10', texto: 'octubre 2026' }, ...] del más reciente al más antiguo
export function mesesParaPago(fechaHoy = HOY_EJEMPLO, cantidad = 6) {
  const [anio, mes] = fechaHoy.split('-').map(Number)
  return Array.from({ length: cantidad }, (_, i) => {
    const d = new Date(Date.UTC(anio, mes - 1 - i, 1))
    const valor = `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`
    return { valor, texto: `${mesEnTexto(`${valor}-01`)} ${d.getUTCFullYear()}` }
  })
}

// Reglas del formulario y del servicio (los mismos validadores compartidos). Devuelve { campo: mensaje } solo con los campos con error.
// datos: { idDeportista, mes ("AAAA-MM"), monto, fechaPago ("AAAA-MM-DD"), metodo, referencia, nota }
export function validarDatosPagoManual(datos, fechaHoy = HOY_EJEMPLO) {
  return validarCampos(datos, {
    idDeportista: [requerido(MENSAJES.pagoDeportista)],
    mes: [(v) => validarMes(v, fechaHoy)],
    monto: [validarMonto],
    fechaPago: [(v) => validarFechaPago(v, fechaHoy)],
    metodo: [enLista(METODOS_PAGO_MANUAL, MENSAJES.pagoMetodo)],
    referencia: [requeridoSi((todos) => todos.metodo === 'Transferencia', MENSAJES.pagoReferenciaObligatoria), maximoCaracteres(MAX_REFERENCIA, MENSAJES.pagoReferenciaLarga)],
    nota: [maximoCaracteres(MAX_NOTA, MENSAJES.pagoNotaLarga)],
  })
}

function exigirRegistrador(actor) {
  if (actor?.rol !== 'administrador' && actor?.rol !== 'tesorero') throw error('SIN_PERMISO', 'No tienes permiso para registrar pagos.')
}

// El cobro de la mensualidad de un mes: concepto "Mensualidad ..." con vencimiento dentro de ese mes
const buscarMensualidad = (idDeportista, mes) =>
  pagos.find((p) => p.idDeportista === idDeportista && p.concepto.startsWith('Mensualidad') && p.fechaVencimiento.startsWith(mes))

// Consulta el cobro de la mensualidad de un deportista y un mes (para prellenar el monto y avisar si ya está pagada).
// Devuelve { existe: false } o { existe: true, concepto, monto, estado, fechaVencimiento, fechaPago }.
export async function consultarCobroMensualidad(idDeportista, mes, actor) {
  await simularRed()
  exigirRegistrador(actor)
  const cobro = buscarMensualidad(Number(idDeportista), mes)
  if (!cobro) return { existe: false }
  return { existe: true, concepto: cobro.concepto, monto: cobro.monto, estado: estadoDelPago(cobro, hoy), fechaVencimiento: cobro.fechaVencimiento, fechaPago: cobro.fechaPago }
}

// Registra el pago de la mensualidad de un mes. Orden de las comprobaciones: permiso, datos, deportista (existe y está activo),
// mensualidad ya pagada (YA_PAGADO) y monto igual al del cobro (MONTO_DISTINTO). Si el cobro de ese mes no existe, se CREA ya pagado.
// Si algo falla no se guarda nada. Nunca se borra ni se sobrescribe un pago anterior. Devuelve { pago, cobroCreado }.
export async function registrarPagoManual(datos, actor) {
  await simularRed()
  exigirRegistrador(actor)
  const errores = validarDatosPagoManual(datos, hoy)
  if (Object.keys(errores).length > 0) throw Object.assign(error('VALIDACION', 'Hay datos que corregir.'), { errores })

  const deportista = await obtenerDeportista(datos.idDeportista) // lanza DEPORTISTA_NO_EXISTE
  if (!deportista.activo) throw error('DEPORTISTA_INACTIVO', 'El deportista está inactivo.')

  const mes = String(datos.mes).trim()
  const monto = Number(String(datos.monto).trim())
  const cobro = buscarMensualidad(deportista.id, mes)
  if (cobro?.fechaPago) throw error('YA_PAGADO', `La mensualidad de ${mesEnTexto(`${mes}-01`)} ya está pagada para este deportista.`)
  if (cobro && cobro.monto !== monto) throw Object.assign(error('MONTO_DISTINTO', 'El monto debe ser igual al del cobro.'), { esperado: cobro.monto })

  const registro = {
    fechaPago: String(datos.fechaPago).trim(),
    metodo: datos.metodo,
    referencia: String(datos.referencia ?? '').trim(),
    nota: String(datos.nota ?? '').trim(),
    registradoPor: actor.correo ?? null,
    registradoEn: new Date().toISOString(),
  }
  let resultado
  if (cobro) {
    resultado = { ...cobro, ...registro }
    pagos = pagos.map((p) => (p.id === cobro.id ? resultado : p))
  } else {
    // Vence el último día del mes, con tope en el 30 (como las mensualidades de la demo)
    const [anio, numeroMes] = mes.split('-').map(Number)
    const dia = Math.min(new Date(Date.UTC(anio, numeroMes, 0)).getUTCDate(), 30)
    resultado = {
      id: Math.max(...pagos.map((p) => p.id)) + 1,
      idDeportista: deportista.id,
      concepto: `Mensualidad ${mesEnTexto(`${mes}-01`)}`,
      monto,
      fechaVencimiento: `${mes}-${String(dia).padStart(2, '0')}`,
      ...registro,
    }
    pagos = [...pagos, resultado]
  }
  return { pago: { ...resultado, estado: estadoDelPago(resultado, hoy) }, cobroCreado: !cobro }
}
