package com.chesslan.game.service.impl;

import com.chesslan.game.common.exception.ApiException;
import com.chesslan.game.common.exception.ErrorCode;
import com.chesslan.game.mapper.GameMapper;
import com.chesslan.game.model.dto.user.UserProfileResponseDTO;
import com.chesslan.game.repository.UserRepository;
import com.chesslan.game.service.interfaces.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final GameMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponseDTO me(String username) {
        return userRepository.findByUsernameIgnoreCase(username)
                .map(mapper::toUserProfile)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));
    }
}
