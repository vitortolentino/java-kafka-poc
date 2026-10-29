package poc_kafka.domain;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Document stored in the "messages" collection.
 * The city field is indexed because it is used as a filter when listing.
 * savedBy records which Kafka consumer persisted the document.
 */
@Document("messages")
public record Message(
        @Id String id,
        String name,
        @Indexed String city,
        String note,
        Instant createdAt,
        Instant updatedAt,
        String savedBy) {
}
