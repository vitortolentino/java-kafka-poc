package poc_kafka.messaging;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    public static final String TOPIC = "messages";
    public static final String DEAD_LETTER_TOPIC = TOPIC + ".DLT";
    /** Consumers A and B share this group, so each record is processed by only one of them. */
    public static final String CONSUMER_GROUP = "message-savers";

    /** Two partitions: one per consumer in the group. Replicas use the broker default. */
    private static final int PARTITIONS = 2;

    @Bean
    NewTopic messagesTopic() {
        return TopicBuilder.name(TOPIC).partitions(PARTITIONS).build();
    }

    /** Same partition count as the main topic: a failed record goes to the same partition number. */
    @Bean
    NewTopic messagesDeadLetterTopic() {
        return TopicBuilder.name(DEAD_LETTER_TOPIC).partitions(PARTITIONS).build();
    }

    /**
     * Values are JSON, except raw byte[] values: a record that failed deserialization is
     * forwarded to the dead-letter topic with its original bytes. Boot builds the
     * KafkaTemplate on top of this factory.
     */
    @Bean
    ProducerFactory<Object, Object> producerFactory(KafkaProperties properties) {
        Map<Class<?>, Serializer<?>> valueSerializers = new LinkedHashMap<>();
        valueSerializers.put(byte[].class, new ByteArraySerializer());
        valueSerializers.put(Object.class, new JacksonJsonSerializer<>().noTypeInfo());
        // Null key serializer: falls back to Boot's key-serializer property (StringSerializer)
        return new DefaultKafkaProducerFactory<>(properties.buildProducerProperties(),
                null, new DelegatingByTypeSerializer(valueSerializers, true));
    }

    /**
     * Retries a failed record twice (1s apart) and then publishes it to the dead-letter topic.
     * Deserialization errors are not retryable and go straight to the dead-letter topic.
     */
    @Bean
    DefaultErrorHandler errorHandler(KafkaOperations<Object, Object> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(DEAD_LETTER_TOPIC, record.partition()));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2));
    }
}
