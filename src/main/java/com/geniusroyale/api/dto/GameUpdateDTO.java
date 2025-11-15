package com.geniusroyale.api.dto;

// Un DTO genérico para todos los mensajes del servidor a la app
public class GameUpdateDTO {
    private String type; // "RIVAL_ANSWERED", "QUESTION_RESULT", "GAME_OVER"
    private String message;
    // Podríamos añadir más datos (ej. puntuaciones, respuesta correcta)

    public GameUpdateDTO(String type, String message) {
        this.type = type;
        this.message = message;
    }

    // Getters y Setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}