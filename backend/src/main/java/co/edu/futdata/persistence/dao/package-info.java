/**
 * <b>Persistencia: interfaces DAO</b> (patrón DAO).
 *
 * <p>Una interfaz por entidad o agregado ({@code I<Recurso>DAO}, por ejemplo {@code IUsuarioDAO})
 * con las operaciones de acceso a datos que necesita el servicio ({@code buscarPorId},
 * {@code guardar}...). Recibe y devuelve entidades de dominio, nunca {@code ResultSet} ni SQL. El
 * servicio depende solo de esta interfaz, así que cambiar la base de datos no lo afecta.
 */
package co.edu.futdata.persistence.dao;
