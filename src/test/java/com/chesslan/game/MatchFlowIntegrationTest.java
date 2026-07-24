package com.chesslan.game;

import com.chesslan.game.common.exception.ApiException;
import com.chesslan.game.model.dto.auth.SignupRequestDTO;
import com.chesslan.game.model.entity.GameMode;
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

import java.util.UUID;

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

    @Test
    void aramRoomStartsMovesAndSyncsWithAuthoritativeState() {
        authService.signup(new SignupRequestDTO("aram_white", "123456"));
        authService.signup(new SignupRequestDTO("aram_black", "123456"));

        var room = roomService.create("aram_white", "ARAM");
        roomService.join("aram_black", room.roomCode(), "ARAM");
        var started = matchService.startMatch(room.roomCode(), "ARAM");

        assertThat(started.get("gameMode")).isEqualTo("ARAM");
        assertThat(started.get("aramSeed")).isNotNull();
        assertThat(started.get("aramState")).isNotNull();

        var moved = matchService.submitMove(
                "aram_white", room.roomCode(), "aram-move-1", "e2", "e4", null, "ARAM");
        assertThat(moved.get("accepted")).isEqualTo(true);
        assertThat(moved.get("gameMode")).isEqualTo("ARAM");
        assertThat(moved.get("aramState")).isNotNull();

        var synced = matchService.syncState("aram_black", room.roomCode(), "ARAM");
        assertThat(synced.get("fen")).isEqualTo(moved.get("fen"));
        assertThat(synced.get("aramState")).isEqualTo(moved.get("aramState"));

        assertThatThrownBy(() -> matchService.syncState("aram_black", room.roomCode(), null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("gameMode=ARAM");
    }

    @Test
    void modeMismatchFailsClosedWithoutPoisoningRoomOrMatch() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String host = "mode_host_" + suffix;
        String guest = "mode_guest_" + suffix;
        authService.signup(new SignupRequestDTO(host, "123456"));
        authService.signup(new SignupRequestDTO(guest, "123456"));

        var room = roomService.create(host, "ARAM");
        assertThatThrownBy(() -> roomService.join(guest, room.roomCode(), "CLASSIC"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("mode");

        var joined = roomService.join(guest, room.roomCode(), "ARAM");
        assertThat(joined.guestUsername()).isEqualTo(guest);
        assertThatThrownBy(() -> matchService.startMatch(room.roomCode()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("gameMode=ARAM");

        matchService.startMatch(room.roomCode(), "ARAM");
        assertThatThrownBy(() -> matchService.submitMove(
                host, room.roomCode(), "wrong-mode-move", "e2", "e4", null, "CLASSIC"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("mode");

        var untouched = matchService.syncState(host, room.roomCode(), "ARAM");
        assertThat(untouched.get("moveCount")).isEqualTo(0);
        assertThat(matchRepository.findByRoomRoomCodeIgnoreCase(room.roomCode()).orElseThrow().getGameMode())
                .isEqualTo(GameMode.ARAM);
    }

    @Test
    void classicCompatibilityStillAcceptsLegacyRequestsWithoutMode() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String host = "classic_host_" + suffix;
        String guest = "classic_guest_" + suffix;
        authService.signup(new SignupRequestDTO(host, "123456"));
        authService.signup(new SignupRequestDTO(guest, "123456"));

        String roomCode = roomService.create(host).roomCode();
        roomService.join(guest, roomCode);
        var started = matchService.startMatch(roomCode);
        var moved = matchService.submitMove(host, roomCode, "classic-legacy-1", "e2", "e4", null);

        assertThat(started.get("gameMode")).isEqualTo("CLASSIC");
        assertThat(started).doesNotContainKeys("aramSeed", "aramState");
        assertThat(moved.get("gameMode")).isEqualTo("CLASSIC");
        assertThat(matchService.syncState(guest, roomCode).get("moveCount")).isEqualTo(1);
    }
}
