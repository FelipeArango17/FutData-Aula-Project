package co.edu.futdata.api.dto;

public record Codigo2FAResponseDTO(String correoEnmascarado, long vigenciaSegundos, long reenvioDisponibleEn) {
}
