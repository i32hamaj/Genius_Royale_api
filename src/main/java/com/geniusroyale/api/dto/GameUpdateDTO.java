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

    // Constructor para RIVAL_ANSWERED
    public GameUpdateDTO(String type, String message) {
        this.type = type;
        this.message = message;
    }

    // Constructor para ROUND_RESULT
    public GameUpdateDTO(String type, String correctAnswer, int p1Score, int p2Score) {
        this.type = type;
        this.correctAnswer = correctAnswer;
        this.playerOneScore = p1Score;
        this.playerTwoScore = p2Score;
    }

    // Constructor para GAME_OVER
    public GameUpdateDTO(String type, String winnerUsername, int p1Score, int p2Score) {
        this.type = type;
        this.winnerUsername = winnerUsername;
        this.playerOneScore = p1Score;
        this.playerTwoScore = p2Score;
    }

    // Getters
    public String getType() { return type; }
    public String getMessage() { return message; }
    public String getCorrectAnswer() { return correctAnswer; }
    public int getPlayerOneScore() { return playerOneScore; }
    public int getPlayerTwoScore() { return playerTwoScore; }
    public String getWinnerUsername() { return winnerUsername; }
}