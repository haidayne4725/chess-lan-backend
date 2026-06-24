package com.chesslan.game.controller.impl;

import com.chesslan.game.common.response.ApiResponse;
import com.chesslan.game.controller.interfaces.AuthControllerApi;
import com.chesslan.game.model.dto.auth.AuthResponseDTO;
import com.chesslan.game.model.dto.auth.LoginRequestDTO;
import com.chesslan.game.model.dto.auth.SignupRequestDTO;
import com.chesslan.game.service.interfaces.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController implements AuthControllerApi {
    private final AuthService authService;

    @Override
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthResponseDTO>> signup(@Valid @RequestBody SignupRequestDTO body,
                                                               HttpServletRequest request) {
        return ResponseFactory.ok(authService.signup(body), "Signup success", request);
    }

    @Override
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponseDTO>> login(@Valid @RequestBody LoginRequestDTO body,
                                                              HttpServletRequest request) {
        return ResponseFactory.ok(authService.login(body), "Login success", request);
    }

    @Override
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        authService.logout();
        return ResponseFactory.ok(null, "Logout success", request);
    }
}
