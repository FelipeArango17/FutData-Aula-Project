// Menús fijos por rol. Cada rol es una lista de grupos { title?, items: [{ label, to }] }.
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
  entrenador: 'Andrés Mora',
  tesorero: 'Laura Gómez',
  administrador: 'Carlos Mendoza',
}

export const menus = {
  acudiente: [
    {
      items: [
        { label: 'Inicio', to: '/acudiente/inicio' },
        { label: 'Mis jugadores', to: '/acudiente/jugadores' },
        { label: 'Pagos', to: '/acudiente/pagos' },
        { label: 'Documentos', to: '/acudiente/documentos' },
        { label: 'Horarios', to: '/acudiente/horarios' },
        { label: 'Notificaciones', to: '/acudiente/notificaciones' },
      ],
    },
  ],
  entrenador: [
    {
      items: [
        { label: 'Inicio', to: '/entrenador/inicio' },
        { label: 'Mis grupos', to: '/entrenador/grupos' },
        { label: 'Asistencia', to: '/entrenador/asistencia' },
        { label: 'Horarios', to: '/entrenador/horarios' },
        { label: 'Torneos', to: '/entrenador/torneos' },
      ],
    },
  ],
  tesorero: [
    {
      items: [
        { label: 'Inicio', to: '/tesorero/inicio' },
        { label: 'Pagos', to: '/tesorero/pagos' },
        { label: 'Reportes', to: '/tesorero/reportes' },
        { label: 'Avisos', to: '/tesorero/avisos' },
      ],
    },
  ],
  administrador: [
    {
      items: [{ label: 'Dashboard', to: '/administrador/dashboard' }],
    },
    {
      title: 'Personas',
      items: [
        { label: 'Deportistas', to: '/administrador/deportistas' },
        { label: 'Grupos', to: '/administrador/grupos' },
        { label: 'Documentos', to: '/administrador/documentos' },
        { label: 'Usuarios y roles', to: '/administrador/usuarios' },
      ],
    },
    {
      title: 'Deportivo',
      items: [
        { label: 'Asistencia', to: '/administrador/asistencia' },
        { label: 'Horarios', to: '/administrador/horarios' },
        { label: 'Torneos', to: '/administrador/torneos' },
      ],
    },
    {
      title: 'Finanzas',
      items: [
        { label: 'Pagos', to: '/administrador/pagos' },
        { label: 'Reportes', to: '/administrador/reportes' },
      ],
    },
    {
      title: 'Sistema',
      items: [{ label: 'Respaldo', to: '/administrador/respaldo' }],
    },
  ],
}

// Barra inferior móvil: 4 opciones. El administrador no tiene (no aparece aquí).
// `icon` es el nombre de un ícono de components/Icon.jsx.
export const bottomNavs = {
  acudiente: [
    { label: 'Inicio', to: '/acudiente/inicio', icon: 'home' },
    { label: 'Jugadores', to: '/acudiente/jugadores', icon: 'users' },
    { label: 'Pagos', to: '/acudiente/pagos', icon: 'wallet' },
    { label: 'Avisos', to: '/acudiente/notificaciones', icon: 'bell' },
  ],
  entrenador: [
    { label: 'Inicio', to: '/entrenador/inicio', icon: 'home' },
    { label: 'Asistencia', to: '/entrenador/asistencia', icon: 'check' },
    { label: 'Horarios', to: '/entrenador/horarios', icon: 'calendar' },
    { label: 'Torneos', to: '/entrenador/torneos', icon: 'trophy' },
  ],
  tesorero: [
    { label: 'Inicio', to: '/tesorero/inicio', icon: 'home' },
    { label: 'Pagos', to: '/tesorero/pagos', icon: 'wallet' },
    { label: 'Reportes', to: '/tesorero/reportes', icon: 'chart' },
    { label: 'Avisos', to: '/tesorero/avisos', icon: 'bell' },
  ],
}

// Primera pantalla del menú de un rol: a donde se llega después de iniciar sesión.
export const inicioDe = (role) => menus[role][0].items[0].to

export const getItems = (role) => menus[role].flatMap((group) => group.items)
