package server.poptato.external.firebase.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Profile("!test")
public class FcmNotificationScheduler {
    private final FcmNotificationService fcmNotificationService;

    /**
     * 비활성 FCM 토큰을 삭제한다.
     */
    @Scheduled(cron = "${scheduling.fcmCleanupCron}")
    public void deleteOldFcmTokens() {
        fcmNotificationService.deleteOldFcmTokens();
    }

    /**
     * 마감일 알림을 전송한다.
     */
    @Scheduled(cron = "${scheduling.deadlineNotificationCron}")
    @Async
    public void sendDeadlineNotifications() {
        fcmNotificationService.sendDeadlineNotifications();
    }
}
