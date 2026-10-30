package co.edu.futdata.domain.entities;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Entrenador del club (tabla {@code entrenador}, diccionario de datos 2.3).
 *
 * <p>{@code categorias} son los nombres de las categorías que dirige ({@code categoria.id_entrenador}), en el
 * orden oficial. No se guardan en esta tabla: el DAO las lee de {@code categoria}. Las columnas
 * {@code documentos} e {@code id_usuario} no se manejan en HU-025 (la cuenta de usuario es de CU-06).
 *
 * <p>{@code activo} es {@code false} si el entrenador fue desactivado (migración 004, decisión D8): conserva su
 * historial, pero no dirige categorías ni recibe asignaciones nuevas.
 */
public class Entrenador {

    private UUID id;
    private String nombre;
    private String apellido;
    private String tipoDocumento;
    private String documento;
    private LocalDate fechaNacimiento;
    private String telefono;
    private String correo;
    private String direccion;
    private String horarioTrabajo;
    private String especialidad;
    private List<String> categorias = List.of();
    private boolean activo = true;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getApellido() {
        return apellido;
    }

    public void setApellido(String apellido) {
        this.apellido = apellido;
    }

    /** Código de la base de datos: {@code CC}, {@code TI}, {@code CE} o {@code PASAPORTE}. */
    public String getTipoDocumento() {
        return tipoDocumento;
    }

    public void setTipoDocumento(String tipoDocumento) {
        this.tipoDocumento = tipoDocumento;
    }

    /** Número del documento ({@code documento_identidad}). */
    public String getDocumento() {
        return documento;
    }

    public void setDocumento(String documento) {
        this.documento = documento;
    }

    public LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }

    public void setFechaNacimiento(LocalDate fechaNacimiento) {
        this.fechaNacimiento = fechaNacimiento;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public String getHorarioTrabajo() {
        return horarioTrabajo;
    }

    public void setHorarioTrabajo(String horarioTrabajo) {
        this.horarioTrabajo = horarioTrabajo;
    }

    public String getEspecialidad() {
        return especialidad;
    }

    public void setEspecialidad(String especialidad) {
        this.especialidad = especialidad;
    }

    public List<String> getCategorias() {
        return categorias;
    }

    public void setCategorias(List<String> categorias) {
        this.categorias = categorias == null ? List.of() : List.copyOf(categorias);
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}
