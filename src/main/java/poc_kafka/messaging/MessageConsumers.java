package poc_kafka.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import poc_kafka.service.MessageService;

/**
 * Two listeners in the same consumer group: Kafka assigns each partition to one of them,
 * so every message is saved exactly once, either by Consumer A or by Consumer B.
 */
@Component
public class MessageConsumers {

    public static final String CONSUMER_A = "consumer-a";
    public static final String CONSUMER_B = "consumer-b";

    private static final Logger log = LoggerFactory.getLogger(MessageConsumers.class);

    private final MessageService service;

    public MessageConsumers(MessageService service) {
        this.service = service;
    }

    @KafkaListener(id = CONSUMER_A, groupId = KafkaConfig.CONSUMER_GROUP, topics = KafkaConfig.TOPIC)
    void consumerA(MessageCreatedEvent event, @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        save(event, CONSUMER_A, partition);
    }

    @KafkaListener(id = CONSUMER_B, groupId = KafkaConfig.CONSUMER_GROUP, topics = KafkaConfig.TOPIC)
    void consumerB(MessageCreatedEvent event, @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        save(event, CONSUMER_B, partition);
    }

    /** Only logs: in a real system someone would inspect, fix and replay these records. */
    @KafkaListener(id = "dead-letter-logger", topics = KafkaConfig.DEAD_LETTER_TOPIC,
            properties = "value.deserializer=org.apache.kafka.common.serialization.ByteArrayDeserializer")
    void deadLetter(ConsumerRecord<String, byte[]> record) {
        log.warn("Dead-letter record: partition={} offset={} key={}",
                record.partition(), record.offset(), record.key());
    }

    private void save(MessageCreatedEvent event, String consumer, int partition) {
        log.info("{} received message {} from partition {}", consumer, event.id(), partition);
        service.saveFromEvent(event, consumer);
    }
}
