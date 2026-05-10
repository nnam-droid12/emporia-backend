package com.emporia.backend.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NotificationService {

    public void sendPushNotification(String targetToken, String title, String body) {
        if (targetToken == null || targetToken.trim().isEmpty()) {
            log.error("ABORTED: Cannot send notification because FCM Token is missing or empty.");
            return;
        }

        log.info("Attempting to send FCM message to token: {}", targetToken);

        try {
            Message message = Message.builder()
                    .setToken(targetToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("✅ SUCCESS! Firebase accepted the message. Message ID: {}", response);

        } catch (FirebaseMessagingException e) {

            log.error("❌ FIREBASE REJECTED MESSAGE! Error Code: '{}', Detail: {}", e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            log.error("❌ CRITICAL ERROR sending FCM: {}", e.getMessage(), e);
        }
    }
}