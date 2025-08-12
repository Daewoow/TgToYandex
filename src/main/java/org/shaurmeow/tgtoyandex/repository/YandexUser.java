package org.shaurmeow.tgtoyandex.repository;

import lombok.Getter;

@Getter
public class YandexUser {
    private String accessToken;
    private String clientId;
    private String expiresIn;
}
