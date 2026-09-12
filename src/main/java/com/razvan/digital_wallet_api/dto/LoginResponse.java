package com.razvan.digital_wallet_api.dto;

public class LoginResponse {

    private Long userId;
    private String email;
    private String accessToken;
    private String refreshToken;

    public LoginResponse(
            Long userId,
            String email,
            String accessToken,
            String refreshToken
    ) {
        this.userId = userId;
        this.email = email;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }
}