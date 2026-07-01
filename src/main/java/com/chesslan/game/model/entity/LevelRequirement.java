package com.chesslan.game.model.entity;

import com.chesslan.game.infrastructure.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "level_requirements")
public class LevelRequirement extends BaseEntity {
    @Column(nullable = false, unique = true)
    private Integer level;

    @Column(nullable = false)
    private Long requiredExp;
}
