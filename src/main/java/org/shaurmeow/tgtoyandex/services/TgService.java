package org.shaurmeow.tgtoyandex.services;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.drinkless.tdlib.TdApi;
import org.shaurmeow.tgtoyandex.clients.CustomClient;
import org.shaurmeow.tgtoyandex.handlers.UpdatesHandler;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TgService {
    private final ConvertService convertService;
    private CustomClient client;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @PostConstruct
    public void init() {
        try {
            log.info("Initializing TDLib client...");
            client = CustomClient.create(new UpdatesHandler(), null, null);
            log.info("TDLib client initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize TDLib client", e);
            throw new RuntimeException("TDLib initialization failed", e);
        }
    }

    public void startAuthorization(String phone, HttpSession session) {
        client.send(new TdApi.SetAuthenticationPhoneNumber(phone, null), null);
        session.setAttribute("tgStatus", "waiting_code");
    }

    public boolean checkCode(String code, HttpSession session) {
        client.send(new TdApi.CheckAuthenticationCode(code), null);
        session.setAttribute("tgStatus", "authorized");
        return false;
    }

    public void checkPassword(String password, HttpSession session) {
        client.send(new TdApi.CheckAuthenticationPassword(password), null);
        session.setAttribute("tgStatus", "authorized");
    }

    public void startSync(String chatName, HttpSession session, String clientId) {
        session.setAttribute("tgStatus", "syncing");
        executor.submit(() -> {
            try {
                // Load chats
                CompletableFuture<TdApi.Chats> chatsFuture = new CompletableFuture<>();
                client.send(new TdApi.LoadChats(new TdApi.ChatListMain(), 100), result -> {
                    if (result instanceof TdApi.Chats chats) {
                        chatsFuture.complete(chats);
                    } else {
                        chatsFuture.completeExceptionally(new RuntimeException("Failed to load chats"));
                    }
                });

                TdApi.Chats chats = chatsFuture.get(30, TimeUnit.SECONDS);
                CompletableFuture<Optional<TdApi.Chat>> targetChatFuture = CompletableFuture.supplyAsync(() -> {
                    for (long chatId : chats.chatIds) {
                        CompletableFuture<TdApi.Chat> chatFuture = new CompletableFuture<>();
                        client.send(new TdApi.GetChat(chatId), result -> {
                            if (result instanceof TdApi.Chat chat) {
                                chatFuture.complete(chat);
                            } else {
                                chatFuture.completeExceptionally(new RuntimeException("Failed to load chat " + chatId));
                            }
                        });
                        try {
                            TdApi.Chat chat = chatFuture.get(10, TimeUnit.SECONDS);
                            if (chatName.equalsIgnoreCase(chat.title)) {
                                return Optional.of(chat);
                            }
                        } catch (Exception e) {
                            log.error("Error fetching chat {}: {}", chatId, e.getMessage());
                        }
                    }
                    return Optional.empty();
                }, executor);

                Optional<TdApi.Chat> targetChat = targetChatFuture.get(30, TimeUnit.SECONDS);
                if (targetChat.isEmpty()) {
                    session.setAttribute("tgStatus", "error: Chat not found");
                    return;
                }

                long chatId = targetChat.get().id;

                // Get chat history
                CompletableFuture<TdApi.Messages> messagesFuture = new CompletableFuture<>();
                client.send(new TdApi.GetChatHistory(chatId, 0, 0, 100, false), result -> {
                    if (result instanceof TdApi.Messages messages) {
                        messagesFuture.complete(messages);
                    } else {
                        messagesFuture.completeExceptionally(new RuntimeException("Failed to load messages"));
                    }
                });

                TdApi.Messages messages = messagesFuture.get(30, TimeUnit.SECONDS);
                for (TdApi.Message msg : messages.messages) {
                    try {
                        TdApi.MessageContent content = msg.content;
                        byte[] fileBytes = null;
                        String mediaType = null;
                        String fileName = "file_" + msg.id;

                        if (content instanceof TdApi.MessagePhoto photoContent) {
                            TdApi.Photo photo = photoContent.photo;
                            int fileId = photo.sizes[photo.sizes.length - 1].photo.id;
                            fileBytes = downloadFileBytes(fileId);
                            mediaType = "photos";
                        } else if (content instanceof TdApi.MessageDocument docContent) {
                            TdApi.Document doc = docContent.document;
                            fileBytes = downloadFileBytes(doc.document.id);
                            mediaType = "documents";
                            fileName = doc.fileName != null ? doc.fileName : fileName;
                        } else {
                            continue;
                        }

                        if (fileBytes != null) {
                            String path = String.format("/%s/%s/%s", chatName, mediaType, fileName);
                            convertService.uploadFileToYandex(clientId, path, fileBytes);
                        }
                    } catch (Exception e) {
                        log.error("Error processing message {}: {}", msg.id, e.getMessage());
                        continue;
                    }
                }
                session.setAttribute("tgStatus", "done");
            } catch (Exception e) {
                log.error("Sync failed: {}", e.getMessage());
                session.setAttribute("tgStatus", "error: " + e.getMessage());
            }
        });
    }

    private byte[] downloadFileBytes(int fileId) {
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        client.send(new TdApi.DownloadFile(fileId, 1, 0, 100, false), obj -> {
            if (obj instanceof TdApi.File file) {
                if (file.local.isDownloadingCompleted) {
                    try {
                        Path path = Path.of(file.local.path);
                        future.complete(Files.readAllBytes(path));
                    } catch (Exception ex) {
                        future.completeExceptionally(ex);
                    }
                } else {
                    future.completeExceptionally(new RuntimeException("File download not completed"));
                }
            } else {
                future.completeExceptionally(new RuntimeException("Invalid file response"));
            }
        });
        try {
            return future.get(60, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to download file", ex);
        }
    }

    public String getStatus(HttpSession session) {
        return Optional.ofNullable(session.getAttribute("tgStatus")).orElse("idle").toString();
    }
}