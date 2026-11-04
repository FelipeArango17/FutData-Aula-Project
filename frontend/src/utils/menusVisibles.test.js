import { describe, expect, it } from 'vitest'
import { menus, bottomNavs } from '../config/menus.js'
import { MODULOS_PREVISTOS } from '../services/usarApi.js'
import { menusVisibles, bottomNavVisible, inicioVisibleDe, opcionDeRuta } from './menusVisibles.js'

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

  it('mapeo completo: toda opción tiene módulo previsto', () => {
    const items = [...Object.values(menus).flatMap((gs) => gs.flatMap((g) => g.items)), ...Object.values(bottomNavs).flat()]
    for (const item of items) expect(MODULOS_PREVISTOS, item.to).toContain(item.modulo)
  })
})
