package co.edu.futdata.domain.entities;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Una franja del horario fijo del club (tabla {@code franja_horario}, migración 007): una categoría entrena un día
 * de la semana, de una hora a otra, en una sede.
 *
 * <p>{@code categoria}, {@code entrenador} y {@code entrenadorActivo} no son columnas de la tabla: el DAO los lee de
 * {@code categoria} y {@code entrenador} al consultar, y los ignora al escribir.
 */
public class FranjaHorario {

    private UUID id;
    private UUID idCategoria;
    private String categoria;
    private int dia;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private String sede;
    private UUID idEntrenador;
    private String entrenador;
    private Boolean entrenadorActivo;
    private String grupo;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getIdCategoria() {
        return idCategoria;
    }

    public void setIdCategoria(UUID idCategoria) {
        this.idCategoria = idCategoria;
    }

    /** Nombre de la categoría (solo lectura). */
    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    /** 1 = lunes ... 6 = sábado. */
    public int getDia() {
        return dia;
    }

    public void setDia(int dia) {
        this.dia = dia;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public void setHoraInicio(LocalTime horaInicio) {
        this.horaInicio = horaInicio;
    }

    public LocalTime getHoraFin() {
        return horaFin;
    }

    public void setHoraFin(LocalTime horaFin) {
        this.horaFin = horaFin;
    }

    /** {@code Horizontes} o {@code UVA El Paraíso}. */
    public String getSede() {
        return sede;
    }

    public void setSede(String sede) {
        this.sede = sede;
    }

    /** {@code null} = "por turnos": no hay entrenador fijo. */
    public UUID getIdEntrenador() {
        return idEntrenador;
    }

    public void setIdEntrenador(UUID idEntrenador) {
        this.idEntrenador = idEntrenador;
    }

    /** Nombre y apellido del entrenador (solo lectura); {@code null} si no tiene. */
    public String getEntrenador() {
        return entrenador;
    }

    public void setEntrenador(String entrenador) {
        this.entrenador = entrenador;
    }

    /** Si el entrenador está activo (solo lectura); {@code null} si no tiene. */
    public Boolean getEntrenadorActivo() {
        return entrenadorActivo;
    }

    public void setEntrenadorActivo(Boolean entrenadorActivo) {
        this.entrenadorActivo = entrenadorActivo;
    }

    /** {@code mañana}, {@code tarde} o {@code null}. */
    public String getGrupo() {
        return grupo;
    }

    public void setGrupo(String grupo) {
        this.grupo = grupo;
    }
}
