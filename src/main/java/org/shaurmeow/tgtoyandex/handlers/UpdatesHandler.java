package org.shaurmeow.tgtoyandex.handlers;

import org.drinkless.tdlib.TdApi;
import org.shaurmeow.tgtoyandex.clients.CustomClient;

public class UpdatesHandler implements CustomClient.ResultHandler {
    @Override
    public void onResult(TdApi.Object object) {
        if (object instanceof TdApi.UpdateAuthorizationState authState) {
            if (authState.authorizationState instanceof TdApi.AuthorizationStateWaitCode) {
                System.out.println("Ждём код");
            } else if (authState.authorizationState instanceof TdApi.AuthorizationStateReady) {
                System.out.println("Авторизация успешна");
            } else if (authState.authorizationState instanceof TdApi.AuthorizationStateWaitPassword) {
                System.out.println("Ждём пароль 2FA");
            }
        }
    }
}
