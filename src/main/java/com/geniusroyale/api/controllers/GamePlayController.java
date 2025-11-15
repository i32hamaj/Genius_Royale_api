package com.geniusroyale.api.controllers;

import com.geniusroyale.api.dto.GameUpdateDTO;
import com.geniusroyale.api.dto.PlayerAnswerDTO;
import com.geniusroyale.api.models.Game;
import com.geniusroyale.api.models.Question;
import com.geniusroyale.api.models.User;
import com.geniusroyale.api.models.Difficulty; // <-- ¡IMPORT AÑADIDO!
import com.geniusroyale.api.repositories.GameRepository;
import com.geniusroyale.api.repositories.QuestionRepository;
import com.geniusroyale.api.repositories.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class GamePlayController {

    @Autowired private SimpMessagingTemplate messagingTemplate;
    @Autowired private GameRepository gameRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private QuestionRepository questionRepository;

    private static final Map<String, String> playerOneAnswers = new ConcurrentHashMap<>();
    private static final Map<String, String> playerTwoAnswers = new ConcurrentHashMap<>();

    @MessageMapping("/game.answer")
    public void handleAnswer(Principal principal, @Payload PlayerAnswerDTO answer) {

        String email = principal.getName();
        User player = userRepository.findByEmail(email).orElseThrow();
        Game game = gameRepository.findById(answer.getGameId()).orElseThrow();

        System.out.println("PARTIDA: Respuesta recibida de " + player.getUsername() + " para la partida " + game.getId());

        boolean isPlayerOne = game.getPlayerOne().getUsername().equals(player.getUsername());
        String opponentUsername = (isPlayerOne) ? game.getPlayerTwo().getUsername() : game.getPlayerOne().getUsername();

        String rivalTopic = "/topic/game.updates." + opponentUsername;
        messagingTemplate.convertAndSend(rivalTopic,
                new GameUpdateDTO("RIVAL_ANSWERED", "¡Tu rival ha contestado!")
        );

        String[] questionIds = game.getQuestionIds().split(",");
        int questionIndex = game.getCurrentQuestionIndex();
        Question currentQuestion = questionRepository.findById(Integer.parseInt(questionIds[questionIndex])).orElseThrow();

        boolean isCorrect = answer.getSelectedAnswer().equals(currentQuestion.getCorrectAnswer());

        if (isCorrect) {
            int score = 0;

            // --- ¡CAMBIO AQUÍ! ---
            // Switch sobre el ENUM, sin comillas
            switch (currentQuestion.getDifficultyLevel()) {
                case Fácil: score = 100; break;
                case Intermedia: score = 200; break;
                case Difícil: score = 300; break;
            }
            // --- FIN DEL CAMBIO ---

            if (isPlayerOne) {
                game.setPlayerOneScore(game.getPlayerOneScore() + score);
            } else {
                game.setPlayerTwoScore(game.getPlayerTwoScore() + score);
            }
        }

        String playerOneTopic = "/topic/game.updates." + game.getPlayerOne().getUsername();
        String playerTwoTopic = "/topic/game.updates." + game.getPlayerTwo().getUsername();

        GameUpdateDTO resultMessage = new GameUpdateDTO(
                "QUESTION_RESULT",
                "La respuesta correcta era: " + currentQuestion.getCorrectAnswer()
        );

        messagingTemplate.convertAndSend(playerOneTopic, resultMessage);
        messagingTemplate.convertAndSend(playerTwoTopic, resultMessage);
    }
}