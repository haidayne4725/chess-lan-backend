package com.chesslan.game.repository;

import com.chesslan.game.model.entity.MatchMoveEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchMoveRepository extends JpaRepository<MatchMoveEntity, UUID> {
    @EntityGraph(attributePaths = {"player"})
    List<MatchMoveEntity> findAllByMatchIdOrderByMoveNumberAsc(UUID matchId);

    Optional<MatchMoveEntity> findByMatchIdAndRequestId(UUID matchId, String requestId);
}
