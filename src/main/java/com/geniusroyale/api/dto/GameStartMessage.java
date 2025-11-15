package com.geniusroyale.api.dto;

public class GameStartMessage {
    private String gameId;
    private String opponentUsername;

    public GameStartMessage(String gameId, String opponentUsername) {
        this.gameId = gameId;
        this.opponentUsername = opponentUsername;
    }

    // Getters
    public String getGameId() { return gameId; }
    public String getOpponentUsername() { return opponentUsername; }
}