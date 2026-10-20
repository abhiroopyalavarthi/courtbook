package com.courtbook.booking.events;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

/**
 * Same SDK code locally and on AWS. Locally the endpoint points at ElasticMQ;
 * on EC2 it is empty and credentials come from the instance's IAM role.
 */
@Configuration
@ConditionalOnProperty(name = "courtbook.events.enabled", havingValue = "true")
public class SqsConfig {

    @Bean(destroyMethod = "close")
    SqsClient sqsClient(@Value("${courtbook.aws.endpoint:}") String endpoint,
            @Value("${AWS_REGION:us-east-1}") String region) {
        SqsClientBuilder builder = SqsClient.builder().region(Region.of(region));
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }
}
