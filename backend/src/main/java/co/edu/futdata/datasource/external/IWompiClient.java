package co.edu.futdata.datasource.external;

import java.util.List;

/** Consulta de transacciones a Wompi (conciliación). Se simula en las pruebas: nunca se llama a Wompi de verdad. */
public interface IWompiClient {

    /**
     * Las transacciones reales de una referencia (GET {@code /transactions?reference=}), con {@code WOMPI_PRIVATE_KEY}.
     *
     * @throws WompiNoDisponibleException Wompi no respondió o respondió un error
     */
    List<TransaccionWompi> transaccionesDe(String referencia);
}
