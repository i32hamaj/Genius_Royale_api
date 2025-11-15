package com.geniusroyale.api.dto;

public class GameStartMessage {
    private String gameId;
    private String playerOneUsername; // <-- CAMPO NUEVO
    private String playerTwoUsername; // <-- CAMPO NUEVO
    private String opponentUsername; // Mantenemos este para el Toast

    public GameStartMessage(String gameId, String p1Username, String p2Username, String opponent) {
        this.gameId = gameId;
        this.playerOneUsername = p1Username;
        this.playerTwoUsername = p2Username;
        this.opponentUsername = opponent;
    }

    // Getters
    public String getGameId() { return gameId; }
    public String getPlayerOneUsername() { return playerOneUsername; }
    public String getPlayerTwoUsername() { return playerTwoUsername; }
    public String getOpponentUsername() { return opponentUsername; }
}