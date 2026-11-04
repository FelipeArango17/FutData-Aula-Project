import { describe, expect, it, vi } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'

vi.mock('../../services/pagosService.js', () => ({
  aprobarPagoManual: vi.fn(), conciliarCobro: vi.fn(), crearCobro: vi.fn(), descargarComprobante: vi.fn(), generarCiclos: vi.fn(), listarCobros: vi.fn(), rechazarCobro: vi.fn(),
}))
vi.mock('../../services/deportistasService.js', () => ({ listarDeportistas: vi.fn() }))

vi.mock('react-dom', async (original) => ({ ...(await original()), createPortal: (nodo) => nodo })) // el diálogo va en un portal

import DialogoConfirmacion from '../ui/DialogoConfirmacion.jsx'
import GestionCobros from './GestionCobros.jsx'
import { CONFIRMACION_GENERAR_CICLOS, MENSAJE_MONTO_NO_CONFIGURADO, NOTA_PERIODO_SOLO_MENSUALIDAD, resumenGeneracionCiclos, tipoUsaPeriodo } from '../../utils/pagosApi.js'

const html = (props) => renderToStaticMarkup(<MemoryRouter><GestionCobros titulo="Pagos" subtitulo="x" {...props} /></MemoryRouter>)

describe('botón "Generar cobros del ciclo"', () => {
  it('solo lo ve el administrador', () => {
    expect(html({ esAdministrador: true })).toContain('Generar cobros del ciclo')
    expect(html({ esAdministrador: false })).not.toContain('Generar cobros del ciclo')
    expect(html({ esAdministrador: true })).not.toContain('Generar mensualidades')
  })

  it('al inicio no está bloqueado', () => {
    expect(html({ esAdministrador: true })).not.toMatch(/<button[^>]*aria-busy="true"[^>]*>Generar cobros/)
  })
})

describe('filtro de periodo', () => {
  it('con "Todos los tipos" está activo y sin nota', () => {
    const h = html({})
    expect(h).toContain('id="filtro-periodo"')
    expect(h).not.toMatch(/<fieldset[^>]*id="filtro-periodo"[^>]*disabled/)
    expect(h).not.toContain(NOTA_PERIODO_SOLO_MENSUALIDAD)
  })

  it('el periodo aplica solo a Todos y Mensualidad', () => {
    expect(tipoUsaPeriodo('')).toBe(true)
    expect(tipoUsaPeriodo('MENSUALIDAD')).toBe(true)
    for (const t of ['INSCRIPCION', 'TORNEO', 'UNIFORME']) expect(tipoUsaPeriodo(t)).toBe(false)
  })
})

describe('resumenGeneracionCiclos', () => {
  it('resultado sin problemas', () => {
    const r = resumenGeneracionCiclos({ creadas: 12, yaExistentes: 3, avisosEnviados: 10, avisosFallidos: 0, deportistasConError: 0 })
    expect(r.conProblemas).toBe(false)
    expect(r.texto).toBe('Se crearon 12 cobros nuevos. Ya existían 3 cobros; no se duplicaron. Se enviaron 10 avisos por correo.')
  })

  it('singulares y nada nuevo', () => {
    expect(resumenGeneracionCiclos({ creadas: 1, yaExistentes: 1, avisosEnviados: 1 }).texto).toBe('Se creó 1 cobro nuevo. Ya existía 1 cobro; no se duplicaron. Se envió 1 aviso por correo.')
    expect(resumenGeneracionCiclos({ creadas: 0, yaExistentes: 5 }).texto).toContain('No hizo falta crear cobros nuevos.')
  })

  it('avisos fallidos y deportistas con error se señalan como problema', () => {
    const r = resumenGeneracionCiclos({ creadas: 2, yaExistentes: 0, avisosEnviados: 0, avisosFallidos: 2, deportistasConError: 1 })
    expect(r.conProblemas).toBe(true)
    expect(r.texto).toContain('Los cobros quedaron creados, pero 2 avisos por correo no se pudieron enviar y no se reintentan solos.')
    expect(r.texto).toContain('1 deportista no se pudo procesar. Vuelve a generar los cobros para completarlos.')
  })

  it('"vuelve a generar" solo aparece con deportistas con error, nunca con avisos fallidos', () => {
    const soloAvisos = resumenGeneracionCiclos({ creadas: 1, avisosFallidos: 1 }).texto
    expect(soloAvisos).toContain('1 aviso por correo no se pudo enviar y no se reintenta solo')
    expect(soloAvisos).not.toMatch(/vuelve a generar/i)
  })

  it('mensaje del 409 es comprensible', () => {
    expect(MENSAJE_MONTO_NO_CONFIGURADO).toMatch(/valor de la mensualidad no está configurado/)
  })
})

describe('confirmación antes de generar los cobros del ciclo', () => {
  it('el diálogo es accesible: rol, modal, título y descripción, con Cancelar y Generar', () => {
    vi.stubGlobal('document', { body: {} }) // el portal recibe document.body; en el render de servidor no existe
    const c = CONFIRMACION_GENERAR_CICLOS
    const h = renderToStaticMarkup(<DialogoConfirmacion open titulo={c.titulo} descripcion={c.descripcion} textoConfirmar={c.boton} onConfirmar={() => {}} onCancelar={() => {}} />)
    expect(h).toContain('role="alertdialog"')
    expect(h).toContain('aria-modal="true"')
    expect(h).toMatch(/aria-labelledby="[^"]+"/)
    expect(h).toMatch(/aria-describedby="[^"]+"/)
    expect(h).toContain('Crea los cobros que le tocan a cada jugador según su fecha de ingreso, hasta 5 días antes de su fecha de cobro. Los que ya existen no se repiten. Esto también se hace solo cada día a las 6:00 a. m. ¿Continuar?')
    expect(h).toMatch(/>Cancelar</)
    expect(h).toMatch(/>Generar</)
    vi.unstubAllGlobals()
  })

  it('cerrado al inicio: el botón no genera nada hasta confirmar', () => {
    expect(html({ esAdministrador: true })).not.toContain('alertdialog')
  })
})
