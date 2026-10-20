package com.courtbook.notification.api;

import com.courtbook.notification.store.NotificationStore;
import com.courtbook.notification.store.NotificationStore.FeedItem;
import com.courtbook.notification.store.NotificationStore.NotificationPage;
import com.courtbook.notification.web.CurrentUser;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationController {

    private final NotificationStore store;

    public NotificationController(NotificationStore store) {
        this.store = store;
    }

    @GetMapping("/v1/notifications")
    public NotificationPage notifications(@RequestParam(defaultValue = "30") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        return store.notificationsFor(CurrentUser.from(jwt).id(), clamp(limit));
    }

    @PostMapping("/v1/notifications/read")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        store.markAllRead(CurrentUser.from(jwt).id(), Instant.now());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/v1/feed")
    public List<FeedItem> feed(@RequestParam(defaultValue = "30") int limit) {
        return store.feed(clamp(limit));
    }

    private static int clamp(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }
}
