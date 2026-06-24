package com.chesslan.game.mapper;

import com.chesslan.game.model.dto.room.RoomResponseDTO;
import com.chesslan.game.model.dto.match.MatchMoveResponseDTO;
import com.chesslan.game.model.dto.match.MatchResponseDTO;
import com.chesslan.game.model.dto.user.UserProfileResponseDTO;
import com.chesslan.game.model.entity.MatchEntity;
import com.chesslan.game.model.entity.MatchMoveEntity;
import com.chesslan.game.model.entity.RoomEntity;
import com.chesslan.game.model.entity.UserEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GameMapper {
    public UserProfileResponseDTO toUserProfile(UserEntity user) {
        return new UserProfileResponseDTO(user.getId(), user.getUsername(), user.getElo(), user.getCreatedAt());
    }

    public RoomResponseDTO toRoom(RoomEntity room) {
        UserEntity guest = room.getGuest();
        return new RoomResponseDTO(
                room.getId(),
                room.getRoomCode(),
                room.getHost().getId(),
                room.getHost().getUsername(),
                guest == null ? null : guest.getId(),
                guest == null ? null : guest.getUsername(),
                room.getStatus().name()
        );
    }

    public MatchResponseDTO toMatch(MatchEntity match, List<MatchMoveEntity> moves) {
        UserEntity winner = match.getWinner();
        return new MatchResponseDTO(
                match.getId(),
                match.getRoom().getRoomCode(),
                match.getWhitePlayer().getId(),
                match.getWhitePlayer().getUsername(),
                match.getBlackPlayer().getId(),
                match.getBlackPlayer().getUsername(),
                winner == null ? null : winner.getId(),
                winner == null ? null : winner.getUsername(),
                match.getStatus().name(),
                match.getTerminationReason().name(),
                match.getCurrentFen(),
                match.getMoveCount(),
                match.getWhiteEloBefore(),
                match.getWhiteEloAfter(),
                match.getBlackEloBefore(),
                match.getBlackEloAfter(),
                match.getStartedAt(),
                match.getFinishedAt(),
                moves.stream().map(this::toMove).toList()
        );
    }

    public MatchMoveResponseDTO toMove(MatchMoveEntity move) {
        return new MatchMoveResponseDTO(
                move.getId(),
                move.getMoveNumber(),
                move.getPlayer().getId(),
                move.getPlayer().getUsername(),
                move.getFromSquare(),
                move.getToSquare(),
                move.getPromotion(),
                move.getNotation(),
                move.getFenAfter(),
                move.getCreatedAt()
        );
    }
}
