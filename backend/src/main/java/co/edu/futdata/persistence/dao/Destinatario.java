package co.edu.futdata.persistence.dao;

/** A quien se le envía un aviso: su correo y su nombre completo (puede ser {@code null}). Tiene la forma que espera n8n. */
public record Destinatario(String correo, String nombre) {
}
