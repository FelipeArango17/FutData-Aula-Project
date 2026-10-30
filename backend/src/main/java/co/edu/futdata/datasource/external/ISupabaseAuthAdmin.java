package co.edu.futdata.datasource.external;

import java.util.UUID;

public interface ISupabaseAuthAdmin {
    UUID invitar(String correo);
}
