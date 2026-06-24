package com.chesslan.game.service.impl;

import com.chesslan.game.common.exception.ApiException;
import com.chesslan.game.common.exception.ErrorCode;
import com.chesslan.game.common.security.JwtService;
import com.chesslan.game.mapper.GameMapper;
import com.chesslan.game.model.dto.auth.AuthResponseDTO;
import com.chesslan.game.model.dto.auth.LoginRequestDTO;
import com.chesslan.game.model.dto.auth.SignupRequestDTO;
import com.chesslan.game.model.entity.UserEntity;
import com.chesslan.game.repository.UserRepository;
import com.chesslan.game.service.interfaces.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final GameMapper mapper;

    @Override
    @Transactional
    public AuthResponseDTO signup(SignupRequestDTO request) {
        String username = request.username().trim();
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ApiException(ErrorCode.USERNAME_EXISTS);
        }

        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setElo(1200);
        return createAuthResponse(userRepository.save(user));
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponseDTO login(LoginRequestDTO request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username().trim(), request.password()));
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }

        UserEntity user = userRepository.findByUsernameIgnoreCase(request.username().trim())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS));
        return createAuthResponse(user);
    }

    @Override
    public void logout() {
        // JWT is stateless. Unity removes the token from PlayerPrefs after this response.
    }

    private AuthResponseDTO createAuthResponse(UserEntity user) {
        return new AuthResponseDTO(
                jwtService.generateAccessToken(user),
                jwtService.accessTokenExpiresInSeconds(),
                mapper.toUserProfile(user)
        );
    }
}
