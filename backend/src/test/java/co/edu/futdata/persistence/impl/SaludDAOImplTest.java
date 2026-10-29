package co.edu.futdata.persistence.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/** SELECT 1 con el DataSource simulado (Mockito), sin base de datos real. */
class SaludDAOImplTest {

    @Test
    void devuelveTrueSiLaBaseRespondeYUsaUnTimeoutCorto() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection conexion = mock(Connection.class);
        Statement sentencia = mock(Statement.class);
        ResultSet resultado = mock(ResultSet.class);
        ResultSetMetaData metadatos = mock(ResultSetMetaData.class);
        when(dataSource.getConnection()).thenReturn(conexion);
        when(conexion.createStatement()).thenReturn(sentencia);
        when(sentencia.executeQuery("SELECT 1")).thenReturn(resultado);
        when(resultado.next()).thenReturn(true, false);
        when(resultado.getMetaData()).thenReturn(metadatos);
        when(metadatos.getColumnCount()).thenReturn(1);
        when(resultado.getInt(anyInt())).thenReturn(1);

        assertThat(new SaludDAOImpl(dataSource).baseDeDatosDisponible()).isTrue();
        verify(sentencia).setQueryTimeout(SaludDAOImpl.TIMEOUT_SEGUNDOS);
    }

    @Test
    void devuelveFalseSiNoHayConexion() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection())
                .thenThrow(new SQLTransientConnectionException("futdata-pool - Connection is not available"));

        assertThat(new SaludDAOImpl(dataSource).baseDeDatosDisponible()).isFalse();
    }
}
