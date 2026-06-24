package com.chesslan.game.controller.interfaces;

import com.chesslan.game.common.response.ApiResponse;
import com.chesslan.game.model.dto.auth.AuthResponseDTO;
import com.chesslan.game.model.dto.auth.LoginRequestDTO;
import com.chesslan.game.model.dto.auth.SignupRequestDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

public interface AuthControllerApi {
    ResponseEntity<ApiResponse<AuthResponseDTO>> signup(@Valid @RequestBody SignupRequestDTO body,
                                                        HttpServletRequest request);
    ResponseEntity<ApiResponse<AuthResponseDTO>> login(@Valid @RequestBody LoginRequestDTO body,
                                                       HttpServletRequest request);
    ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request);
}
