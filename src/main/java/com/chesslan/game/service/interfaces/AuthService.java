package com.chesslan.game.service.interfaces;

import com.chesslan.game.model.dto.auth.AuthResponseDTO;
import com.chesslan.game.model.dto.auth.LoginRequestDTO;
import com.chesslan.game.model.dto.auth.SignupRequestDTO;

public interface AuthService {
    AuthResponseDTO signup(SignupRequestDTO request);
    AuthResponseDTO login(LoginRequestDTO request);
    void logout();
}
