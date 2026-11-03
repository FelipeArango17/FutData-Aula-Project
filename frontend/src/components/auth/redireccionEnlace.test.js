import { describe, expect, it } from 'vitest'
import { decidirRedireccionEnlace } from './redireccionEnlace.js'

// El proyecto no tiene DOM en las pruebas: se simula la secuencia de rutas que recorre LlevarEnlaceDeCorreo
// (cada cambio de ruta repite el efecto con el `consumida` que dejó el anterior).
function recorrer(activo, rutas) {
  let consumida = false
  let actual = rutas[0]
  const visitadas = [actual]
  for (const siguiente of rutas.slice(1)) {
    // el efecto corre con la ruta actual; si redirige, la ruta cambia y el efecto corre de nuevo
    for (let vueltas = 0; vueltas < 5; vueltas += 1) {
      const d = decidirRedireccionEnlace({ activo, consumida, pathname: actual })
      consumida = d.consumida
      if (!d.destino) break
      actual = d.destino
      visitadas.push(actual)
    }
    actual = siguiente
    visitadas.push(actual)
  }
  return { visitadas, final: actual }
}

describe('decidirRedireccionEnlace', () => {
  it('(a) entrar en "/" con activo redirige a /restablecer', () => {
    expect(decidirRedireccionEnlace({ activo: true, consumida: false, pathname: '/' })).toEqual({ consumida: true, destino: '/restablecer' })
  })
  it('(b) ya consumida: navegar a /login se queda en /login', () => {
    expect(decidirRedireccionEnlace({ activo: true, consumida: true, pathname: '/login' })).toEqual({ consumida: true, destino: null })
    const { visitadas, final } = recorrer(true, ['/', '/login'])
    expect(visitadas).toEqual(['/', '/restablecer', '/login'])
    expect(final).toBe('/login')
  })
  it('(c) con activo=false nunca redirige', () => {
    expect(decidirRedireccionEnlace({ activo: false, consumida: false, pathname: '/' })).toEqual({ consumida: false, destino: null })
    expect(recorrer(false, ['/', '/login']).visitadas).toEqual(['/', '/login'])
  })
  it('(d) entrar directo en /restablecer o /recuperar no redirige y consume la redirección', () => {
    for (const ruta of ['/restablecer', '/recuperar']) {
      expect(decidirRedireccionEnlace({ activo: true, consumida: false, pathname: ruta })).toEqual({ consumida: true, destino: null })
    }
    expect(recorrer(true, ['/restablecer', '/login']).visitadas).toEqual(['/restablecer', '/login'])
  })
  it('el doble efecto de StrictMode no redirige dos veces', () => {
    const primero = decidirRedireccionEnlace({ activo: true, consumida: false, pathname: '/' })
    const segundo = decidirRedireccionEnlace({ activo: true, consumida: primero.consumida, pathname: '/' })
    expect(primero.destino).toBe('/restablecer')
    expect(segundo.destino).toBeNull()
  })
})
