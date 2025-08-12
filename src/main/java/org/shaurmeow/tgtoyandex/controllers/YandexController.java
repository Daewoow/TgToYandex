package org.shaurmeow.tgtoyandex.controllers;

import lombok.AllArgsConstructor;
import org.shaurmeow.tgtoyandex.repository.ConvertRepository;
import org.shaurmeow.tgtoyandex.repository.YandexUser;
import org.shaurmeow.tgtoyandex.services.ConvertService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.Jedis;

@RestController
@RequestMapping(path = "api/yandex")
@AllArgsConstructor
public class YandexController {
    private final ConvertService convertService;

    @PostMapping
    @RequestMapping(path = "/save")
    public ResponseEntity<String> save(@RequestBody YandexUser yandexUser) {
        convertService.saveYandexToken(yandexUser.getAccessToken(), yandexUser.getClientId());
        return ResponseEntity.ok(yandexUser.getClientId());
    }
}
