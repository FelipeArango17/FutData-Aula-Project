import Page from '../../components/ui/Page.jsx'
import Button from '../../components/ui/Button.jsx'
import Field from '../../components/ui/Field.jsx'
import Icon from '../../components/Icon.jsx'

export default function NuevoEntrenamiento() {
  return (
    <Page title="Nuevo entrenamiento" subtitle="Define la categoría, la fecha, la hora y el lugar">
      <form className="flex max-w-xl flex-col gap-4" onSubmit={(e) => e.preventDefault()}>
        <Field label="Categoría" id="categoria" as="select" defaultValue="Sub-12">
          <option>Sub-10</option>
          <option>Sub-12</option>
        </Field>
        <Field label="Fecha" id="fecha" defaultValue="Sáb 24 oct. 2026" />
        <Field label="Hora" id="hora" defaultValue="10:00 a. m." />
        <Field label="Lugar" id="lugar" as="select" defaultValue="Cancha 2">
          <option>Cancha 1</option>
          <option>Cancha 2</option>
        </Field>
        <div role="alert" className="flex items-start gap-2 rounded-lg bg-danger p-4 text-danger">
          <Icon name="alert" className="mt-0.5 h-5 w-5 shrink-0" />
          <p className="text-body-medium">
            Conflicto: Cancha 2 ocupada el sábado a las 10:00. Horarios libres: 11:30 a. m. o Cancha 1 a las 10:00.
          </p>
        </div>
        <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
          <Button>Guardar y notificar</Button>
          <Button variant="secondary" to="/entrenador/horarios">Cancelar</Button>
        </div>
      </form>
    </Page>
  )
}
