package com.chesslan.game.service.impl;

import com.chesslan.game.common.exception.ApiException;
import com.chesslan.game.common.exception.ErrorCode;
import com.chesslan.game.infrastructure.aram.AramMatchState;
import com.chesslan.game.infrastructure.aram.AramMoveResult;
import com.chesslan.game.infrastructure.aram.AramRulesEngine;
import com.chesslan.game.infrastructure.aram.AramStateCodec;
import com.chesslan.game.infrastructure.aram.AramStateFactory;
import com.chesslan.game.infrastructure.chess.ChessMoveCommand;
import com.chesslan.game.infrastructure.chess.ChessMoveRecord;
import com.chesslan.game.infrastructure.chess.ChessMoveResult;
import com.chesslan.game.infrastructure.chess.ChessRulesEngine;
import com.chesslan.game.mapper.GameMapper;
import com.chesslan.game.model.dto.match.MatchResponseDTO;
import com.chesslan.game.model.entity.MatchEntity;
import com.chesslan.game.model.entity.GameMode;
import com.chesslan.game.model.entity.MatchMoveEntity;
import com.chesslan.game.model.entity.MatchStatus;
import com.chesslan.game.model.entity.MatchTerminationReason;
import com.chesslan.game.model.entity.RoomEntity;
import com.chesslan.game.model.entity.RoomStatus;
import com.chesslan.game.model.entity.UserEntity;
import com.chesslan.game.repository.MatchMoveRepository;
import com.chesslan.game.repository.MatchRepository;
import com.chesslan.game.repository.RoomRepository;
import com.chesslan.game.repository.UserRepository;
import com.chesslan.game.service.interfaces.MatchService;
import com.chesslan.game.service.interfaces.RewardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class MatchServiceImpl implements MatchService {
    private final MatchRepository matchRepository;
    private final MatchMoveRepository matchMoveRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final ChessRulesEngine chessRulesEngine;
    private final AramRulesEngine aramRulesEngine;
    private final AramStateFactory aramStateFactory;
    private final AramStateCodec aramStateCodec;
    private final EloCalculator eloCalculator;
    private final RewardService rewardService;
    private final GameMapper mapper;
    private final ConcurrentHashMap<String, String> drawOffers = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public Map<String, Object> startMatch(String roomCode) {
        return startMatch(roomCode, null);
    }

    @Override
    @Transactional
    public Map<String, Object> startMatch(String roomCode, String gameMode) {
        RoomEntity room = requireRoomForUpdate(roomCode);
        requireMatchingMode(room.getGameMode(), gameMode);
        if (room.getGuest() == null) {
            throw new ApiException(ErrorCode.ROOM_NOT_JOINABLE, "Two players are required to start");
        }
        var existing = matchRepository.findByRoomRoomCodeIgnoreCase(roomCode);
        if (existing.isPresent()) {
            return gameStartPayload(existing.get());
        }

        MatchEntity match = new MatchEntity();
        match.setRoom(room);
        match.setWhitePlayer(room.getHost());
        match.setBlackPlayer(room.getGuest());
        match.setWhiteEloBefore(room.getHost().getElo());
        match.setBlackEloBefore(room.getGuest().getElo());
        match.setCurrentFen(chessRulesEngine.initialFen());
        match.setGameMode(room.getGameMode());
        if (match.getGameMode() == GameMode.ARAM) {
            String seed = UUID.randomUUID().toString();
            match.setAramSeed(seed);
            match.setAramState(aramStateCodec.encode(aramStateFactory.create(seed)));
        }
        match.setMoveCount(0);
        match.setStartedAt(LocalDateTime.now());
        match.setStatus(MatchStatus.ACTIVE);
        match.setTerminationReason(MatchTerminationReason.NONE);
        room.setStatus(RoomStatus.PLAYING);
        roomRepository.save(room);
        return gameStartPayload(matchRepository.save(match));
    }

    @Override
    @Transactional
    public Map<String, Object> submitMove(String username,
                                          String roomCode,
                                          String requestId,
                                          String from,
                                          String to,
                                          String promotion) {
        return submitMove(username, roomCode, requestId, from, to, promotion, null);
    }

    @Override
    @Transactional
    public Map<String, Object> submitMove(String username,
                                          String roomCode,
                                          String requestId,
                                          String from,
                                          String to,
                                          String promotion,
                                          String gameMode) {
        if (requestId == null || requestId.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "requestId is required");
        }
        MatchEntity match = requireActiveMatchForUpdate(roomCode);
        requireMatchingMode(match.getGameMode(), gameMode);
        UserEntity player = requireParticipant(match, username);

        var duplicate = matchMoveRepository.findByMatchIdAndRequestId(match.getId(), requestId);
        if (duplicate.isPresent()) {
            return acceptedMovePayload(match, duplicate.get(), false);
        }

        boolean whiteTurn = sideToMove(match.getCurrentFen()).equals("WHITE");
        if (whiteTurn != match.getWhitePlayer().getId().equals(player.getId())) {
            throw new ApiException(ErrorCode.NOT_YOUR_TURN);
        }

        List<MatchMoveEntity> previous = matchMoveRepository.findAllByMatchIdOrderByMoveNumberAsc(match.getId());
        MoveOutcome result = applyMove(match, previous, new ChessMoveCommand(from, to, promotion));

        MatchMoveEntity move = new MatchMoveEntity();
        move.setMatch(match);
        move.setPlayer(player);
        move.setMoveNumber(match.getMoveCount() + 1);
        move.setRequestId(requestId);
        move.setFromSquare(from.toLowerCase());
        move.setToSquare(to.toLowerCase());
        move.setPromotion(normalizeNullable(promotion));
        move.setNotation(result.notation());
        move.setFenAfter(result.fen());
        matchMoveRepository.save(move);

        match.setMoveCount(move.getMoveNumber());
        match.setCurrentFen(result.fen());
        if (match.getGameMode() == GameMode.ARAM) {
            match.setAramState(aramStateCodec.encode(result.aramState()));
        }
        if (result.checkmate()) {
            finish(match, player, player.getId().equals(match.getWhitePlayer().getId())
                    ? MatchStatus.WHITE_WON
                    : MatchStatus.BLACK_WON, MatchTerminationReason.CHECKMATE);
        } else if (result.draw()) {
            MatchTerminationReason reason = "STALEMATE".equals(result.drawReason())
                    ? MatchTerminationReason.STALEMATE
                    : MatchTerminationReason.DRAW_RULE;
            finish(match, null, MatchStatus.DRAW, reason);
        } else {
            matchRepository.save(match);
        }
        return acceptedMovePayload(match, move, true);
    }

    @Override
    @Transactional
    public Map<String, Object> resign(String username, String roomCode) {
        MatchEntity match = requireActiveMatch(roomCode);
        UserEntity resigning = requireParticipant(match, username);
        UserEntity winner = resigning.getId().equals(match.getWhitePlayer().getId())
                ? match.getBlackPlayer()
                : match.getWhitePlayer();
        MatchStatus status = winner.getId().equals(match.getWhitePlayer().getId())
                ? MatchStatus.WHITE_WON
                : MatchStatus.BLACK_WON;
        finish(match, winner, status, MatchTerminationReason.RESIGNATION);
        return gameOverPayload(match);
    }

    @Override
    public Map<String, Object> offerDraw(String username, String roomCode) {
        MatchEntity match = requireActiveMatch(roomCode);
        requireParticipant(match, username);
        drawOffers.put(roomCode.toUpperCase(), username);
        return Map.of("offeredBy", username);
    }

    @Override
    @Transactional
    public Map<String, Object> acceptDraw(String username, String roomCode) {
        MatchEntity match = requireActiveMatch(roomCode);
        requireParticipant(match, username);
        String offeredBy = drawOffers.get(roomCode.toUpperCase());
        if (offeredBy == null || offeredBy.equalsIgnoreCase(username)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "No opponent draw offer is pending");
        }
        drawOffers.remove(roomCode.toUpperCase());
        finish(match, null, MatchStatus.DRAW, MatchTerminationReason.DRAW_AGREEMENT);
        return gameOverPayload(match);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> syncState(String username, String roomCode) {
        return syncState(username, roomCode, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> syncState(String username, String roomCode, String gameMode) {
        MatchEntity match = requireMatch(roomCode);
        requireMatchingMode(match.getGameMode(), gameMode);
        requireParticipant(match, username);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("matchId", match.getId().toString());
        payload.put("status", match.getStatus().name());
        payload.put("fen", match.getCurrentFen());
        payload.put("turn", sideToMove(match.getCurrentFen()));
        payload.put("moveCount", match.getMoveCount());
        payload.put("whitePlayerId", match.getWhitePlayer().getId().toString());
        payload.put("blackPlayerId", match.getBlackPlayer().getId().toString());
        addModeState(payload, match);
        if (match.getStatus() != MatchStatus.ACTIVE) {
            payload.putAll(gameOverPayload(match));
        }
        return payload;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MatchResponseDTO> history(String username) {
        UserEntity user = requireUser(username);
        return matchRepository.findAllByWhitePlayerIdOrBlackPlayerIdOrderByCreatedAtDesc(user.getId(), user.getId())
                .stream()
                .map(match -> mapper.toMatch(match,
                        matchMoveRepository.findAllByMatchIdOrderByMoveNumberAsc(match.getId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MatchResponseDTO findById(String username, UUID matchId) {
        UserEntity user = requireUser(username);
        MatchEntity match = matchRepository.findByIdAndWhitePlayerIdOrIdAndBlackPlayerId(
                        matchId, user.getId(), matchId, user.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Match not found"));
        return mapper.toMatch(match, matchMoveRepository.findAllByMatchIdOrderByMoveNumberAsc(match.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public MatchResponseDTO active(String username) {
        UserEntity user = requireUser(username);
        MatchEntity match = matchRepository
                .findFirstByStatusAndWhitePlayerIdOrStatusAndBlackPlayerIdOrderByStartedAtDesc(
                        MatchStatus.ACTIVE, user.getId(), MatchStatus.ACTIVE, user.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "No active match"));
        return mapper.toMatch(match, matchMoveRepository.findAllByMatchIdOrderByMoveNumberAsc(match.getId()));
    }

    private void finish(MatchEntity match,
                        UserEntity winner,
                        MatchStatus status,
                        MatchTerminationReason reason) {
        if (match.getStatus() != MatchStatus.ACTIVE) {
            return;
        }
        double whiteScore = status == MatchStatus.DRAW
                ? 0.5
                : winner != null && winner.getId().equals(match.getWhitePlayer().getId()) ? 1.0 : 0.0;
        EloCalculator.RatingChange rating = eloCalculator.calculate(
                match.getWhitePlayer().getElo(),
                match.getBlackPlayer().getElo(),
                whiteScore
        );
        match.getWhitePlayer().setElo(rating.whiteRating());
        match.getBlackPlayer().setElo(rating.blackRating());
        match.setWhiteEloAfter(rating.whiteRating());
        match.setBlackEloAfter(rating.blackRating());
        match.setWinner(winner);
        match.setStatus(status);
        match.setTerminationReason(reason);
        match.setFinishedAt(LocalDateTime.now());
        match.getRoom().setStatus(RoomStatus.FINISHED);
        rewardService.processMatchReward(match);
        userRepository.save(match.getWhitePlayer());
        userRepository.save(match.getBlackPlayer());
        roomRepository.save(match.getRoom());
        matchRepository.save(match);
        drawOffers.remove(match.getRoom().getRoomCode().toUpperCase());
    }

    private Map<String, Object> gameStartPayload(MatchEntity match) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("matchId", match.getId().toString());
        payload.put("whitePlayerId", match.getWhitePlayer().getId().toString());
        payload.put("whiteUsername", match.getWhitePlayer().getUsername());
        payload.put("blackPlayerId", match.getBlackPlayer().getId().toString());
        payload.put("blackUsername", match.getBlackPlayer().getUsername());
        payload.put("fen", match.getCurrentFen());
        payload.put("turn", sideToMove(match.getCurrentFen()));
        addModeState(payload, match);
        return payload;
    }

    private Map<String, Object> acceptedMovePayload(MatchEntity match,
                                                    MatchMoveEntity move,
                                                    boolean newlyProcessed) {
        String authoritativeFen = newlyProcessed ? move.getFenAfter() : match.getCurrentFen();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("accepted", true);
        payload.put("newlyProcessed", newlyProcessed);
        payload.put("matchId", match.getId().toString());
        payload.put("moveNumber", move.getMoveNumber());
        payload.put("from", move.getFromSquare());
        payload.put("to", move.getToSquare());
        payload.put("promotion", move.getPromotion());
        payload.put("notation", move.getNotation());
        payload.put("fen", authoritativeFen);
        payload.put("turn", sideToMove(authoritativeFen));
        payload.put("check", move.getNotation().contains("+") || move.getNotation().contains("#"));
        payload.put("status", match.getStatus().name());
        addModeState(payload, match);
        if (match.getStatus() != MatchStatus.ACTIVE) {
            payload.put("gameOver", gameOverPayload(match));
        }
        return payload;
    }

    private Map<String, Object> gameOverPayload(MatchEntity match) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("matchId", match.getId().toString());
        payload.put("result", match.getStatus().name());
        payload.put("reason", match.getTerminationReason().name());
        payload.put("winnerId", match.getWinner() == null ? null : match.getWinner().getId().toString());
        payload.put("whiteEloBefore", match.getWhiteEloBefore());
        payload.put("whiteEloAfter", match.getWhiteEloAfter());
        payload.put("blackEloBefore", match.getBlackEloBefore());
        payload.put("blackEloAfter", match.getBlackEloAfter());
        return payload;
    }

    private MatchEntity requireActiveMatch(String roomCode) {
        MatchEntity match = requireMatch(roomCode);
        if (match.getStatus() != MatchStatus.ACTIVE) {
            throw new ApiException(ErrorCode.MATCH_NOT_ACTIVE);
        }
        return match;
    }

    private MatchEntity requireActiveMatchForUpdate(String roomCode) {
        MatchEntity match = matchRepository.findByRoomCodeForUpdate(roomCode)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Match not found"));
        if (match.getStatus() != MatchStatus.ACTIVE) {
            throw new ApiException(ErrorCode.MATCH_NOT_ACTIVE);
        }
        return match;
    }

    private MatchEntity requireMatch(String roomCode) {
        return matchRepository.findByRoomRoomCodeIgnoreCase(roomCode)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Match not found"));
    }

    private RoomEntity requireRoom(String roomCode) {
        return roomRepository.findByRoomCodeIgnoreCase(roomCode)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Room not found"));
    }

    private RoomEntity requireRoomForUpdate(String roomCode) {
        return roomRepository.findByRoomCodeForUpdate(roomCode)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Room not found"));
    }

    private UserEntity requireUser(String username) {
        return userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));
    }

    private UserEntity requireParticipant(MatchEntity match, String username) {
        UserEntity user = requireUser(username);
        if (!match.getWhitePlayer().getId().equals(user.getId())
                && !match.getBlackPlayer().getId().equals(user.getId())) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return user;
    }

    private String sideToMove(String fen) {
        String[] parts = fen.split(" ");
        return parts.length > 1 && "b".equals(parts[1]) ? "BLACK" : "WHITE";
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private MoveOutcome applyMove(MatchEntity match,
                                  List<MatchMoveEntity> previous,
                                  ChessMoveCommand command) {
        if (match.getGameMode() == GameMode.ARAM) {
            AramMoveResult result = aramRulesEngine.applyMove(
                    match.getCurrentFen(),
                    aramStateCodec.decode(match.getAramState()),
                    previous.stream().map(MatchMoveEntity::getFenAfter).toList(),
                    command
            );
            if (!result.accepted()) {
                throw new ApiException(ErrorCode.ILLEGAL_MOVE, result.message());
            }
            return new MoveOutcome(result.notation(), result.fen(), result.checkmate(), result.draw(),
                    result.drawReason(), result.aramState());
        }

        List<ChessMoveRecord> replay = previous.stream()
                .map(move -> new ChessMoveRecord(move.getFromSquare(), move.getToSquare(), move.getPromotion()))
                .toList();
        ChessMoveResult result = chessRulesEngine.applyMove(replay, command);
        if (!result.accepted()) {
            throw new ApiException(ErrorCode.ILLEGAL_MOVE, result.message());
        }
        return new MoveOutcome(result.notation(), result.fen(), result.checkmate(), result.draw(),
                result.drawReason(), null);
    }

    private void addModeState(Map<String, Object> payload, MatchEntity match) {
        payload.put("gameMode", match.getGameMode().name());
        if (match.getGameMode() == GameMode.ARAM) {
            payload.put("aramSeed", match.getAramSeed());
            payload.put("aramState", aramStateCodec.decode(match.getAramState()));
        }
    }

    private void requireMatchingMode(GameMode actual, String requested) {
        if (requested == null || requested.isBlank()) {
            if (actual == GameMode.ARAM) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "ARAM match requires gameMode=ARAM");
            }
            return;
        }
        GameMode parsed;
        try {
            parsed = GameMode.fromNullable(requested);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "gameMode must be CLASSIC or ARAM");
        }
        if (parsed != actual) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Match game mode does not match the request");
        }
    }

    private record MoveOutcome(
            String notation,
            String fen,
            boolean checkmate,
            boolean draw,
            String drawReason,
            AramMatchState aramState
    ) {
    }
}
