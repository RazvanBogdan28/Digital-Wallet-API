package com.razvan.digital_wallet_api.dto;

public class RefreshTokenResponse {

    private String accessToken;

    public RefreshTokenResponse(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getAccessToken() {
        return accessToken;
    }
}
