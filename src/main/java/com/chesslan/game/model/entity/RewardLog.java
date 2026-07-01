package com.chesslan.game.model.entity;

import com.chesslan.game.infrastructure.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "reward_logs",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_reward_logs_user_match_type_desc",
                        columnNames = {"user_id", "match_id", "reward_type", "description"}
                )
        }
)
public class RewardLog extends BaseEntity {
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RewardType rewardType;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false, length = 255)
    private String description;
}
