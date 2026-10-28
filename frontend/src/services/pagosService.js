// Servicio de pagos y estado de cuenta (HU-017, tareas 326 y 329). Por ahora trabaja sobre datos de ejemplo EN MEMORIA.
// Las funciones son async y devuelven lo que devolvería una API REST, para cambiarlas luego por
// Axios (GET /pagos/estado-cuenta) sin tocar las pantallas. Los deportistas salen de deportistasService.
//
// Forma de datos propuesta para el frontend:
//   actor = { rol: 'acudiente', correo }
//   Pago  = { id, idDeportista, concepto, monto, fechaVencimiento, fechaPago | null, metodo | null }
//     monto             pesos colombianos (COP), entero, SIN decimales
//     fechaVencimiento  "AAAA-MM-DD"
//     fechaPago         "AAAA-MM-DD" o null si no se ha pagado
//   El estado NO se guarda: se deriva de las fechas (ver estadoDelPago).
//
// Errores: Error con `codigo` -> 'SIN_PERMISO' | 'ERROR_DE_RED'.
//
// PENDIENTE: confirmar con Juan / el modelo de datos (docs/info-sprints/sprint-1/diccionario-datos.md, sección 2.5 Pago):
// - La tabla Pago del diccionario aún no cierra estos campos (concepto, fecha de vencimiento, método, estado derivado).
// - "Hoy" es HOY_EJEMPLO. "Desde cuándo" = vencimiento más antiguo entre los pagos sin pagar del deportista.
// - Un acudiente solo ve a SUS deportistas (los que tienen su correo en `acudiente`). Aquí se simula; en producción
//   lo valida el backend con el JWT.
// - El pago en línea (Wompi) NO está en esta tarea.
// - La descarga es un CSV provisional generado en el navegador; la versión final será un PDF generado por el
//   backend (GET /pagos/estado-cuenta?formato=pdf).
import { HOY_EJEMPLO } from './entrenamientosService.js'
import { listarDeportistas } from './deportistasService.js'

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
      .map((p) => ({ ...p, estado: estadoDelPago(p, hoy) }))
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
