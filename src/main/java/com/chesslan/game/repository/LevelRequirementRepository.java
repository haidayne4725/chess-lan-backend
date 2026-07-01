package com.chesslan.game.repository;

import com.chesslan.game.model.entity.LevelRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LevelRequirementRepository extends JpaRepository<LevelRequirement, UUID> {
    List<LevelRequirement> findAllByOrderByLevelAsc();
    Optional<LevelRequirement> findByLevel(Integer level);
    Optional<LevelRequirement> findFirstByLevelGreaterThanOrderByLevelAsc(Integer level);
}
