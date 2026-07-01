package com.chesslan.game.service.impl;

import com.chesslan.game.common.exception.ApiException;
import com.chesslan.game.common.exception.ErrorCode;
import com.chesslan.game.mapper.GameMapper;
import com.chesslan.game.model.dto.user.UserCurrencyResponseDTO;
import com.chesslan.game.model.dto.user.UserProfileResponseDTO;
import com.chesslan.game.model.dto.user.UserProgressionResponseDTO;
import com.chesslan.game.model.dto.user.UpdateUserCurrencyRequestDTO;
import com.chesslan.game.model.entity.UserEntity;
import com.chesslan.game.repository.LevelRequirementRepository;
import com.chesslan.game.repository.UserRepository;
import com.chesslan.game.service.interfaces.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final LevelRequirementRepository levelRequirementRepository;
    private final GameMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponseDTO me(String username) {
        UserEntity user = requireUser(username);
        return mapper.toUserProfile(user, nextLevelExp(user));
    }

    @Override
    @Transactional(readOnly = true)
    public UserProgressionResponseDTO progression(String username) {
        UserEntity user = requireUser(username);
        Long nextLevelExp = nextLevelExp(user);
        return mapper.toUserProgression(user, nextLevelExp, calculateProgressPercent(user.getExp(), nextLevelExp));
    }

    @Override
    @Transactional(readOnly = true)
    public UserCurrencyResponseDTO currency(String username) {
        return mapper.toUserCurrency(requireUser(username));
    }

    @Override
    @Transactional
    public UserCurrencyResponseDTO updateCurrency(String username, UpdateUserCurrencyRequestDTO request) {
        UserEntity user = requireUser(username);
        user.setGold(request.gold());
        return mapper.toUserCurrency(userRepository.save(user));
    }

    private UserEntity requireUser(String username) {
        return userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));
    }

    private Long nextLevelExp(UserEntity user) {
        return levelRequirementRepository.findFirstByLevelGreaterThanOrderByLevelAsc(user.getLevel())
                .map(levelRequirement -> levelRequirement.getRequiredExp())
                .orElse(null);
    }

    private int calculateProgressPercent(Long currentExp, Long nextLevelExp) {
        if (nextLevelExp == null || nextLevelExp <= 0) {
            return 100;
        }
        long rawPercent = (currentExp * 100) / nextLevelExp;
        return (int) Math.max(0, Math.min(100, rawPercent));
    }
}
