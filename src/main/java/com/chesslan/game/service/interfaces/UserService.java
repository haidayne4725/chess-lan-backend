package com.chesslan.game.service.interfaces;

import com.chesslan.game.model.dto.user.UserProfileResponseDTO;
import com.chesslan.game.model.dto.user.UserCurrencyResponseDTO;
import com.chesslan.game.model.dto.user.UserProgressionResponseDTO;

public interface UserService {
    UserProfileResponseDTO me(String username);
    UserProgressionResponseDTO progression(String username);
    UserCurrencyResponseDTO currency(String username);
}
