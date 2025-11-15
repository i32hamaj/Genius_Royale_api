package com.geniusroyale.api.dto;

// DTO genérico para todos los mensajes del servidor a la app
public class GameUpdateDTO {
    private String type; // "RIVAL_ANSWERED", "ROUND_RESULT", "GAME_OVER"

    // Campos opcionales (pueden ser null)
    private String message;
    private String correctAnswer;
    private int playerOneScore;
    private int playerTwoScore;
    private String winnerUsername;

    // Solo el constructor por defecto
    public GameUpdateDTO() {}

    // Getters y Setters para todos los campos
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }
    public int getPlayerOneScore() { return playerOneScore; }
    public void setPlayerOneScore(int playerOneScore) { this.playerOneScore = playerOneScore; }
    public int getPlayerTwoScore() { return playerTwoScore; }
    public void setPlayerTwoScore(int playerTwoScore) { this.playerTwoScore = playerTwoScore; }
    public String getWinnerUsername() { return winnerUsername; }
    public void setWinnerUsername(String winnerUsername) { this.winnerUsername = winnerUsername; }
}