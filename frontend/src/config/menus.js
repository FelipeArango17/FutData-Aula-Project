// Menús fijos por rol. Cada rol es una lista de grupos { title?, items: [{ label, to, modulo }] }.
// Si un grupo no tiene title, no se dibuja encabezado.
export const roles = ['acudiente', 'entrenador', 'tesorero', 'administrador']

export const roleLabels = {
  acudiente: 'Acudiente',
  entrenador: 'Entrenador',
  tesorero: 'Tesorero',
  administrador: 'Administrador',
}

// Datos de ejemplo hasta que exista el login
export const sampleUsers = {
  acudiente: 'María Torres',
  entrenador: 'Nicolás',
  tesorero: 'Laura Gómez',
  administrador: 'Carlos Mendoza',
}

export const menus = {
  acudiente: [
    {
      items: [
        { label: 'Inicio', to: '/acudiente/inicio', modulo: 'inicioacudiente' },
        { label: 'Mis jugadores', to: '/acudiente/jugadores', modulo: 'acudientes' },
        { label: 'Pagos', to: '/acudiente/pagos', modulo: 'pagos' },
        { label: 'Documentos', to: '/acudiente/documentos', modulo: 'documentos' },
        { label: 'Horarios', to: '/acudiente/horarios', modulo: 'horarioclub' },
        { label: 'Notificaciones', to: '/acudiente/notificaciones', modulo: 'avisos' },
      ],
    },
  ],
  entrenador: [
    {
      items: [
        { label: 'Inicio', to: '/entrenador/inicio', modulo: 'inicioentrenador' },
        { label: 'Mis jugadores', to: '/entrenador/jugadores', modulo: 'misjugadores' },
        { label: 'Asistencia', to: '/entrenador/asistencia', modulo: 'asistencia' },
        { label: 'Horarios', to: '/entrenador/horarios', modulo: 'horarioclub' },
        { label: 'Torneos', to: '/entrenador/torneos', modulo: 'torneos' },
      ],
    },
  ],
  tesorero: [
    {
      items: [
        { label: 'Inicio', to: '/tesorero/inicio', modulo: 'iniciotesorero' },
        { label: 'Pagos', to: '/tesorero/pagos', modulo: 'pagos' },
        { label: 'Reportes', to: '/tesorero/reportes', modulo: 'reportes' },
        { label: 'Avisos', to: '/tesorero/avisos', modulo: 'avisos' },
      ],
    },
  ],
  administrador: [
    {
      items: [{ label: 'Dashboard', to: '/administrador/dashboard', modulo: 'dashboard' }],
    },
    {
      title: 'Personas',
      items: [
        { label: 'Deportistas', to: '/administrador/deportistas', modulo: 'deportistas' },
        { label: 'Grupos', to: '/administrador/grupos', modulo: 'deportistas' },
        { label: 'Documentos', to: '/administrador/documentos', modulo: 'documentos' },
        { label: 'Usuarios y roles', to: '/administrador/usuarios', modulo: 'usuarios' },
      ],
    },
    {
      title: 'Deportivo',
      items: [
        { label: 'Asistencia', to: '/administrador/asistencia', modulo: 'asistencia' },
        { label: 'Horarios', to: '/administrador/horarios', modulo: 'horarioclub' },
        { label: 'Torneos', to: '/administrador/torneos', modulo: 'torneos' },
      ],
    },
    {
      title: 'Finanzas',
      items: [
        { label: 'Pagos', to: '/administrador/pagos', modulo: 'pagos' },
        { label: 'Reportes', to: '/administrador/reportes', modulo: 'reportes' },
      ],
    },
    {
      title: 'Sistema',
      items: [{ label: 'Respaldo', to: '/administrador/respaldo', modulo: 'respaldo' }],
    },
  ],
}

// Barra inferior móvil: 4 opciones. El administrador no tiene (no aparece aquí).
// `icon` es el nombre de un ícono de components/Icon.jsx.
export const bottomNavs = {
  acudiente: [
    { label: 'Inicio', to: '/acudiente/inicio', icon: 'home', modulo: 'inicioacudiente' },
    { label: 'Jugadores', to: '/acudiente/jugadores', icon: 'users', modulo: 'acudientes' },
    { label: 'Pagos', to: '/acudiente/pagos', icon: 'wallet', modulo: 'pagos' },
    { label: 'Avisos', to: '/acudiente/notificaciones', icon: 'bell', modulo: 'avisos' },
  ],
  entrenador: [
    { label: 'Inicio', to: '/entrenador/inicio', icon: 'home', modulo: 'inicioentrenador' },
    { label: 'Jugadores', to: '/entrenador/jugadores', icon: 'users', modulo: 'misjugadores' },
    { label: 'Asistencia', to: '/entrenador/asistencia', icon: 'check', modulo: 'asistencia' },
    { label: 'Horarios', to: '/entrenador/horarios', icon: 'calendar', modulo: 'horarioclub' },
  ],
  tesorero: [
    { label: 'Inicio', to: '/tesorero/inicio', icon: 'home', modulo: 'iniciotesorero' },
    { label: 'Pagos', to: '/tesorero/pagos', icon: 'wallet', modulo: 'pagos' },
    { label: 'Reportes', to: '/tesorero/reportes', icon: 'chart', modulo: 'reportes' },
    { label: 'Avisos', to: '/tesorero/avisos', icon: 'bell', modulo: 'avisos' },
  ],
}

// Primera pantalla del menú de un rol: a donde se llega después de iniciar sesión.
// (Sin filtrar por módulos: la primera opción del menú completo. Para la entrega usa inicioVisibleDe de utils/menusVisibles.js.)
export const inicioDe = (role) => menus[role][0].items[0].to

export const getItems = (role) => menus[role].flatMap((group) => group.items)

// Rutas públicas que dependen de un módulo (fuera del menú). Sin el módulo en VITE_API_MODULOS se ocultan: la ruta redirige al login
// y ningún enlace lleva a ella.
export const RUTA_INSCRIPCION = { to: '/inscripcion', modulo: 'inscripcionpublica' }
