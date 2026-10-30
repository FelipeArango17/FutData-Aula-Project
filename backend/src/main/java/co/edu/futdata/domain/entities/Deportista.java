package co.edu.futdata.domain.entities;

import java.time.LocalDate;
import java.util.UUID;

/** Deportista registrado en el club. */
public class Deportista {

    private UUID id;
    private String nombre;
    private String apellido;
    private String tipoDocumento;
    private String documento;
    private LocalDate fechaNacimiento;
    private String telefono;
    private String direccion;
    private UUID idCategoria;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getApellido() { return apellido; }
    public void setApellido(String apellido) { this.apellido = apellido; }
    public String getTipoDocumento() { return tipoDocumento; }
    public void setTipoDocumento(String tipoDocumento) { this.tipoDocumento = tipoDocumento; }
    public String getDocumento() { return documento; }
    public void setDocumento(String documento) { this.documento = documento; }
    public LocalDate getFechaNacimiento() { return fechaNacimiento; }
    public void setFechaNacimiento(LocalDate fechaNacimiento) { this.fechaNacimiento = fechaNacimiento; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public UUID getIdCategoria() { return idCategoria; }
    public void setIdCategoria(UUID idCategoria) { this.idCategoria = idCategoria; }
}
