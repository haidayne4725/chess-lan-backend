package com.chesslan.game.controller.interfaces;

import com.chesslan.game.common.response.ApiResponse;
import com.chesslan.game.model.dto.user.UserCurrencyResponseDTO;
import com.chesslan.game.model.dto.user.UserProfileResponseDTO;
import com.chesslan.game.model.dto.user.UserProgressionResponseDTO;
import com.chesslan.game.model.dto.user.UpdateUserCurrencyRequestDTO;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ResponseEntity;

import java.security.Principal;

public interface UserControllerApi {
    ResponseEntity<ApiResponse<UserProfileResponseDTO>> me(Principal principal, HttpServletRequest request);
    ResponseEntity<ApiResponse<UserProgressionResponseDTO>> progression(Principal principal, HttpServletRequest request);
    ResponseEntity<ApiResponse<UserCurrencyResponseDTO>> currency(Principal principal, HttpServletRequest request);
    ResponseEntity<ApiResponse<UserCurrencyResponseDTO>> updateCurrency(
            @Valid @RequestBody UpdateUserCurrencyRequestDTO body,
            Principal principal,
            HttpServletRequest request
    );
}
