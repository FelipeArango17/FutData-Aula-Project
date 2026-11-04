// Regla de la entrega final: ninguna pantalla muestra datos de ejemplo. Cada opción del menú lleva su `modulo`
// y solo se muestra si ese módulo está conectado a la API (VITE_API_MODULOS).
// Con autenticacionReal en false (desarrollo con datos de ejemplo y pruebas) se muestra todo.
// Funciones puras: reciben todo por parámetro.

const normalizar = (lista) => new Set((lista ?? []).map((m) => String(m).trim().toLowerCase()))

export function opcionVisible(item, { autenticacionReal, modulos }) {
  if (!autenticacionReal) return true
  return normalizar(modulos).has(String(item.modulo ?? '').toLowerCase())
}

// Grupos del rol con solo las opciones visibles; un grupo sin opciones se quita entero (con su título).
export function menusVisibles({ menus, role, autenticacionReal, modulos }) {
  return (menus[role] ?? [])
    .map((grupo) => ({ ...grupo, items: grupo.items.filter((item) => opcionVisible(item, { autenticacionReal, modulos })) }))
    .filter((grupo) => grupo.items.length > 0)
}

// Barra inferior móvil: lista plana de opciones visibles (vacía si el rol no tiene barra).
export function bottomNavVisible({ bottomNavs, role, autenticacionReal, modulos }) {
  return (bottomNavs[role] ?? []).filter((item) => opcionVisible(item, { autenticacionReal, modulos }))
}

// Primera opción visible del rol (ruta), o null si ninguna.
export function inicioVisibleDe({ menus, role, autenticacionReal, modulos }) {
  return menusVisibles({ menus, role, autenticacionReal, modulos })[0]?.items[0]?.to ?? null
}

// Opción del menú a la que pertenece una ruta (la ruta misma o una hija: /administrador/usuarios/nuevo -> usuarios).
export function opcionDeRuta(menus, role, ruta) {
  return (menus[role] ?? [])
    .flatMap((g) => g.items)
    .find((item) => ruta === item.to || ruta.startsWith(`${item.to}/`))
}
