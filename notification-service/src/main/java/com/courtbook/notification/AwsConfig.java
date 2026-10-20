package com.courtbook.notification;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

/**
 * Identical SDK clients locally and on AWS. Only the endpoints differ:
 * ElasticMQ and DynamoDB Local in Docker, the real services on EC2.
 */
@Configuration
public class AwsConfig {

    @Bean(destroyMethod = "close")
    SqsClient sqsClient(NotificationProperties props, @Value("${AWS_REGION:us-east-1}") String region) {
        SqsClientBuilder builder = SqsClient.builder().region(Region.of(region));
        String endpoint = props.aws().endpoint();
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    DynamoDbClient dynamoDbClient(NotificationProperties props, @Value("${AWS_REGION:us-east-1}") String region) {
        DynamoDbClientBuilder builder = DynamoDbClient.builder().region(Region.of(region));
        String endpoint = props.dynamodb().endpoint();
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }
}
