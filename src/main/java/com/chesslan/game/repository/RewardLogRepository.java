package com.chesslan.game.repository;

import com.chesslan.game.model.entity.RewardLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface RewardLogRepository extends JpaRepository<RewardLog, UUID> {
    boolean existsByMatchId(UUID matchId);
    List<RewardLog> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("""
            select coalesce(sum(r.amount), 0)
            from RewardLog r
            where r.userId = :userId
              and r.rewardType = :rewardType
              and r.createdAt >= :since
              and r.description like 'BOT_%'
            """)
    Long sumBotRewardsSince(@Param("userId") UUID userId,
                            @Param("rewardType") com.chesslan.game.model.entity.RewardType rewardType,
                            @Param("since") LocalDateTime since);
}
