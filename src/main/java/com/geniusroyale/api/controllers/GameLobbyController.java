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

    // Un mapa de salas de espera, una por cada categoría
    // (String = categoryName, Map = (username, User))
    private static final Map<String, Map<String, User>> categoryWaitingPools = new ConcurrentHashMap<>();

    @MessageMapping("/lobby.join")
    public void joinPublicLobby(Principal principal, @Payload LobbyJoinRequest request) {
        String email = principal.getName();
        User joiningPlayer = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + email));

        String categoryName = request.getCategoryName();
        System.out.println("LOBBY: " + joiningPlayer.getUsername() + " quiere unirse a [" + categoryName + "]");

        // Obtener (o crear) la sala de espera para ESTA categoría
        Map<String, User> waitingPool = categoryWaitingPools.computeIfAbsent(categoryName, k -> new ConcurrentHashMap<>());

        synchronized (waitingPool) {
            if (waitingPool.isEmpty()) {
                // No hay nadie en esta categoría, te ponemos en espera
                waitingPool.put(joiningPlayer.getUsername(), joiningPlayer);
                System.out.println("LOBBY: " + joiningPlayer.getUsername() + " puesto en espera en [" + categoryName + "]");
            } else {
                // ¡Hay alguien! Los emparejamos
                User playerOne = waitingPool.remove(waitingPool.keySet().iterator().next());
                User playerTwo = joiningPlayer;

                System.out.println("LOBBY: ¡Match encontrado en [" + categoryName + "]! " + playerOne.getUsername() + " vs " + playerTwo.getUsername());

                // Generar la lista de preguntas
                List<Question> gameQuestions = generateGameQuestions(categoryName);

                // Convertir la lista de IDs a un String (ej: "1,5,22,10,30...")
                String questionIdList = gameQuestions.stream()
                        .map(q -> String.valueOf(q.getId()))
                        .collect(Collectors.joining(","));

                // Crear la partida en la BBDD
                Game newGame = new Game();
                newGame.setId(UUID.randomUUID().toString());
                newGame.setPlayerOne(playerOne);
                newGame.setPlayerTwo(playerTwo);
                newGame.setGameState("IN_PROGRESS");
                newGame.setQuestionIds(questionIdList); // Guardamos la lista de preguntas

                // Guardar la categoría (si no es "Cultura General")
                if (!categoryName.equals("Cultura General")) {
                    categoryRepository.findByName(categoryName).ifPresent(newGame::setCategory);
                }

                gameRepository.save(newGame);

                // Avisar a ambos jugadores
                String gameId = newGame.getId();
                messagingTemplate.convertAndSend("/topic/game.start." + playerOne.getUsername(), new GameStartMessage(gameId, playerTwo.getUsername()));
                messagingTemplate.convertAndSend("/topic/game.start." + playerTwo.getUsername(), new GameStartMessage(gameId, playerOne.getUsername()));
            }
        }
    }

    // Lógica para coger las 5 Fáciles, 5 Intermedias, 5 Difíciles de la BBDD
    private List<Question> generateGameQuestions(String categoryName) {

        List<Question> easy, medium, hard;

        if (categoryName.equals("Cultura General")) {
            // Coger de todas las categorías
            easy = questionRepository.findAllByDifficultyLevel(Difficulty.Fácil);
            medium = questionRepository.findAllByDifficultyLevel(Difficulty.Intermedia);
            hard = questionRepository.findAllByDifficultyLevel(Difficulty.Difícil);
        } else {
            // Coger solo de la categoría específica
            Category category = categoryRepository.findByName(categoryName)
                    .orElseThrow(() -> new RuntimeException("Categoría no encontrada: " + categoryName));

            easy = questionRepository.findByCategoryAndDifficultyLevel(category, Difficulty.Fácil);
            medium = questionRepository.findByCategoryAndDifficultyLevel(category, Difficulty.Intermedia);
            hard = questionRepository.findByCategoryAndDifficultyLevel(category, Difficulty.Difícil);
        }

        // Mezclarlas por separado
        Collections.shuffle(easy);
        Collections.shuffle(medium);
        Collections.shuffle(hard);

        // Unir 5 de cada una
        List<Question> gameQuestions = Stream.concat(
                easy.stream().limit(5),
                Stream.concat(
                        medium.stream().limit(5),
                        hard.stream().limit(5)
                )
        ).collect(Collectors.toList());

        // Mezclar la lista final
        Collections.shuffle(gameQuestions);
        return gameQuestions;
    }
}