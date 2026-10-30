package co.edu.futdata.domain.entities;

import java.time.Instant;
import java.util.UUID;

public class CodigoVerificacion {
    private UUID id;
    private UUID usuarioId;
    private String codigoHash;
    private Instant expiraEn;
    private int intentos;
    private Instant bloqueadoHasta;
    private Instant ultimoEnvio;
    private boolean usado;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUsuarioId() { return usuarioId; }
    public void setUsuarioId(UUID usuarioId) { this.usuarioId = usuarioId; }
    public String getCodigoHash() { return codigoHash; }
    public void setCodigoHash(String codigoHash) { this.codigoHash = codigoHash; }
    public Instant getExpiraEn() { return expiraEn; }
    public void setExpiraEn(Instant expiraEn) { this.expiraEn = expiraEn; }
    public int getIntentos() { return intentos; }
    public void setIntentos(int intentos) { this.intentos = intentos; }
    public Instant getBloqueadoHasta() { return bloqueadoHasta; }
    public void setBloqueadoHasta(Instant bloqueadoHasta) { this.bloqueadoHasta = bloqueadoHasta; }
    public Instant getUltimoEnvio() { return ultimoEnvio; }
    public void setUltimoEnvio(Instant ultimoEnvio) { this.ultimoEnvio = ultimoEnvio; }
    public boolean isUsado() { return usado; }
    public void setUsado(boolean usado) { this.usado = usado; }
}
