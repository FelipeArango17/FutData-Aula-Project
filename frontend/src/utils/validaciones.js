// Validaciones de texto reutilizables en los formularios.
// Correo: algo@dominio.ext, sin espacios. Es una revisión de formato, no prueba que el buzón exista.
export const esCorreoValido = (correo) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(correo.trim())
