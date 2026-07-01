package com.chesslan.game;

import com.chesslan.game.common.exception.ApiException;
import com.chesslan.game.model.dto.auth.SignupRequestDTO;
import com.chesslan.game.model.entity.MatchStatus;
import com.chesslan.game.repository.MatchRepository;
import com.chesslan.game.repository.UserRepository;
import com.chesslan.game.service.interfaces.AuthService;
import com.chesslan.game.service.interfaces.MatchService;
import com.chesslan.game.service.interfaces.RewardService;
import com.chesslan.game.service.interfaces.RoomService;
import com.chesslan.game.service.interfaces.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class MatchFlowIntegrationTest {
    @Autowired
    private AuthService authService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private MatchService matchService;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RewardService rewardService;

    @Autowired
    private UserService userService;

    @Test
    void foolMatePersistsMovesFinishesMatchAndUpdatesElo() {
        authService.signup(new SignupRequestDTO("white_player", "123456"));
        authService.signup(new SignupRequestDTO("black_player", "123456"));

        String roomCode = roomService.create("white_player").roomCode();
        roomService.join("black_player", roomCode);
        matchService.startMatch(roomCode);

        matchService.submitMove("white_player", roomCode, "move-1", "f2", "f3", null);
        assertThatThrownBy(() ->
                matchService.submitMove("white_player", roomCode, "wrong-turn", "g2", "g3", null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("turn");

        matchService.submitMove("black_player", roomCode, "move-2", "e7", "e5", null);
        matchService.submitMove("white_player", roomCode, "move-3", "g2", "g4", null);
        var finalMove = matchService.submitMove("black_player", roomCode, "move-4", "d8", "h4", null);

        assertThat(finalMove.get("status")).isEqualTo(MatchStatus.BLACK_WON.name());
        var match = matchRepository.findByRoomRoomCodeIgnoreCase(roomCode).orElseThrow();
        assertThat(match.getMoveCount()).isEqualTo(4);
        assertThat(match.getStatus()).isEqualTo(MatchStatus.BLACK_WON);
        assertThat(match.getWinner().getUsername()).isEqualTo("black_player");

        assertThat(userRepository.findByUsernameIgnoreCase("white_player").orElseThrow().getElo())
                .isEqualTo(1184);
        assertThat(userRepository.findByUsernameIgnoreCase("black_player").orElseThrow().getElo())
                .isEqualTo(1216);

        var whiteProfile = userService.me("white_player");
        assertThat(whiteProfile.exp()).isEqualTo(30L);
        assertThat(whiteProfile.gold()).isEqualTo(50L);
        assertThat(whiteProfile.totalMatches()).isEqualTo(1L);
        assertThat(whiteProfile.totalLosses()).isEqualTo(1L);

        var blackProfile = userService.me("black_player");
        assertThat(blackProfile.exp()).isEqualTo(120L);
        assertThat(blackProfile.gold()).isEqualTo(150L);
        assertThat(blackProfile.level()).isEqualTo(2);
        assertThat(blackProfile.totalMatches()).isEqualTo(1L);
        assertThat(blackProfile.totalWins()).isEqualTo(1L);

        assertThat(rewardService.history("black_player"))
                .extracting(reward -> reward.description())
                .contains("Match Victory EXP", "Match Victory Gold", "Checkmate Bonus", "Level Up to 2");

        var history = matchService.history("white_player");
        assertThat(history).hasSize(1);
        assertThat(history.get(0).moves()).hasSize(4);
        assertThat(history.get(0).moves().get(3).notation()).contains("#");
    }
}
