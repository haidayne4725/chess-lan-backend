package com.chesslan.game;

import com.chesslan.game.model.dto.auth.SignupRequestDTO;
import com.chesslan.game.model.dto.reward.BotDifficulty;
import com.chesslan.game.model.dto.reward.BotMatchResult;
import com.chesslan.game.model.dto.reward.BotMatchRewardRequestDTO;
import com.chesslan.game.model.entity.MatchEntity;
import com.chesslan.game.model.entity.MatchStatus;
import com.chesslan.game.model.entity.MatchTerminationReason;
import com.chesslan.game.model.entity.RoomEntity;
import com.chesslan.game.model.entity.RoomStatus;
import com.chesslan.game.repository.MatchRepository;
import com.chesslan.game.repository.RewardLogRepository;
import com.chesslan.game.repository.RoomRepository;
import com.chesslan.game.repository.UserRepository;
import com.chesslan.game.service.interfaces.AuthService;
import com.chesslan.game.service.interfaces.RewardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RewardServiceIntegrationTest {
    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private RewardLogRepository rewardLogRepository;

    @Autowired
    private RewardService rewardService;

    @Test
    void processMatchRewardIsIdempotentAndAppliesBonuses() {
        authService.signup(new SignupRequestDTO("reward_white", "123456"));
        authService.signup(new SignupRequestDTO("reward_black", "123456"));

        var white = userRepository.findByUsernameIgnoreCase("reward_white").orElseThrow();
        var black = userRepository.findByUsernameIgnoreCase("reward_black").orElseThrow();

        RoomEntity room = new RoomEntity();
        room.setRoomCode("RLONG1");
        room.setHost(white);
        room.setGuest(black);
        room.setStatus(RoomStatus.FINISHED);
        room = roomRepository.save(room);

        MatchEntity match = new MatchEntity();
        match.setRoom(room);
        match.setWhitePlayer(white);
        match.setBlackPlayer(black);
        match.setWinner(black);
        match.setStatus(MatchStatus.BLACK_WON);
        match.setTerminationReason(MatchTerminationReason.CHECKMATE);
        match.setCurrentFen("7k/8/8/8/8/8/8/7K w - - 0 1");
        match.setMoveCount(31);
        match.setWhiteEloBefore(1200);
        match.setWhiteEloAfter(1184);
        match.setBlackEloBefore(1200);
        match.setBlackEloAfter(1216);
        match.setStartedAt(LocalDateTime.now().minusMinutes(20));
        match.setFinishedAt(LocalDateTime.now());
        match = matchRepository.save(match);

        rewardService.processMatchReward(match);
        rewardService.processMatchReward(match);

        var updatedWhite = userRepository.findByUsernameIgnoreCase("reward_white").orElseThrow();
        assertThat(updatedWhite.getExp()).isEqualTo(40L);
        assertThat(updatedWhite.getGold()).isEqualTo(50L);
        assertThat(updatedWhite.getTotalMatches()).isEqualTo(1L);
        assertThat(updatedWhite.getTotalLosses()).isEqualTo(1L);

        var updatedBlack = userRepository.findByUsernameIgnoreCase("reward_black").orElseThrow();
        assertThat(updatedBlack.getExp()).isEqualTo(130L);
        assertThat(updatedBlack.getGold()).isEqualTo(150L);
        assertThat(updatedBlack.getLevel()).isEqualTo(2);
        assertThat(updatedBlack.getTotalMatches()).isEqualTo(1L);
        assertThat(updatedBlack.getTotalWins()).isEqualTo(1L);

        long matchRewardCount = rewardLogRepository.findAllByUserIdOrderByCreatedAtDesc(white.getId()).size()
                + rewardLogRepository.findAllByUserIdOrderByCreatedAtDesc(black.getId()).size();
        assertThat(matchRewardCount).isEqualTo(8L);
        assertThat(rewardService.history("reward_black"))
                .extracting(reward -> reward.description())
                .contains("Match Victory EXP", "Match Victory Gold", "Checkmate Bonus", "Long Match Bonus", "Level Up to 2");
    }

    @Test
    void botMatchRewardsProgressionWithoutAffectingRankedStats() {
        authService.signup(new SignupRequestDTO("bot_reward_player", "123456"));

        var reward = rewardService.processBotMatchReward(
                "bot_reward_player",
                new BotMatchRewardRequestDTO(BotDifficulty.EXPERT, BotMatchResult.WIN)
        );

        assertThat(reward.expAwarded()).isEqualTo(250L);
        assertThat(reward.goldAwarded()).isEqualTo(100L);
        assertThat(reward.level()).isEqualTo(3);

        var user = userRepository.findByUsernameIgnoreCase("bot_reward_player").orElseThrow();
        assertThat(user.getElo()).isEqualTo(1200);
        assertThat(user.getTotalMatches()).isZero();
        assertThat(user.getTotalWins()).isZero();
        assertThat(user.getTotalLosses()).isZero();
        assertThat(rewardService.history("bot_reward_player"))
                .extracting(item -> item.description())
                .contains("BOT_EXPERT_WIN", "BOT_LEVEL_UP_2", "BOT_LEVEL_UP_3");
    }
}
