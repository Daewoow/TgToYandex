package org.shaurmeow.tgtoyandex.repository;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class User {
    private String yandexToken;
    private String tgToken;
}
