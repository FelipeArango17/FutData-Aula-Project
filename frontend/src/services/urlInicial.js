// La URL con la que se cargó la página, capturada ANTES de que supabase-js la procese (detectSessionInUrl): al terminar,
// supabase-js borra el hash con los tokens de la recuperación de contraseña y ya no se podría saber que venía de ese enlace.
// supabaseClient.js lo importa primero para que esto se evalúe antes de createClient.
export const urlInicial = Object.freeze({
  hash: globalThis.location?.hash ?? '',
  search: globalThis.location?.search ?? '',
})
