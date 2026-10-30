package co.edu.futdata.domain.entities;

import java.util.UUID;

/**
 * Categoría por edad del club (tabla {@code categoria}, diccionario de datos 2.14). Según la decisión D1, la
 * dirige como máximo un entrenador. {@code idEntrenador} es {@code null} si todavía no tiene (decisión D7).
 */
public class Categoria {

    private UUID id;
    private String nombre;
    private int edadMinima;
    private int edadMaxima;
    private UUID idEntrenador;

    public Categoria() {
    }

    public Categoria(UUID id, String nombre, int edadMinima, int edadMaxima, UUID idEntrenador) {
        this.id = id;
        this.nombre = nombre;
        this.edadMinima = edadMinima;
        this.edadMaxima = edadMaxima;
        this.idEntrenador = idEntrenador;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    /** {@code Sub-8}, {@code Sub-10}, {@code Sub-12}, {@code Sub-14} o {@code Sub-16}. */
    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public int getEdadMinima() {
        return edadMinima;
    }

    public void setEdadMinima(int edadMinima) {
        this.edadMinima = edadMinima;
    }

    public int getEdadMaxima() {
        return edadMaxima;
    }

    public void setEdadMaxima(int edadMaxima) {
        this.edadMaxima = edadMaxima;
    }

    /** El entrenador que la dirige, o {@code null} si no tiene. */
    public UUID getIdEntrenador() {
        return idEntrenador;
    }

    public void setIdEntrenador(UUID idEntrenador) {
        this.idEntrenador = idEntrenador;
    }
}
