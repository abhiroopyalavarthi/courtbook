package com.courtbook.notification;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("courtbook")
public record NotificationProperties(String timeZone, Events events, Aws aws, Dynamodb dynamodb) {

    public ZoneId zone() {
        return ZoneId.of(timeZone);
    }

    public record Events(boolean consume, String queueName, int waitSeconds) {
    }

    public record Aws(String endpoint) {
    }

    public record Dynamodb(String endpoint, String tablePrefix, boolean createTables) {
    }
}
