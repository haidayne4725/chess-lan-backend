package com.chesslan.game.model.entity;

import com.chesslan.game.infrastructure.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "match_moves",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_match_move_number", columnNames = {"match_id", "move_number"}),
                @UniqueConstraint(name = "uk_match_request_id", columnNames = {"match_id", "request_id"})
        }
)
public class MatchMoveEntity extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private MatchEntity match;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private UserEntity player;

    @Column(name = "move_number", nullable = false)
    private Integer moveNumber;

    @Column(name = "request_id", nullable = false, length = 80)
    private String requestId;

    @Column(nullable = false, length = 2)
    private String fromSquare;

    @Column(nullable = false, length = 2)
    private String toSquare;

    @Column(length = 10)
    private String promotion;

    @Column(nullable = false, length = 10)
    private String notation;

    @Column(nullable = false, columnDefinition = "text")
    private String fenAfter;
}
