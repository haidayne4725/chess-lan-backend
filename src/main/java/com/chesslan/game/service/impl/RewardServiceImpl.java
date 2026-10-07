package com.chesslan.game.service.impl;

import com.chesslan.game.common.exception.ApiException;
import com.chesslan.game.common.exception.ErrorCode;
import com.chesslan.game.mapper.GameMapper;
import com.chesslan.game.model.dto.reward.RewardHistoryResponseDTO;
import com.chesslan.game.model.dto.reward.BotDifficulty;
import com.chesslan.game.model.dto.reward.BotMatchResult;
import com.chesslan.game.model.dto.reward.BotMatchRewardRequestDTO;
import com.chesslan.game.model.dto.reward.BotMatchRewardResponseDTO;
import com.chesslan.game.model.entity.LevelRequirement;
import com.chesslan.game.model.entity.MatchEntity;
import com.chesslan.game.model.entity.MatchStatus;
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
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RewardServiceImpl implements RewardService {
    private static final long MAX_BOT_EXP_PER_DAY = 3000L;
    private static final long MAX_BOT_GOLD_PER_DAY = 1500L;

    private final UserRepository userRepository;
    private final com.chesslan.game.repository.MatchRepository matchRepository;
    private final com.chesslan.game.repository.MatchMoveRepository matchMoveRepository;
    private final jakarta.persistence.EntityManager entityManager;
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
        MatchEntity persisted = matchRepository.findByIdForUpdate(match.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Match not found"));
        if (persisted.getStatus() == MatchStatus.ACTIVE || persisted.isStatisticsProcessed()) return;
        // Refresh under locks in stable ID order to avoid stale totals and cross-match lost updates.
        var players = new java.util.ArrayList<>(List.of(persisted.getWhitePlayer(), persisted.getBlackPlayer()));
        players.sort(java.util.Comparator.comparing(p -> p.getId().toString()));
        for (var player : players) entityManager.refresh(player, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        boolean played = matchMoveRepository.existsByMatchIdAndPlayerId(persisted.getId(), persisted.getWhitePlayer().getId())
                && matchMoveRepository.existsByMatchIdAndPlayerId(persisted.getId(), persisted.getBlackPlayer().getId());
        persisted.setStatsApplied(played);
        if (played) incrementMatchTotals(persisted.getWhitePlayer(), persisted.getBlackPlayer(), persisted.getStatus());
        persisted.setStatisticsProcessed(true);
        persisted.setRated(false);
        persisted.setSettlementDecision(played ? "UNRATED_LAN_ROOM" : "NOT_PLAYED");
        // No Elo, gold, EXP, bonuses or level changes can be minted by invite rooms.
        userRepository.save(persisted.getWhitePlayer());
        userRepository.save(persisted.getBlackPlayer());
        matchRepository.save(persisted);
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

    @Override
    @Transactional
    public BotMatchRewardResponseDTO processBotMatchReward(String username, BotMatchRewardRequestDTO request) {
        UserEntity user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));

        BotReward reward = botReward(request.difficulty(), request.result());
        long awardedExp = cappedBotReward(user, RewardType.EXP, reward.exp(), MAX_BOT_EXP_PER_DAY);
        long awardedGold = cappedBotReward(user, RewardType.GOLD, reward.gold(), MAX_BOT_GOLD_PER_DAY);
        UUID rewardEventId = UUID.randomUUID();
        String description = "BOT_" + request.difficulty() + "_" + request.result();

        grantBotReward(user, rewardEventId, RewardType.EXP, awardedExp, description);
        grantBotReward(user, rewardEventId, RewardType.GOLD, awardedGold, description);
        processBotLevelUp(user, rewardEventId);
        userRepository.save(user);

        return new BotMatchRewardResponseDTO(
                awardedExp,
                awardedGold,
                user.getExp(),
                user.getGold(),
                user.getLevel()
        );
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

    private RewardLog rewardLog(UserEntity user, MatchEntity match, RewardType rewardType, long amount, String description) {
        return rewardLog(user, match.getId(), rewardType, amount, description);
    }

    private void grantBotReward(UserEntity user, UUID eventId, RewardType type, long amount, String description) {
        if (amount <= 0) {
            return;
        }
        if (type == RewardType.EXP) {
            user.setExp(user.getExp() + amount);
        } else if (type == RewardType.GOLD) {
            user.setGold(user.getGold() + amount);
        }
        rewardLogRepository.save(rewardLog(user, eventId, type, amount, description));
    }

    private void processBotLevelUp(UserEntity user, UUID eventId) {
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
                    eventId,
                    RewardType.LEVEL_UP,
                    nextLevel.getRequiredExp(),
                    "BOT_LEVEL_UP_" + nextLevel.getLevel()
            ));
        }
    }

    private long cappedBotReward(UserEntity user, RewardType type, long requested, long dailyCap) {
        Long awardedToday = rewardLogRepository.sumBotRewardsSince(
                user.getId(),
                type,
                LocalDate.now().atStartOfDay()
        );
        return Math.min(requested, Math.max(0L, dailyCap - awardedToday));
    }

    private BotReward botReward(BotDifficulty difficulty, BotMatchResult result) {
        if (result == BotMatchResult.LOSE) {
            return switch (difficulty) {
                case BEGINNER -> new BotReward(5L, 0L);
                case EASY -> new BotReward(10L, 0L);
                case MEDIUM -> new BotReward(20L, 0L);
                case HARD -> new BotReward(30L, 0L);
                case EXPERT -> new BotReward(50L, 0L);
            };
        }
        return switch (difficulty) {
            case BEGINNER -> new BotReward(20L, 5L);
            case EASY -> new BotReward(40L, 10L);
            case MEDIUM -> new BotReward(80L, 25L);
            case HARD -> new BotReward(150L, 50L);
            case EXPERT -> new BotReward(250L, 100L);
        };
    }

    private RewardLog rewardLog(UserEntity user, UUID matchId, RewardType rewardType, long amount, String description) {
        RewardLog rewardLog = new RewardLog();
        rewardLog.setUserId(user.getId());
        rewardLog.setMatchId(matchId);
        rewardLog.setRewardType(rewardType);
        rewardLog.setAmount(amount);
        rewardLog.setDescription(description);
        return rewardLog;
    }

    private record BotReward(long exp, long gold) {
    }
}
