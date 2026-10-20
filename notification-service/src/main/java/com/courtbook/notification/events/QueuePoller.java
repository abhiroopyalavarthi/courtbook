package com.courtbook.notification.events;

import com.courtbook.notification.NotificationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

/**
 * Long-polls the queue and deletes each message only after it has been
 * handled. If this service is down, events simply wait in the queue.
 */
@Component
@ConditionalOnProperty(name = "courtbook.events.consume", havingValue = "true", matchIfMissing = true)
public class QueuePoller {

    private static final Logger log = LoggerFactory.getLogger(QueuePoller.class);

    private final SqsClient sqs;
    private final EventConsumer consumer;
    private final NotificationProperties props;
    private volatile String queueUrl;

    public QueuePoller(SqsClient sqs, EventConsumer consumer, NotificationProperties props) {
        this.sqs = sqs;
        this.consumer = consumer;
        this.props = props;
    }

    @Scheduled(fixedDelay = 100)
    public void poll() {
        ReceiveMessageResponse response;
        try {
            response = sqs.receiveMessage(r -> r.queueUrl(queueUrl())
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(props.events().waitSeconds()));
        } catch (RuntimeException e) {
            log.warn("Couldn't reach the queue, retrying: {}", e.toString());
            queueUrl = null;
            sleepQuietly(2_000);
            return;
        }
        for (Message message : response.messages()) {
            try {
                consumer.handle(message.body());
                sqs.deleteMessage(r -> r.queueUrl(queueUrl()).receiptHandle(message.receiptHandle()));
            } catch (RuntimeException e) {
                // Not deleted: it reappears after the visibility timeout and is retried.
                log.warn("Failed to handle message {}, will retry: {}", message.messageId(), e.toString());
            }
        }
    }

    private String queueUrl() {
        if (queueUrl == null) {
            queueUrl = sqs.getQueueUrl(r -> r.queueName(props.events().queueName())).queueUrl();
        }
        return queueUrl;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
