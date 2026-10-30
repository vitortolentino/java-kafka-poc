package pockafka.messaging;

import org.springframework.kafka.core.KafkaOperations;
import org.springframework.stereotype.Component;

@Component
public class MessageProducer {

    private final KafkaOperations<Object, Object> kafka;

    public MessageProducer(KafkaOperations<Object, Object> kafka) {
        this.kafka = kafka;
    }

    /**
     * The message id is the record key: records with the same key always land on the
     * same partition, which keeps their order. Blocks until the broker acknowledges.
     */
    public void publish(MessageCreatedEvent event) {
        kafka.send(KafkaConfig.TOPIC, event.id(), event).join();
    }
}
