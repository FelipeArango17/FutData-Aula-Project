import { describe, expect, it, vi } from 'vitest'
import { claveDeLectura, leerCobros } from './lecturaCobros.js'

const cobro = (id, fechaVencimiento) => ({ id, fechaVencimiento })
const todos = Array.from({ length: 25 }, (_, i) => cobro(i, '2026-11-05')).concat([cobro(99, '2026-12-01')])

function servicios() {
  return {
    listarTodosLosCobros: vi.fn().mockResolvedValue({ items: todos, total: todos.length, truncado: false }),
    listarCobros: vi.fn().mockResolvedValue({ items: [], total: 0, pagina: 0, tamano: 10 }),
  }
}

// Imita el efecto de GestionCobros: solo lee cuando cambia la clave de lectura.
function pantalla() {
  const s = servicios()
  let ultima = null
  let estado = { estado: 'Todos', tipo: '', periodo: '', pagina: 1, recarga: 0 }
  const lecturas = () => s.listarTodosLosCobros.mock.calls.length + s.listarCobros.mock.calls.length
  async function mostrar(cambios = {}) {
    estado = { ...estado, ...cambios }
    const clave = claveDeLectura(estado)
    if (clave === ultima) return
    ultima = clave
    await leerCobros({ estado: estado.estado === 'Todos' ? '' : estado.estado, tipo: estado.tipo, periodo: estado.periodo, pagina: estado.pagina, tamano: 10 }, s)
  }
  return { s, mostrar, lecturas }
}

describe('filtro local (mes por vencimiento): una sola lectura completa por estado + tipo + mes', () => {
  it('cambiar de página no vuelve a leer', async () => {
    const p = pantalla()
    await p.mostrar({ periodo: '2026-11' })
    await p.mostrar({ pagina: 2 })
    await p.mostrar({ pagina: 3 })
    await p.mostrar({ pagina: 1 })
    expect(p.s.listarTodosLosCobros).toHaveBeenCalledTimes(1)
    expect(p.s.listarCobros).not.toHaveBeenCalled()
  })

  it('cambiar de estado, de tipo o de mes sí vuelve a leer', async () => {
    const p = pantalla()
    await p.mostrar({ periodo: '2026-11' })
    await p.mostrar({ estado: 'PENDIENTE' })
    await p.mostrar({ tipo: 'TORNEO' })
    await p.mostrar({ periodo: '2026-12' })
    expect(p.s.listarTodosLosCobros).toHaveBeenCalledTimes(4)
    expect(p.s.listarTodosLosCobros).toHaveBeenLastCalledWith({ estado: 'PENDIENTE', tipo: 'TORNEO' })
  })

  it('tras crear, aprobar o rechazar (sube recarga) la lista se vuelve a leer', async () => {
    const p = pantalla()
    await p.mostrar({ periodo: '2026-11' })
    await p.mostrar({ recarga: 1 })
    await p.mostrar({ recarga: 2 })
    expect(p.s.listarTodosLosCobros).toHaveBeenCalledTimes(3)
  })

  it('devuelve toda la lista filtrada por mes (la página se recorta en la pantalla)', async () => {
    const r = await leerCobros({ estado: '', tipo: '', periodo: '2026-11', pagina: 0, tamano: 10 }, servicios())
    expect(r.items).toHaveLength(25)
    expect(r.total).toBe(25)
    expect(r.truncado).toBe(false)
  })
})

describe('filtro en el servidor (sin mes, o Mensualidad)', () => {
  it('cada página es una petición y cada cambio de página vuelve a leer', async () => {
    const p = pantalla()
    await p.mostrar({})
    await p.mostrar({ pagina: 2 })
    await p.mostrar({ tipo: 'MENSUALIDAD', periodo: '2026-11', pagina: 1 })
    await p.mostrar({ pagina: 2 })
    expect(p.s.listarCobros).toHaveBeenCalledTimes(4)
    expect(p.s.listarCobros).toHaveBeenLastCalledWith({ estado: '', tipo: 'MENSUALIDAD', periodo: '2026-11', page: 1, size: 10 })
    expect(p.s.listarTodosLosCobros).not.toHaveBeenCalled()
  })
})
