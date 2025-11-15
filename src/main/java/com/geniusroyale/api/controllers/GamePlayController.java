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

import java.security.Principal;

@Controller
public class GamePlayController {

    @Autowired private SimpMessagingTemplate messagingTemplate;
    @Autowired private GameRepository gameRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private QuestionRepository questionRepository;

    @MessageMapping("/game.answer")
    public synchronized void handleAnswer(Principal principal, @Payload PlayerAnswerDTO answer) {

        String email = principal.getName();
        User player = userRepository.findByEmail(email).orElseThrow();
        Game game = gameRepository.findById(answer.getGameId()).orElseThrow();

        String playerOneTopic = "/topic/game.updates." + game.getPlayerOne().getUsername();
        String playerTwoTopic = "/topic/game.updates." + game.getPlayerTwo().getUsername();

        boolean isPlayerOne = game.getPlayerOne().getUsername().equals(player.getUsername());

        // 1. Guardar la respuesta de este jugador
        if (isPlayerOne) {
            game.setPlayerOneCurrentAnswer(answer.getSelectedAnswer());
            // Avisar al J2 que J1 ha contestado
            messagingTemplate.convertAndSend(playerTwoTopic, new GameUpdateDTO("RIVAL_ANSWERED", "Tu rival ha contestado"));
        } else {
            game.setPlayerTwoCurrentAnswer(answer.getSelectedAnswer());
            // Avisar al J1 que J2 ha contestado
            messagingTemplate.convertAndSend(playerOneTopic, new GameUpdateDTO("RIVAL_ANSWERED", "Tu rival ha contestado"));
        }

        gameRepository.save(game); // Guardar esta respuesta

        // 2. Comprobar si AMBOS jugadores han respondido
        String p1Answer = game.getPlayerOneCurrentAnswer();
        String p2Answer = game.getPlayerTwoCurrentAnswer();

        if (p1Answer != null && p2Answer != null) {
            // ¡Ambos han respondido! Es hora de calcular

            // 3. Coger la pregunta y la respuesta correcta
            String[] questionIds = game.getQuestionIds().split(",");
            int questionIndex = game.getCurrentQuestionIndex();
            Question question = questionRepository.findById(Integer.parseInt(questionIds[questionIndex])).orElseThrow();
            String correctAnswer = question.getCorrectAnswer();

            // 4. Calcular puntuaciones
            if (p1Answer.equals(correctAnswer)) {
                game.setPlayerOneScore(game.getPlayerOneScore() + getScore(question.getDifficultyLevel()));
            }
            if (p2Answer.equals(correctAnswer)) {
                game.setPlayerTwoScore(game.getPlayerTwoScore() + getScore(question.getDifficultyLevel()));
            }

            // 5. Enviar el resultado de la ronda a AMBOS
            GameUpdateDTO roundResult = new GameUpdateDTO("ROUND_RESULT", correctAnswer, game.getPlayerOneScore(), game.getPlayerTwoScore());
            messagingTemplate.convertAndSend(playerOneTopic, roundResult);
            messagingTemplate.convertAndSend(playerTwoTopic, roundResult);

            // 6. Preparar para la siguiente ronda
            game.setPlayerOneCurrentAnswer(null); // Limpiar respuestas
            game.setPlayerTwoCurrentAnswer(null);
            game.setCurrentQuestionIndex(questionIndex + 1); // Avanzar

            // 7. Comprobar si es el final de la partida
            if (game.getCurrentQuestionIndex() >= questionIds.length) {
                // ¡JUEGO TERMINADO!
                game.setGameState("FINISHED");
                String winner = (game.getPlayerOneScore() > game.getPlayerTwoScore()) ? game.getPlayerOne().getUsername() : game.getPlayerTwo().getUsername();
                if (game.getPlayerOneScore() == game.getPlayerTwoScore()) winner = "Empate";

                GameUpdateDTO gameOver = new GameUpdateDTO("GAME_OVER", winner, game.getPlayerOneScore(), game.getPlayerTwoScore());
                messagingTemplate.convertAndSend(playerOneTopic, gameOver);
                messagingTemplate.convertAndSend(playerTwoTopic, gameOver);
            }

            gameRepository.save(game);
        }
    }

    // Método ayudante para la puntuación
    private int getScore(Difficulty difficulty) {
        switch (difficulty) {
            case Fácil: return 100;
            case Intermedia: return 200;
            case Difícil: return 300;
            default: return 0;
        }
    }
}