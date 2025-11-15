package com.geniusroyale.api.controllers;

import com.geniusroyale.api.dto.GameUpdateDTO;
import com.geniusroyale.api.dto.PlayerAnswerDTO;
import com.geniusroyale.api.models.Difficulty;
import com.geniusroyale.api.models.Game;
import com.geniusroyale.api.models.Question;
import com.geniusroyale.api.models.User;
import com.geniusroyale.api.repositories.GameRepository;
import com.geniusroyale.api.repositories.QuestionRepository;
import com.geniusroyale.api.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class GamePlayController {

    @Autowired private SimpMessagingTemplate messagingTemplate;
    @Autowired private GameRepository gameRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private QuestionRepository questionRepository;

    @MessageMapping("/game.answer")
    @Transactional // ¡Muy importante para esta lógica!
    public void handleAnswer(Principal principal, @Payload PlayerAnswerDTO answer) {

        String email = principal.getName();
        User player = userRepository.findByEmail(email).orElseThrow();
        Game game = gameRepository.findById(answer.getGameId()).orElseThrow();

        // Si el juego ya ha terminado, no hacer nada
        if ("FINISHED".equals(game.getGameState())) {
            return;
        }

        String playerOneTopic = "/topic/game.updates." + game.getPlayerOne().getUsername();
        String playerTwoTopic = "/topic/game.updates." + game.getPlayerTwo().getUsername();

        boolean isPlayerOne = game.getPlayerOne().getUsername().equals(player.getUsername());

        // 1. Guardar la respuesta de este jugador
        if (isPlayerOne) {
            // Comprobar si ya había respondido (para evitar dobles clics)
            if (game.getPlayerOneCurrentAnswer() != null) return;

            game.setPlayerOneCurrentAnswer(answer.getSelectedAnswer());

            // Avisar al J2 que J1 ha contestado
            GameUpdateDTO rivalUpdate = new GameUpdateDTO();
            rivalUpdate.setType("RIVAL_ANSWERED");
            rivalUpdate.setMessage("¡Tu rival ha contestado!");
            messagingTemplate.convertAndSend(playerTwoTopic, rivalUpdate);

        } else {
            // Comprobar si ya había respondido
            if (game.getPlayerTwoCurrentAnswer() != null) return;

            game.setPlayerTwoCurrentAnswer(answer.getSelectedAnswer());

            // Avisar al J1 que J2 ha contestado
            GameUpdateDTO rivalUpdate = new GameUpdateDTO();
            rivalUpdate.setType("RIVAL_ANSWERED");
            rivalUpdate.setMessage("¡Tu rival ha contestado!");
            messagingTemplate.convertAndSend(playerOneTopic, rivalUpdate);
        }

        // 2. Comprobar si AMBOS jugadores han respondido
        String p1Answer = game.getPlayerOneCurrentAnswer();
        String p2Answer = game.getPlayerTwoCurrentAnswer();

        if (p1Answer != null && p2Answer != null) {
            // ¡Ambos han respondido! Es hora de procesar la ronda
            processRound(game);
        }

        gameRepository.save(game); // Guardar la respuesta
    }

    /**
     * Este método se llama CUANDO AMBOS jugadores han respondido.
     */
    private void processRound(Game game) {
        String playerOneTopic = "/topic/game.updates." + game.getPlayerOne().getUsername();
        String playerTwoTopic = "/topic/game.updates." + game.getPlayerTwo().getUsername();

        String[] questionIds = game.getQuestionIds().split(",");
        int questionIndex = game.getCurrentQuestionIndex();
        Question question = questionRepository.findById(Integer.parseInt(questionIds[questionIndex])).orElseThrow();
        String correctAnswer = question.getCorrectAnswer();

        // 1. Calcular puntuaciones
        if (game.getPlayerOneCurrentAnswer().equals(correctAnswer)) {
            game.setPlayerOneScore(game.getPlayerOneScore() + getScore(question.getDifficultyLevel()));
        }
        if (game.getPlayerTwoCurrentAnswer().equals(correctAnswer)) {
            game.setPlayerTwoScore(game.getPlayerTwoScore() + getScore(question.getDifficultyLevel()));
        }

        // 2. Enviar el resultado de la ronda a AMBOS
        GameUpdateDTO roundResult = new GameUpdateDTO();
        roundResult.setType("ROUND_RESULT");
        roundResult.setCorrectAnswer(correctAnswer);
        roundResult.setPlayerOneScore(game.getPlayerOneScore());
        roundResult.setPlayerTwoScore(game.getPlayerTwoScore());
        messagingTemplate.convertAndSend(playerOneTopic, roundResult);
        messagingTemplate.convertAndSend(playerTwoTopic, roundResult);

        // 3. Preparar para la siguiente ronda
        game.setPlayerOneCurrentAnswer(null); // Limpiar respuestas
        game.setPlayerTwoCurrentAnswer(null);
        game.setCurrentQuestionIndex(questionIndex + 1); // Avanzar

        // 4. Comprobar si es el final de la partida
        if (game.getCurrentQuestionIndex() >= questionIds.length) {
            game.setGameState("FINISHED");
            String winner = (game.getPlayerOneScore() > game.getPlayerTwoScore()) ? game.getPlayerOne().getUsername() : game.getPlayerTwo().getUsername();
            if (game.getPlayerOneScore() == game.getPlayerTwoScore()) winner = "Empate";

            GameUpdateDTO gameOver = new GameUpdateDTO();
            gameOver.setType("GAME_OVER");
            gameOver.setWinnerUsername(winner);
            gameOver.setPlayerOneScore(game.getPlayerOneScore());
            gameOver.setPlayerTwoScore(game.getPlayerTwoScore());
            messagingTemplate.convertAndSend(playerOneTopic, gameOver);
            messagingTemplate.convertAndSend(playerTwoTopic, gameOver);
        }

        // gameRepository.save(game); // @Transactional se encarga de guardar al final del método
    }

    private int getScore(Difficulty difficulty) {
        switch (difficulty) {
            case Fácil: return 100;
            case Intermedia: return 200;
            case Difícil: return 300;
            default: return 0;
        }
    }
} // <-- ¡¡ESTA ES LA LLAVE QUE FALTABA!!