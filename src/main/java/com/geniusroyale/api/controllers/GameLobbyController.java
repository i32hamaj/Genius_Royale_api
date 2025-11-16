package com.geniusroyale.api.controllers;

// --- Imports de DTOs ---
import com.geniusroyale.api.dto.GameStartMessage;
import com.geniusroyale.api.dto.LobbyJoinRequest;
import com.geniusroyale.api.dto.InviteRequestDTO;
import com.geniusroyale.api.dto.InviteNotificationDTO;
import com.geniusroyale.api.dto.InviteAcceptDTO;

// --- Imports de Modelos (Entidades) ---
import com.geniusroyale.api.models.Category;
import com.geniusroyale.api.models.Difficulty;
import com.geniusroyale.api.models.Game;
import com.geniusroyale.api.models.Question;
import com.geniusroyale.api.models.User;
import com.geniusroyale.api.models.GameInvite;

// --- Imports de Repositorios ---
import com.geniusroyale.api.repositories.CategoryRepository;
import com.geniusroyale.api.repositories.GameRepository;
import com.geniusroyale.api.repositories.QuestionRepository;
import com.geniusroyale.api.repositories.UserRepository;
import com.geniusroyale.api.repositories.GameInviteRepository;

// --- Imports de Spring y Java ---
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Controller
public class GameLobbyController {

    // --- Repositorios y Servicios ---
    @Autowired private SimpMessagingTemplate messagingTemplate;
    @Autowired private GameRepository gameRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private QuestionRepository questionRepository;
    @Autowired private GameInviteRepository inviteRepository;

    // Mapa de salas de espera (una por cada categoría)
    private static final Map<String, Map<String, User>> categoryWaitingPools = new ConcurrentHashMap<>();

    /**
     * Se activa cuando un jugador envía un mensaje a "/app/lobby.join"
     * (Matchmaking público)
     */
    @MessageMapping("/lobby.join")
    @Transactional
    public void joinPublicLobby(Principal principal, @Payload LobbyJoinRequest request) {
        String email = principal.getName();
        User joiningPlayer = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + email));

        String categoryName = request.getCategoryName();
        System.out.println("LOBBY: " + joiningPlayer.getUsername() + " quiere unirse a [" + categoryName + "]");

        Map<String, User> waitingPool = categoryWaitingPools.computeIfAbsent(categoryName, k -> new ConcurrentHashMap<>());

        synchronized (waitingPool) {
            if (waitingPool.isEmpty()) {
                // No hay nadie, poner en espera
                waitingPool.put(joiningPlayer.getUsername(), joiningPlayer);
                System.out.println("LOBBY: " + joiningPlayer.getUsername() + " puesto en espera en [" + categoryName + "]");
            } else {
                // ¡Hay alguien! Emparejar
                User playerOne = waitingPool.remove(waitingPool.keySet().iterator().next());
                User playerTwo = joiningPlayer;

                System.out.println("LOBBY: ¡Match encontrado en [" + categoryName + "]! " + playerOne.getUsername() + " vs " + playerTwo.getUsername());

                // Generar preguntas y crear la partida
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

                // Avisar a ambos jugadores
                messagingTemplate.convertAndSend("/topic/game.start." + p1Username,
                        new GameStartMessage(gameId, p1Username, p2Username, p2Username));

                messagingTemplate.convertAndSend("/topic/game.start." + p2Username,
                        new GameStartMessage(gameId, p1Username, p2Username, p1Username));
            }
        }
    }

    /**
     * Se activa cuando un jugador envía un mensaje a "/app/game.invite"
     * (Invitación privada)
     */
    @MessageMapping("/game.invite")
    @Transactional
    public void invitePlayer(Principal principal, @Payload InviteRequestDTO request) {
        User sender = userRepository.findByEmail(principal.getName()).orElseThrow();
        User receiver = userRepository.findByUsername(request.getReceiverUsername()).orElseThrow();

        System.out.println("INVITE: " + sender.getUsername() + " está invitando a " + receiver.getUsername());

        GameInvite invite = new GameInvite();
        invite.setSender(sender);
        invite.setReceiver(receiver);

        // Guardar la categoría de la invitación
        if (!request.getCategoryName().equals("Cultura General")) {
            Category category = categoryRepository.findByName(request.getCategoryName()).orElseThrow();
            invite.setCategory(category);
        }

        GameInvite savedInvite = inviteRepository.save(invite);

        // Enviar la notificación al canal personal del RECEPTOR
        String receiverTopic = "/topic/invites." + receiver.getUsername();
        messagingTemplate.convertAndSend(receiverTopic, new InviteNotificationDTO(savedInvite));
    }

    /**
     * Se activa cuando un jugador envía un mensaje a "/app/invite.accept"
     * (Aceptar invitación privada)
     */
    @MessageMapping("/invite.accept")
    @Transactional
    public void acceptInvite(Principal principal, @Payload InviteAcceptDTO request) {
        User receiver = userRepository.findByEmail(principal.getName()).orElseThrow();
        GameInvite invite = inviteRepository.findById(request.getInviteId()).orElseThrow();

        // Verificación de seguridad
        if (!invite.getReceiver().getUsername().equals(receiver.getUsername()) || !invite.getStatus().equals("PENDING")) {
            System.err.println("INVITE_ACCEPT: ¡Intento de aceptar invitación fallido!");
            return;
        }

        invite.setStatus("ACCEPTED");
        inviteRepository.save(invite);

        User sender = invite.getSender();
        String categoryName = (invite.getCategory() != null) ? invite.getCategory().getName() : "Cultura General";

        System.out.println("INVITE_ACCEPT: " + receiver.getUsername() + " aceptó la invitación de " + sender.getUsername());

        // 1. Crear la partida
        List<Question> gameQuestions = generateGameQuestions(categoryName);
        String questionIdList = gameQuestions.stream()
                .map(q -> String.valueOf(q.getId()))
                .collect(Collectors.joining(","));

        Game newGame = new Game();
        newGame.setId(UUID.randomUUID().toString());
        newGame.setPlayerOne(sender); // El que invita es J1
        newGame.setPlayerTwo(receiver); // El que acepta es J2
        newGame.setGameState("IN_PROGRESS");
        newGame.setQuestionIds(questionIdList);
        newGame.setCategory(invite.getCategory());
        gameRepository.save(newGame);

        // 2. Avisar a AMBOS jugadores que la partida empieza
        String gameId = newGame.getId();
        String p1Username = sender.getUsername();
        String p2Username = receiver.getUsername();

        // Avisar al J1 (que está esperando en GameModeActivity)
        messagingTemplate.convertAndSend("/topic/game.start." + p1Username,
                new GameStartMessage(gameId, p1Username, p2Username, p2Username));

        // Avisar al J2 (que está en HomeActivity)
        messagingTemplate.convertAndSend("/topic/game.start." + p2Username,
                new GameStartMessage(gameId, p1Username, p2Username, p1Username));
    }

    /**
     * Método ayudante para generar la lista de 15 preguntas
     */
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

        // Unir 5 de cada una
        List<Question> gameQuestions = Stream.concat(
                easy.stream().limit(5),
                Stream.concat(
                        medium.stream().limit(5),
                        hard.stream().limit(5)
                )
        ).collect(Collectors.toList());

        Collections.shuffle(gameQuestions);

        return gameQuestions; // <-- ¡Aquí está el return que faltaba!
    }
}