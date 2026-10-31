import Field from '../ui/Field.jsx'
import { HOY_EJEMPLO, TIPOS_DOCUMENTO_DEPORTISTA, calcularEdad } from '../../services/deportistasService.js'
import { hoyLocal } from '../../utils/fechas.js'
import { validarFechaNacimiento } from '../../utils/validaciones.js'

// Campos de los datos personales de un deportista. Los comparten el formulario del administrador (FormularioDeportista)
// y la inscripción en línea. No guardan nada: reciben texto(campo) -> { value, error, onChange } de quien los usa.
// props: texto, fechaNacimiento (para la edad en vivo), categorias y mostrarCategoria (la inscripción en línea no pide categoría)
// La edad NO es un dato: se calcula en vivo desde la fecha de nacimiento (con el mismo HOY_EJEMPLO que el servicio).
const textoEdad = (fechaNacimiento) => {
  if (validarFechaNacimiento(fechaNacimiento, HOY_EJEMPLO) !== null) return '—'
  const edad = calcularEdad(fechaNacimiento.trim(), HOY_EJEMPLO)
  return `${edad} ${edad === 1 ? 'año' : 'años'}`
}

// Dos columnas cuando caben (en rem, así que con texto grande pasan a una); una en pantallas angostas
const columnas = 'grid grid-cols-[repeat(auto-fit,minmax(min(100%,14rem),1fr))] gap-4'

export default function CamposDeportista({ texto, fechaNacimiento, categorias = [], mostrarCategoria = true }) {
  return (
    <>
      <p className="text-small text-secondary">* Campo obligatorio</p>
      <div className={columnas}>
        <Field label="Nombres" id="nombre" requerido autoComplete="off" {...texto('nombre')} />
        <Field label="Apellidos" id="apellido" requerido autoComplete="off" {...texto('apellido')} />
      </div>
      <div className={columnas}>
        <Field label="Tipo de documento" id="tipoDocumento" requerido as="select" {...texto('tipoDocumento')}>
          <option value="">Selecciona el tipo de documento</option>
          {TIPOS_DOCUMENTO_DEPORTISTA.map((t) => <option key={t} value={t}>{t}</option>)}
        </Field>
        <Field label="Número de documento" id="documento" requerido inputMode="numeric" autoComplete="off" {...texto('documento')} />
      </div>
      <div className={columnas}>
        <Field label="Fecha de nacimiento" id="fechaNacimiento" type="date" requerido max={hoyLocal()} {...texto('fechaNacimiento')} />
        <div className="flex flex-col gap-1">
          <Field
            label="Edad"
            id="edad"
            readOnly
            value={textoEdad(fechaNacimiento)}
            describedBy="edad-ayuda"
            className="min-h-11 w-full rounded-md border bg-app px-3 text-body text-secondary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
          />
          <p id="edad-ayuda" className="break-words text-small text-secondary">Se calcula con la fecha de nacimiento.</p>
        </div>
      </div>
      <Field label="Teléfono de contacto" id="telefono" requerido type="tel" inputMode="numeric" autoComplete="off" {...texto('telefono')} />
      <Field label="Dirección" id="direccion" requerido autoComplete="off" {...texto('direccion')} />
      {mostrarCategoria && (
        <Field label="Categoría (opcional)" id="idCategoria" as="select" {...texto('idCategoria')}>
          <option value="">Sin asignar</option>
          {categorias.map((c) => <option key={c.id} value={c.id}>{c.nombre}</option>)}
        </Field>
      )}
    </>
  )
}
