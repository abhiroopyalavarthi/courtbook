package com.courtbook.tournament.events;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("courtbook.events")
public record EventsProperties(boolean enabled, String queueName, long relayIntervalMs) {
}
