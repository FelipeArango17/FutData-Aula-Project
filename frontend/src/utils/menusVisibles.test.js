import { describe, expect, it } from 'vitest'
import { menus, bottomNavs, RUTA_INSCRIPCION } from '../config/menus.js'
import { MODULOS_PREVISTOS } from '../services/usarApi.js'
import { menusVisibles, bottomNavVisible, inicioVisibleDe, opcionDeRuta, opcionVisible } from './menusVisibles.js'

const rutas = (grupos) => grupos.flatMap((g) => g.items.map((i) => i.to))

describe('menusVisibles', () => {
  it('sin autenticación real muestra todo', () => {
    const r = menusVisibles({ menus, role: 'administrador', autenticacionReal: false, modulos: [] })
    expect(r).toEqual(menus.administrador)
  })

  it('con autenticación real solo muestra los módulos conectados', () => {
    const r = menusVisibles({ menus, role: 'administrador', autenticacionReal: true, modulos: ['usuarios', 'deportistas'] })
    expect(rutas(r)).toEqual(['/administrador/deportistas', '/administrador/grupos', '/administrador/usuarios'])
  })

  it('quita el grupo que queda vacío, con su título', () => {
    const r = menusVisibles({ menus, role: 'administrador', autenticacionReal: true, modulos: ['usuarios'] })
    expect(r.map((g) => g.title)).toEqual(['Personas'])
  })

  it('un rol sin opciones visibles devuelve vacío y no rompe', () => {
    expect(menusVisibles({ menus, role: 'tesorero', autenticacionReal: true, modulos: [] })).toEqual([])
    expect(inicioVisibleDe({ menus, role: 'tesorero', autenticacionReal: true, modulos: [] })).toBeNull()
    expect(bottomNavVisible({ bottomNavs, role: 'tesorero', autenticacionReal: true, modulos: [] })).toEqual([])
    expect(menusVisibles({ menus, role: 'inexistente', autenticacionReal: true, modulos: [] })).toEqual([])
  })

  it('la barra inferior filtra igual y el administrador no tiene', () => {
    const r = bottomNavVisible({ bottomNavs, role: 'acudiente', autenticacionReal: true, modulos: ['pagos'] })
    expect(r.map((i) => i.to)).toEqual(['/acudiente/pagos'])
    expect(bottomNavVisible({ bottomNavs, role: 'administrador', autenticacionReal: true, modulos: ['pagos'] })).toEqual([])
  })

  it('inicioVisibleDe da la primera opción visible', () => {
    expect(inicioVisibleDe({ menus, role: 'entrenador', autenticacionReal: false, modulos: [] })).toBe('/entrenador/inicio')
    expect(inicioVisibleDe({ menus, role: 'entrenador', autenticacionReal: true, modulos: ['torneos', 'asistencia'] })).toBe('/entrenador/asistencia')
  })

  it('opcionDeRuta ubica las rutas extra en su pantalla madre', () => {
    expect(opcionDeRuta(menus, 'administrador', '/administrador/usuarios/entrenadores/nuevo').modulo).toBe('usuarios')
    expect(opcionDeRuta(menus, 'acudiente', '/acudiente/jugadores/3').modulo).toBe('acudientes')
    expect(opcionDeRuta(menus, 'administrador', '/administrador/horario-club')).toBeUndefined()
  })

  it('cada rol activa su inicio por separado', () => {
    const solo = (role, modulos) => inicioVisibleDe({ menus, role, autenticacionReal: true, modulos })
    expect(solo('tesorero', ['iniciotesorero'])).toBe('/tesorero/inicio')
    expect(solo('tesorero', ['inicioacudiente', 'dashboard', 'inicioentrenador'])).toBeNull()
    expect(solo('acudiente', ['inicioacudiente'])).toBe('/acudiente/inicio')
    expect(solo('acudiente', ['iniciotesorero'])).toBeNull()
    expect(solo('administrador', ['dashboard'])).toBe('/administrador/dashboard')
    expect(solo('administrador', ['inicioentrenador', 'misjugadores', 'iniciotesorero'])).toBeNull()
    expect(solo('entrenador', ['inicioentrenador'])).toBe('/entrenador/inicio')
    expect(solo('entrenador', ['misjugadores'])).toBe('/entrenador/jugadores')
    expect(solo('entrenador', ['dashboard', 'iniciotesorero', 'inicioacudiente'])).toBeNull()
  })

  it('reportes lo comparten el tesorero y el administrador', () => {
    const modulo = (role, to) => menus[role].flatMap((g) => g.items).find((i) => i.to === to).modulo
    expect(modulo('tesorero', '/tesorero/reportes')).toBe('reportes')
    expect(modulo('administrador', '/administrador/reportes')).toBe('reportes')
  })

  it('mapeo completo: toda opción tiene módulo previsto', () => {
    const items = [...Object.values(menus).flatMap((gs) => gs.flatMap((g) => g.items)), ...Object.values(bottomNavs).flat()]
    for (const item of items) expect(MODULOS_PREVISTOS, item.to).toContain(item.modulo)
  })
})

