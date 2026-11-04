import { filtrarPorMesDeVencimiento, filtroMesEsLocal } from './pagosApi.js'

// Cómo se lee la lista de cobros de GestionCobros.
//   Servidor (sin mes, o tipo Mensualidad): una petición por página; el backend filtra por estado, tipo y periodo.
//   Local (mes con otro tipo o con todos): UNA lectura completa por combinación de estado + tipo + mes; el cambio de página
//   solo recorta la lista en memoria. Por eso la página no forma parte de la clave de lectura en modo local.
// `recarga` sube tras crear, aprobar o rechazar un cobro (o al reintentar) y fuerza una lectura nueva.
export function claveDeLectura({ estado, tipo, periodo, pagina, recarga }) {
  return filtroMesEsLocal(tipo, periodo)
    ? `${estado}|${tipo}|${periodo}|local|${recarga}`
    : `${estado}|${tipo}|${periodo}|${pagina}|${recarga}`
}

// estado: valor del backend ('' = todos). Devuelve { items, total, truncado? } en local y { items, total, pagina, tamano } en servidor.
export async function leerCobros({ estado, tipo, periodo, pagina, tamano }, { listarCobros, listarTodosLosCobros }) {
  if (filtroMesEsLocal(tipo, periodo)) {
    const r = await listarTodosLosCobros({ estado, tipo })
    const items = filtrarPorMesDeVencimiento(r.items, periodo)
    return { items, total: items.length, truncado: r.truncado }
  }
  return listarCobros({ estado, tipo, periodo, page: pagina - 1, size: tamano })
}
