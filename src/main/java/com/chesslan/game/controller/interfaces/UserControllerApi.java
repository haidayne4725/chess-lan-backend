package com.chesslan.game.controller.interfaces;

import com.chesslan.game.common.response.ApiResponse;
import com.chesslan.game.model.dto.user.UserProfileResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

import java.security.Principal;

public interface UserControllerApi {
    ResponseEntity<ApiResponse<UserProfileResponseDTO>> me(Principal principal, HttpServletRequest request);
}
