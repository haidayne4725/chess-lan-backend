package com.chesslan.game.repository;

import com.chesslan.game.model.entity.MatchEntity;
import com.chesslan.game.model.entity.MatchStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<MatchEntity, UUID> {
    @EntityGraph(attributePaths = {"room", "whitePlayer", "blackPlayer", "winner"})
    Optional<MatchEntity> findByRoomRoomCodeIgnoreCase(String roomCode);

    @EntityGraph(attributePaths = {"room", "whitePlayer", "blackPlayer", "winner"})
    Optional<MatchEntity> findByIdAndWhitePlayerIdOrIdAndBlackPlayerId(
            UUID firstId,
            UUID whitePlayerId,
            UUID secondId,
            UUID blackPlayerId
    );

    @EntityGraph(attributePaths = {"room", "whitePlayer", "blackPlayer", "winner"})
    List<MatchEntity> findAllByWhitePlayerIdOrBlackPlayerIdOrderByCreatedAtDesc(
            UUID whitePlayerId,
            UUID blackPlayerId
    );

    @EntityGraph(attributePaths = {"room", "whitePlayer", "blackPlayer", "winner"})
    Optional<MatchEntity> findFirstByStatusAndWhitePlayerIdOrStatusAndBlackPlayerIdOrderByStartedAtDesc(
            MatchStatus firstStatus,
            UUID whitePlayerId,
            MatchStatus secondStatus,
            UUID blackPlayerId
    );
}
