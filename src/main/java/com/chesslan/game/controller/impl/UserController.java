package com.chesslan.game.controller.impl;

import com.chesslan.game.common.response.ApiResponse;
import com.chesslan.game.controller.interfaces.UserControllerApi;
import com.chesslan.game.model.dto.user.UserProfileResponseDTO;
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
}
