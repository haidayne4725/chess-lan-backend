package com.chesslan.game.service.impl;

import com.chesslan.game.common.exception.ApiException;
import com.chesslan.game.common.exception.ErrorCode;
import com.chesslan.game.infrastructure.chess.ChessMoveCommand;
import com.chesslan.game.infrastructure.chess.ChessMoveRecord;
import com.chesslan.game.infrastructure.chess.ChessMoveResult;
import com.chesslan.game.infrastructure.chess.ChessRulesEngine;
import com.chesslan.game.mapper.GameMapper;
import com.chesslan.game.model.dto.match.MatchResponseDTO;
import com.chesslan.game.model.entity.MatchEntity;
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
    private final EloCalculator eloCalculator;
    private final RewardService rewardService;
    private final GameMapper mapper;
    private final ConcurrentHashMap<String, String> drawOffers = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public Map<String, Object> startMatch(String roomCode) {
        RoomEntity room = requireRoom(roomCode);
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
        if (requestId == null || requestId.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "requestId is required");
        }
        MatchEntity match = requireActiveMatch(roomCode);
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
        List<ChessMoveRecord> replay = previous.stream()
                .map(move -> new ChessMoveRecord(move.getFromSquare(), move.getToSquare(), move.getPromotion()))
                .toList();
        ChessMoveResult result = chessRulesEngine.applyMove(replay, new ChessMoveCommand(from, to, promotion));
        if (!result.accepted()) {
            throw new ApiException(ErrorCode.ILLEGAL_MOVE, result.message());
        }

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
        MatchEntity match = requireMatch(roomCode);
        requireParticipant(match, username);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("matchId", match.getId().toString());
        payload.put("status", match.getStatus().name());
        payload.put("fen", match.getCurrentFen());
        payload.put("turn", sideToMove(match.getCurrentFen()));
        payload.put("moveCount", match.getMoveCount());
        payload.put("whitePlayerId", match.getWhitePlayer().getId().toString());
        payload.put("blackPlayerId", match.getBlackPlayer().getId().toString());
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
        return payload;
    }

    private Map<String, Object> acceptedMovePayload(MatchEntity match,
                                                    MatchMoveEntity move,
                                                    boolean newlyProcessed) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("accepted", true);
        payload.put("newlyProcessed", newlyProcessed);
        payload.put("matchId", match.getId().toString());
        payload.put("moveNumber", move.getMoveNumber());
        payload.put("from", move.getFromSquare());
        payload.put("to", move.getToSquare());
        payload.put("promotion", move.getPromotion());
        payload.put("notation", move.getNotation());
        payload.put("fen", move.getFenAfter());
        payload.put("turn", sideToMove(move.getFenAfter()));
        payload.put("check", move.getNotation().contains("+") || move.getNotation().contains("#"));
        payload.put("status", match.getStatus().name());
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

    private MatchEntity requireMatch(String roomCode) {
        return matchRepository.findByRoomRoomCodeIgnoreCase(roomCode)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Match not found"));
    }

    private RoomEntity requireRoom(String roomCode) {
        return roomRepository.findByRoomCodeIgnoreCase(roomCode)
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
}
