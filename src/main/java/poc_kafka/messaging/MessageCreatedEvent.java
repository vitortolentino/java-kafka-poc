package poc_kafka.messaging;

import java.time.Instant;

/** Payload published to the messages topic when a message is created via POST. */
public record MessageCreatedEvent(
        String id,
        String name,
        String city,
        String note,
        Instant createdAt) {
}