// Claves de producción (VITE_API_MODULOS de la entrega). Lo que no está aquí no tiene backend y no debe verse.
const PRODUCCION = ['entrenadores', 'pagos', 'horarioclub', 'usuarios', 'iniciotesorero', 'dashboard', 'inicioacudiente', 'reportes', 'inicioentrenador', 'misjugadores', 'deportistas', 'documentos', 'acudientes', 'asistencia']
const OCULTAS = ['/acudiente/notificaciones', '/tesorero/avisos', '/entrenador/torneos', '/administrador/torneos', '/administrador/respaldo']
const todas = (m) => Object.keys(m).flatMap((rol) => rutas(menusVisibles({ menus, role: rol, autenticacionReal: true, modulos: PRODUCCION })))

describe('módulos sin backend (avisos, torneos, respaldo, inscripción)', () => {
  it('con las claves de producción no aparecen en el menú de ningún rol ni en la barra inferior', () => {
    const visibles = todas(menus)
    for (const ruta of OCULTAS) expect(visibles).not.toContain(ruta)
    const barra = Object.keys(bottomNavs).flatMap((rol) => bottomNavVisible({ bottomNavs, role: rol, autenticacionReal: true, modulos: PRODUCCION }).map((i) => i.to))
    expect(barra).not.toContain('/acudiente/notificaciones')
    expect(barra).not.toContain('/tesorero/avisos')
  })

  it('la inscripción pública y la campana (módulo avisos) se ocultan sin su clave y vuelven con ella', () => {
    const ctx = (modulos) => ({ autenticacionReal: true, modulos })
    expect(opcionVisible(RUTA_INSCRIPCION, ctx(PRODUCCION))).toBe(false)
    expect(opcionVisible({ modulo: 'avisos' }, ctx(PRODUCCION))).toBe(false)
    expect(opcionVisible(RUTA_INSCRIPCION, ctx([...PRODUCCION, 'inscripcionpublica']))).toBe(true)
    expect(opcionVisible({ modulo: 'avisos' }, ctx([...PRODUCCION, 'avisos']))).toBe(true)
  })

  it('con las claves encendidas todo vuelve a verse', () => {
    const todasLasClaves = [...PRODUCCION, 'avisos', 'torneos', 'respaldo']
    const visibles = Object.keys(menus).flatMap((rol) => rutas(menusVisibles({ menus, role: rol, autenticacionReal: true, modulos: todasLasClaves })))
    for (const ruta of OCULTAS) expect(visibles).toContain(ruta)
  })

  it('el inicio de cada rol con las claves de producción nunca es una ruta oculta', () => {
    for (const rol of Object.keys(menus)) {
      const inicio = inicioVisibleDe({ menus, role: rol, autenticacionReal: true, modulos: PRODUCCION })
      expect(inicio).not.toBeNull()
      expect(OCULTAS).not.toContain(inicio)
    }
  })
})
