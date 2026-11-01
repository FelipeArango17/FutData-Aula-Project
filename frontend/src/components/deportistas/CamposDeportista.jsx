import Field from '../ui/Field.jsx'
import CampoFechaNacimiento from '../ui/CampoFechaNacimiento.jsx'
import { POSICIONES, TIPOS_DOCUMENTO_DEPORTISTA, calcularEdad } from '../../services/deportistasService.js'
import { ETIQUETA_GRUPO, GRUPOS_ENTRENAMIENTO, categoriaPorFecha, categoriaRequiereGrupo, rangoAniosNacimiento } from '../../utils/categorias.js'
import { hoyLocal } from '../../utils/fechas.js'
import { validarFechaNacimiento } from '../../utils/validaciones.js'

// Campos de los datos personales de un deportista. Los comparten el formulario del administrador (FormularioDeportista)
// y la inscripción en línea. No guardan nada: reciben texto(campo) -> { value, error, onChange } de quien los usa.
// props: texto, fechaNacimiento (para la edad y la categoría en vivo) y mostrarCategoria (la inscripción en línea no la muestra,
// ni el grupo de entrenamiento ni la posición, que son datos del club).
// El grupo de entrenamiento solo aparece (y es obligatorio) si la categoría que da la fecha es Cachorros o Semillero. El club decidió
// que el grupo no se guarda en la base de datos; la posición es opcional.
// La edad y la categoría NO son datos: se derivan de la fecha de nacimiento (con hoyLocal(), el mismo "hoy" de la validación).
// La categoría sale del AÑO de nacimiento (utils/categorias.js, regla provisional); quien guarda busca su id por nombre.
const textoEdad = (fechaNacimiento, hoy) => {
  if (validarFechaNacimiento(fechaNacimiento, hoy) !== null) return '—'
  const edad = calcularEdad(fechaNacimiento.trim(), hoy)
  return `${edad} ${edad === 1 ? 'año' : 'años'}`
}

const EDAD_TARJETA_IDENTIDAD = 14
const soloLectura = 'min-h-11 w-full rounded-md border bg-app px-3 text-body text-secondary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus'

// Dos columnas cuando caben (en rem, así que con texto grande pasan a una); una en pantallas angostas
const columnas = 'grid grid-cols-[repeat(auto-fit,minmax(min(100%,14rem),1fr))] gap-4'

export default function CamposDeportista({ texto, fechaNacimiento, mostrarCategoria = true }) {
  const hoy = hoyLocal()
  const { anioMinimo, anioMaximo } = rangoAniosNacimiento(hoy)
  const fecha = texto('fechaNacimiento')
  const tipo = texto('tipoDocumento')
  const edadReal = validarFechaNacimiento(fechaNacimiento, hoy) === null ? calcularEdad(fechaNacimiento.trim(), hoy) : null
  const avisoRegistroCivil = tipo.value === 'Registro civil' && edadReal !== null && edadReal >= EDAD_TARJETA_IDENTIDAD
  return (
    <>
      <p className="text-small text-secondary">* Campo obligatorio</p>
      <div className={columnas}>
        <Field label="Nombres" id="nombre" requerido autoComplete="off" {...texto('nombre')} />
        <Field label="Apellidos" id="apellido" requerido autoComplete="off" {...texto('apellido')} />
      </div>
      <div className={columnas}>
        <div className="flex flex-col gap-1">
          <Field label="Tipo de documento" id="tipoDocumento" requerido as="select" describedBy="tipoDocumento-ayuda" {...tipo}>
            <option value="">Selecciona el tipo de documento</option>
            {TIPOS_DOCUMENTO_DEPORTISTA.map((t) => <option key={t} value={t}>{t}</option>)}
          </Field>
          <p id="tipoDocumento-ayuda" className="break-words text-small text-secondary">Sugerido: tarjeta de identidad. Es obligatoria desde los 14 años.</p>
          {avisoRegistroCivil && (
            <p role="status" className="break-words text-small text-warning">A partir de los 14 años se espera tarjeta de identidad.</p>
          )}
        </div>
        <Field label="Número de documento" id="documento" requerido inputMode="numeric" autoComplete="off" {...texto('documento')} />
      </div>
      <div className={columnas}>
        <CampoFechaNacimiento
          label="Fecha de nacimiento"
          id="fechaNacimiento"
          requerido
          anioMinimo={anioMinimo}
          anioMaximo={anioMaximo}
          value={fecha.value}
          error={fecha.error}
          onChange={(valor) => fecha.onChange({ target: { value: valor } })}
        />
        <div className="flex flex-col gap-1">
          <Field
            label="Edad"
            id="edad"
            readOnly
            value={textoEdad(fechaNacimiento, hoy)}
            describedBy="edad-ayuda"
            className={soloLectura}
          />
          <p id="edad-ayuda" className="break-words text-small text-secondary">Se calcula con la fecha de nacimiento.</p>
        </div>
      </div>
      {mostrarCategoria && (
        <div className="flex flex-col gap-1">
          <Field label="Categoría" id="categoria" readOnly value={categoriaPorFecha(fechaNacimiento, hoy) ?? '—'} describedBy="categoria-ayuda" className={soloLectura} />
          <p id="categoria-ayuda" className="break-words text-small text-secondary">Se asigna automáticamente según el año de nacimiento.</p>
        </div>
      )}
      {mostrarCategoria && categoriaRequiereGrupo(categoriaPorFecha(fechaNacimiento, hoy)) && (
        <Field label={ETIQUETA_GRUPO} id="grupoEntrenamiento" requerido as="select" {...texto('grupoEntrenamiento')}>
          <option value="">Selecciona el grupo</option>
          {GRUPOS_ENTRENAMIENTO.map((g) => <option key={g} value={g}>{g[0].toUpperCase() + g.slice(1)}</option>)}
        </Field>
      )}
      {mostrarCategoria && (
        <Field label="Posición (opcional)" id="posicion" as="select" {...texto('posicion')}>
          <option value="">Sin definir</option>
          {POSICIONES.map((p) => <option key={p} value={p}>{p}</option>)}
        </Field>
      )}
      <Field label="Teléfono de contacto" id="telefono" requerido type="tel" inputMode="numeric" autoComplete="off" {...texto('telefono')} />
      <Field label="Dirección" id="direccion" requerido autoComplete="off" {...texto('direccion')} />
    </>
  )
}
