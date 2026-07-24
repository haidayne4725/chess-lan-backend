package com.chesslan.game.model.entity;

import com.chesslan.game.infrastructure.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "matches")
public class MatchEntity extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false, unique = true)
    private RoomEntity room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "white_player_id", nullable = false)
    private UserEntity whitePlayer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "black_player_id", nullable = false)
    private UserEntity blackPlayer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private UserEntity winner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchStatus status = MatchStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MatchTerminationReason terminationReason = MatchTerminationReason.NONE;

    @Column(nullable = false, columnDefinition = "text")
    private String currentFen;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GameMode gameMode = GameMode.CLASSIC;

    @Column(length = 80)
    private String aramSeed;

    @Column(columnDefinition = "text")
    private String aramState;

    @Column(nullable = false)
    private Integer moveCount = 0;

    @Column(nullable = false)
    private Integer whiteEloBefore;

    private Integer whiteEloAfter;

    @Column(nullable = false)
    private Integer blackEloBefore;

    private Integer blackEloAfter;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    @Version
    private Long version;
}
