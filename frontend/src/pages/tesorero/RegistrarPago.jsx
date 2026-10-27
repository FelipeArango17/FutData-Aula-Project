import Page from '../../components/ui/Page.jsx'
import Button from '../../components/ui/Button.jsx'
import Field from '../../components/ui/Field.jsx'

export default function RegistrarPago({ base = '/tesorero' }) {
  return (
    <Page title="Registrar pago" subtitle="Santiago Ríos · María Torres">
      <form className="flex max-w-xl flex-col gap-4" onSubmit={(e) => e.preventDefault()}>
        <Field label="Concepto" id="concepto" as="select" defaultValue="Mensualidad octubre">
          <option>Mensualidad octubre</option>
          <option>Inscripción 2026</option>
          <option>Uniforme</option>
        </Field>
        <Field label="Valor" id="valor" defaultValue="$ 90.000" inputMode="numeric" />
        <Field label="Método de pago" id="metodo" as="select" defaultValue="Efectivo en sede">
          <option>Efectivo en sede</option>
          <option>Transferencia</option>
          <option>Wompi</option>
        </Field>
        <Field label="Fecha de pago" id="fecha" defaultValue="26 oct. 2026" />
        <p className="text-body text-secondary">
          Al guardar se genera el comprobante y se notifica al acudiente.
        </p>
        <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
          <Button>Guardar y generar comprobante</Button>
          <Button variant="secondary" to={`${base}/pagos`}>Cancelar</Button>
        </div>
      </form>
    </Page>
  )
}
