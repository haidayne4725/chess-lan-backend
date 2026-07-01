package com.chesslan.game.service.impl;

import com.chesslan.game.common.exception.ApiException;
import com.chesslan.game.common.exception.ErrorCode;
import com.chesslan.game.mapper.GameMapper;
import com.chesslan.game.model.dto.reward.RewardHistoryResponseDTO;
import com.chesslan.game.model.entity.LevelRequirement;
import com.chesslan.game.model.entity.MatchEntity;
import com.chesslan.game.model.entity.MatchStatus;
import com.chesslan.game.model.entity.MatchTerminationReason;
import com.chesslan.game.model.entity.RewardLog;
import com.chesslan.game.model.entity.RewardType;
import com.chesslan.game.model.entity.UserEntity;
import com.chesslan.game.repository.LevelRequirementRepository;
import com.chesslan.game.repository.RewardLogRepository;
import com.chesslan.game.repository.UserRepository;
import com.chesslan.game.service.interfaces.RewardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RewardServiceImpl implements RewardService {
    private static final long WIN_EXP = 100L;
    private static final long DRAW_EXP = 50L;
    private static final long LOSS_EXP = 30L;
    private static final long WIN_GOLD = 150L;
    private static final long DRAW_GOLD = 80L;
    private static final long LOSS_GOLD = 50L;
    private static final long CHECKMATE_BONUS_EXP = 20L;
    private static final long LONG_MATCH_BONUS_EXP = 10L;

    private final UserRepository userRepository;
    private final LevelRequirementRepository levelRequirementRepository;
    private final RewardLogRepository rewardLogRepository;
    private final GameMapper mapper;

    @Override
    @Transactional
    public void grantExp(UserEntity user, MatchEntity match, long amount, String description) {
        if (amount <= 0) {
            return;
        }
        user.setExp(user.getExp() + amount);
        rewardLogRepository.save(rewardLog(user, match, RewardType.EXP, amount, description));
    }

    @Override
    @Transactional
    public void grantGold(UserEntity user, MatchEntity match, long amount, String description) {
        if (amount <= 0) {
            return;
        }
        user.setGold(user.getGold() + amount);
        rewardLogRepository.save(rewardLog(user, match, RewardType.GOLD, amount, description));
    }

    @Override
    @Transactional
    public void processLevelUp(UserEntity user, MatchEntity match) {
        while (true) {
            LevelRequirement nextLevel = levelRequirementRepository
                    .findFirstByLevelGreaterThanOrderByLevelAsc(user.getLevel())
                    .orElse(null);
            if (nextLevel == null || user.getExp() < nextLevel.getRequiredExp()) {
                return;
            }
            user.setLevel(nextLevel.getLevel());
            rewardLogRepository.save(rewardLog(
                    user,
                    match,
                    RewardType.LEVEL_UP,
                    nextLevel.getRequiredExp(),
                    "Level Up to " + nextLevel.getLevel()
            ));
        }
    }

    @Override
    @Transactional
    public void processMatchReward(MatchEntity match) {
        if (rewardLogRepository.existsByMatchId(match.getId())) {
            return;
        }

        UserEntity whitePlayer = match.getWhitePlayer();
        UserEntity blackPlayer = match.getBlackPlayer();
        MatchStatus status = match.getStatus();

        incrementMatchTotals(whitePlayer, blackPlayer, status);

        if (status == MatchStatus.DRAW) {
            rewardMatchResult(whitePlayer, match, DRAW_EXP, DRAW_GOLD, "Match Draw");
            rewardMatchResult(blackPlayer, match, DRAW_EXP, DRAW_GOLD, "Match Draw");
        } else {
            UserEntity winner = requireWinner(match);
            UserEntity loser = winner.getId().equals(whitePlayer.getId()) ? blackPlayer : whitePlayer;
            rewardMatchResult(winner, match, WIN_EXP, WIN_GOLD, "Match Victory");
            rewardMatchResult(loser, match, LOSS_EXP, LOSS_GOLD, "Match Defeat");
            if (match.getTerminationReason() == MatchTerminationReason.CHECKMATE) {
                grantExp(winner, match, CHECKMATE_BONUS_EXP, "Checkmate Bonus");
            }
        }

        if (match.getMoveCount() > 30) {
            grantExp(whitePlayer, match, LONG_MATCH_BONUS_EXP, "Long Match Bonus");
            grantExp(blackPlayer, match, LONG_MATCH_BONUS_EXP, "Long Match Bonus");
        }

        processLevelUp(whitePlayer, match);
        processLevelUp(blackPlayer, match);

        userRepository.save(whitePlayer);
        userRepository.save(blackPlayer);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RewardHistoryResponseDTO> history(String username) {
        UserEntity user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));
        return rewardLogRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(mapper::toRewardHistory)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Long nextLevelExp(Integer currentLevel) {
        return levelRequirementRepository.findFirstByLevelGreaterThanOrderByLevelAsc(currentLevel)
                .map(LevelRequirement::getRequiredExp)
                .orElse(null);
    }

    private void rewardMatchResult(UserEntity user, MatchEntity match, long expAmount, long goldAmount, String description) {
        grantExp(user, match, expAmount, description + " EXP");
        grantGold(user, match, goldAmount, description + " Gold");
    }

    private void incrementMatchTotals(UserEntity whitePlayer, UserEntity blackPlayer, MatchStatus status) {
        whitePlayer.setTotalMatches(whitePlayer.getTotalMatches() + 1);
        blackPlayer.setTotalMatches(blackPlayer.getTotalMatches() + 1);

        if (status == MatchStatus.DRAW) {
            whitePlayer.setTotalDraws(whitePlayer.getTotalDraws() + 1);
            blackPlayer.setTotalDraws(blackPlayer.getTotalDraws() + 1);
            return;
        }

        if (status == MatchStatus.WHITE_WON) {
            whitePlayer.setTotalWins(whitePlayer.getTotalWins() + 1);
            blackPlayer.setTotalLosses(blackPlayer.getTotalLosses() + 1);
            return;
        }

        if (status == MatchStatus.BLACK_WON) {
            blackPlayer.setTotalWins(blackPlayer.getTotalWins() + 1);
            whitePlayer.setTotalLosses(whitePlayer.getTotalLosses() + 1);
        }
    }

    private UserEntity requireWinner(MatchEntity match) {
        if (match.getWinner() == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Winner is required for decisive reward processing");
        }
        return match.getWinner();
    }

    private RewardLog rewardLog(UserEntity user, MatchEntity match, RewardType rewardType, long amount, String description) {
        RewardLog rewardLog = new RewardLog();
        rewardLog.setUserId(user.getId());
        rewardLog.setMatchId(match.getId());
        rewardLog.setRewardType(rewardType);
        rewardLog.setAmount(amount);
        rewardLog.setDescription(description);
        return rewardLog;
    }
}
