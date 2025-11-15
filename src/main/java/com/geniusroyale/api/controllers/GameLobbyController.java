package com.geniusroyale.api.controllers;

import com.geniusroyale.api.dto.GameStartMessage;
import com.geniusroyale.api.dto.LobbyJoinRequest;
import com.geniusroyale.api.models.Category;
import com.geniusroyale.api.models.Difficulty;
import com.geniusroyale.api.models.Game;
import com.geniusroyale.api.models.Question;
import com.geniusroyale.api.models.User;
import com.geniusroyale.api.repositories.CategoryRepository;
import com.geniusroyale.api.repositories.GameRepository;
import com.geniusroyale.api.repositories.QuestionRepository;
import com.geniusroyale.api.repositories.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional; // <-- Importar

import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Controller
public class GameLobbyController {

    @Autowired private SimpMessagingTemplate messagingTemplate;
    @Autowired private GameRepository gameRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private QuestionRepository questionRepository;

    private static final Map<String, Map<String, User>> categoryWaitingPools = new ConcurrentHashMap<>();

    @MessageMapping("/lobby.join")
    @Transactional // <-- Añadido por seguridad
    public void joinPublicLobby(Principal principal, @Payload LobbyJoinRequest request) {
        String email = principal.getName();
        User joiningPlayer = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + email));

        String categoryName = request.getCategoryName();
        System.out.println("LOBBY: " + joiningPlayer.getUsername() + " quiere unirse a [" + categoryName + "]");

        Map<String, User> waitingPool = categoryWaitingPools.computeIfAbsent(categoryName, k -> new ConcurrentHashMap<>());

        synchronized (waitingPool) {
            if (waitingPool.isEmpty()) {
                waitingPool.put(joiningPlayer.getUsername(), joiningPlayer);
                System.out.println("LOBBY: " + joiningPlayer.getUsername() + " puesto en espera en [" + categoryName + "]");
            } else {
                User playerOne = waitingPool.remove(waitingPool.keySet().iterator().next());
                User playerTwo = joiningPlayer;

                System.out.println("LOBBY: ¡Match encontrado en [" + categoryName + "]! " + playerOne.getUsername() + " vs " + playerTwo.getUsername());

                List<Question> gameQuestions = generateGameQuestions(categoryName);
                String questionIdList = gameQuestions.stream()
                        .map(q -> String.valueOf(q.getId()))
                        .collect(Collectors.joining(","));

                Game newGame = new Game();
                newGame.setId(UUID.randomUUID().toString());
                newGame.setPlayerOne(playerOne);
                newGame.setPlayerTwo(playerTwo);
                newGame.setGameState("IN_PROGRESS");
                newGame.setQuestionIds(questionIdList);

                if (!categoryName.equals("Cultura General")) {
                    categoryRepository.findByName(categoryName).ifPresent(newGame::setCategory);
                }

                gameRepository.save(newGame);

                String gameId = newGame.getId();
                String p1Username = playerOne.getUsername();
                String p2Username = playerTwo.getUsername();

                // --- ¡CAMBIO AQUÍ! ---
                // Enviar la información completa de la partida a ambos jugadores
                messagingTemplate.convertAndSend("/topic/game.start." + p1Username,
                        new GameStartMessage(gameId, p1Username, p2Username, p2Username)); // El rival de P1 es P2

                messagingTemplate.convertAndSend("/topic/game.start." + p2Username,
                        new GameStartMessage(gameId, p1Username, p2Username, p1Username)); // El rival de P2 es P1
            }
        }
    }

    private List<Question> generateGameQuestions(String categoryName) {

        List<Question> easy, medium, hard;

        if (categoryName.equals("Cultura General")) {
            easy = questionRepository.findAllByDifficultyLevel(Difficulty.Fácil);
            medium = questionRepository.findAllByDifficultyLevel(Difficulty.Intermedia);
            hard = questionRepository.findAllByDifficultyLevel(Difficulty.Difícil);
        } else {
            Category category = categoryRepository.findByName(categoryName)
                    .orElseThrow(() -> new RuntimeException("Categoría no encontrada: " + categoryName));
            easy = questionRepository.findByCategoryAndDifficultyLevel(category, Difficulty.Fácil);
            medium = questionRepository.findByCategoryAndDifficultyLevel(category, Difficulty.Intermedia);
            hard = questionRepository.findByCategoryAndDifficultyLevel(category, Difficulty.Difícil);
        }

        Collections.shuffle(easy);
        Collections.shuffle(medium);
        Collections.shuffle(hard);

        List<Question> gameQuestions = Stream.concat(
                easy.stream().limit(5),
                Stream.concat(
                        medium.stream().limit(5),
                        hard.stream().limit(5)
                )
        ).collect(Collectors.toList());

        Collections.shuffle(gameQuestions);
        return gameQuestions;
    }
}