package com.chesslan.game.repository;

import com.chesslan.game.model.entity.RewardLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RewardLogRepository extends JpaRepository<RewardLog, UUID> {
    boolean existsByMatchId(UUID matchId);
    List<RewardLog> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}
