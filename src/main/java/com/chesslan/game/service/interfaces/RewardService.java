package com.chesslan.game.service.interfaces;

import com.chesslan.game.model.dto.reward.RewardHistoryResponseDTO;
import com.chesslan.game.model.entity.MatchEntity;
import com.chesslan.game.model.entity.UserEntity;

import java.util.List;

public interface RewardService {
    void grantExp(UserEntity user, MatchEntity match, long amount, String description);
    void grantGold(UserEntity user, MatchEntity match, long amount, String description);
    void processLevelUp(UserEntity user, MatchEntity match);
    void processMatchReward(MatchEntity match);
    List<RewardHistoryResponseDTO> history(String username);
    Long nextLevelExp(Integer currentLevel);
}
