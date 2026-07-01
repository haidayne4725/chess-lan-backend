package com.chesslan.game;

import com.chesslan.game.model.dto.auth.LoginRequestDTO;
import com.chesslan.game.model.dto.auth.SignupRequestDTO;
import com.chesslan.game.service.interfaces.AuthService;
import com.chesslan.game.service.interfaces.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AuthFlowIntegrationTest {
    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    @Test
    void signupLoginProfileAndLogoutFlow() {
        var signup = authService.signup(new SignupRequestDTO("unity_player", "123456"));

        assertThat(signup.token()).isNotBlank();
        assertThat(signup.user().elo()).isEqualTo(1200);
        assertThat(signup.user().level()).isEqualTo(1);
        assertThat(signup.user().exp()).isZero();
        assertThat(signup.user().gold()).isZero();

        var login = authService.login(new LoginRequestDTO("unity_player", "123456"));
        assertThat(login.token()).isNotBlank();
        assertThat(userService.me("unity_player").username()).isEqualTo("unity_player");
        assertThat(userService.progression("unity_player").nextLevelExp()).isEqualTo(100L);
        assertThat(userService.currency("unity_player").gold()).isZero();

        authService.logout();
    }
}
