package co.edu.futdata.domain.entities;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Deportista registrado en el club.
 *
 * <p>{@link #getCategoria() categoria} e {@link #getIdCategoria() idCategoria} son la categoría <b>calculada al
 * leer</b> por año de nacimiento (el DAO las obtiene con un join por rango de edad); nunca vienen de la columna
 * heredada {@code deportista.id_categoria}, que ningún código lee ni escribe. Son {@code null} si el deportista
 * queda fuera de 4 a 16 años.
 */
public class Deportista {

    private UUID id;
    private String nombre;
    private String apellido;
    private String tipoDocumento;
    private String documento;
    private LocalDate fechaNacimiento;
    private String telefono;
    private String direccion;
    /** Id de la categoría calculada al leer (no la columna deportista.id_categoria). */
    private UUID idCategoria;
    /** Nombre de la categoría calculada al leer. */
    private String categoria;
    /** mañana o tarde (migración 008); solo en Cachorros y Semillero. */
    private String grupo;
    /** Ruta de la foto en el bucket privado {@code fotos} (nunca sale por la API: se entrega una URL firmada). */
    private String foto;

    public String getFoto() { return foto; }
    public void setFoto(String foto) { this.foto = foto; }
    /** Posición de juego, opcional (texto libre, máximo 50). */
    private String posicion;

    public String getPosicion() { return posicion; }
    public void setPosicion(String posicion) { this.posicion = posicion; }
    /** Columna deportista.activo (migración 008). */
    private boolean activo = true;
    /** COMPLETA o PENDIENTE, calculado desde los documentos (sin columna); null si no se pidió. */
    private String estadoInscripcion;

    public String getEstadoInscripcion() { return estadoInscripcion; }
    public void setEstadoInscripcion(String estadoInscripcion) { this.estadoInscripcion = estadoInscripcion; }

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
    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public String getGrupo() { return grupo; }
    public void setGrupo(String grupo) { this.grupo = grupo; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
