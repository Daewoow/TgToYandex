package org.shaurmeow.tgtoyandex.services;

import lombok.Getter;
import lombok.Setter;
import org.shaurmeow.tgtoyandex.repository.ConvertRepository;
import org.shaurmeow.tgtoyandex.repository.User;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;
import redis.clients.jedis.Jedis;
import com.google.gson.Gson;

import java.util.Map;
import java.util.Objects;

@Getter
@Setter
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

    public void uploadFileToYandex(String clientId, String path, byte[] data) {
        String token = loadUserToken(clientId).getYandexToken();
        RestTemplate rest = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<Map> resp = rest.exchange(
                "https://cloud-api.yandex.net/v1/disk/resources/upload?path="
                        + UriUtils.encode(path, "UTF-8") + "&overwrite=true",
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        String href = (String) Objects.requireNonNull(resp.getBody()).get("href");
        rest.put(href, data);
    }

    private User loadUserToken(String clientId) {
        try (Jedis jedis = new Jedis("localhost")) {
            return new Gson().fromJson(jedis.get(clientId), User.class);
        }
    }
}
