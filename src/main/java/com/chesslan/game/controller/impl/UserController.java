package com.chesslan.game.controller.impl;

import com.chesslan.game.common.response.ApiResponse;
import com.chesslan.game.controller.interfaces.UserControllerApi;
import com.chesslan.game.model.dto.user.UserCurrencyResponseDTO;
import com.chesslan.game.model.dto.user.UserProfileResponseDTO;
import com.chesslan.game.model.dto.user.UserProgressionResponseDTO;
import com.chesslan.game.service.interfaces.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController implements UserControllerApi {
    private final UserService userService;

    @Override
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponseDTO>> me(Principal principal,
                                                                  HttpServletRequest request) {
        return ResponseFactory.ok(userService.me(principal.getName()), "Get profile success", request);
    }

    @Override
    @GetMapping("/progression")
    public ResponseEntity<ApiResponse<UserProgressionResponseDTO>> progression(Principal principal,
                                                                               HttpServletRequest request) {
        return ResponseFactory.ok(
                userService.progression(principal.getName()),
                "Get progression success",
                request
        );
    }

    @Override
    @GetMapping("/currency")
    public ResponseEntity<ApiResponse<UserCurrencyResponseDTO>> currency(Principal principal,
                                                                         HttpServletRequest request) {
        return ResponseFactory.ok(userService.currency(principal.getName()), "Get currency success", request);
    }
}
