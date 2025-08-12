package org.shaurmeow.tgtoyandex.services;

import org.shaurmeow.tgtoyandex.repository.ConvertRepository;
import org.shaurmeow.tgtoyandex.repository.User;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import com.google.gson.Gson;

@Service
public class ConvertService {
    private final ConvertRepository convertRepository;
    private String yaToken;
    private String clientId;
    private String tgToken;

    public ConvertService(ConvertRepository convertRepository) {
        this.convertRepository = convertRepository;
    }

    public String convert() {
        return "Ok";
    }

    public void saveYandexToken (String yaToken, String clientId) {
        this.clientId = clientId;
        this.yaToken = yaToken;
    }

    public void saveTgToken (String tgToken) {
        this.tgToken = tgToken;
        saveTokens();
    }

    private void saveTokens() {
        try (Jedis jedis = new Jedis("localhost")) {
            User user = new User(yaToken, tgToken);
            String json = new Gson().toJson(user);
            jedis.set(clientId, json);
        }
    }
}
