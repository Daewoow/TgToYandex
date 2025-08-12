package org.shaurmeow.tgtoyandex.controllers;

import lombok.RequiredArgsConstructor;
import org.shaurmeow.tgtoyandex.services.ConvertService;
import org.shaurmeow.tgtoyandex.services.TgService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.util.Map;

@RestController
@RequestMapping("/api/tg")
@RequiredArgsConstructor
public class TgController {

    private final TgService tgService;
    private final ConvertService convertService;

    @PostMapping("/start")
    public ResponseEntity<?> start(@RequestBody Map<String, String> payload, HttpSession session) {
        String phone = payload.get("phone");
        tgService.startAuthorization(phone, session);
        return ResponseEntity.ok(Map.of("status", "code_sent"));
    }

    @PostMapping("/code")
    public ResponseEntity<?> checkCode(@RequestBody Map<String, String> payload, HttpSession session) {
        String code = payload.get("code");
        boolean needPassword = tgService.checkCode(code, session);
        return ResponseEntity.ok(Map.of("needPassword", needPassword));
    }

    @PostMapping("/password")
    public ResponseEntity<?> sendPassword(@RequestBody Map<String, String> payload, HttpSession session) {
        String password = payload.get("password");
        tgService.checkPassword(password, session);
        return ResponseEntity.ok(Map.of("status", "authorized"));
    }

    @PostMapping("/sync")
    public ResponseEntity<?> sync(@RequestBody Map<String, String> payload, HttpSession session) {
        String chatName = payload.get("chatName");
        String clientId = convertService.getClientId();
        tgService.startSync(chatName, session, clientId);
        return ResponseEntity.ok(Map.of("status", "sync_started"));
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(HttpSession session) {
        String status = tgService.getStatus(session);
        return ResponseEntity.ok(Map.of("status", status));
    }
}
