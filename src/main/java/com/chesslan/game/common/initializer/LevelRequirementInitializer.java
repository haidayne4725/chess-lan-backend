package com.chesslan.game.common.initializer;

import com.chesslan.game.model.entity.LevelRequirement;
import com.chesslan.game.repository.LevelRequirementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LevelRequirementInitializer implements ApplicationRunner {
    private final LevelRequirementRepository levelRequirementRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (levelRequirementRepository.count() > 0) {
            return;
        }

        levelRequirementRepository.saveAll(List.of(
                level(1, 0L),
                level(2, 100L),
                level(3, 250L),
                level(4, 500L),
                level(5, 800L),
                level(6, 1200L),
                level(7, 1700L),
                level(8, 2300L),
                level(9, 3000L),
                level(10, 3800L)
        ));
    }

    private LevelRequirement level(int level, long requiredExp) {
        LevelRequirement levelRequirement = new LevelRequirement();
        levelRequirement.setLevel(level);
        levelRequirement.setRequiredExp(requiredExp);
        return levelRequirement;
    }
}
