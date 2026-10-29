package poc_kafka.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.utils.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import poc_kafka.controller.dto.MessageRequest;
import poc_kafka.domain.Message;
import poc_kafka.repository.MessageRepository;
import poc_kafka.service.MessageService;

/** Requires the docker-compose MongoDB; Kafka runs embedded in the test JVM. */
@SpringBootTest
@EmbeddedKafka(bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class MessageKafkaFlowTest {

    private static final int MESSAGES = 20;

    @Autowired
    MessageService service;

    @Autowired
    MessageRepository repository;

    @Autowired
    KafkaListenerEndpointRegistry registry;

    @Autowired
    KafkaOperations<Object, Object> kafka;

    @Autowired
    EmbeddedKafkaBroker broker;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        // Each consumer of the group must own one of the two partitions before we publish.
        // Right after startup the first consumer to join may briefly hold both, until the
        // group rebalances (ContainerTestUtils.waitForAssignment fails fast on that instead of waiting).
        await().atMost(Duration.ofSeconds(30)).until(() ->
                assignedCount(MessageConsumers.CONSUMER_A) == 1 && assignedCount(MessageConsumers.CONSUMER_B) == 1);
    }

    @Test
    void eachMessageIsSavedOnceByTheConsumerThatOwnsItsPartition() {
        for (int i = 0; i < MESSAGES; i++) {
            service.create(new MessageRequest("Message " + i, "Recife", null));
        }

        await().atMost(Duration.ofSeconds(15)).until(() -> repository.count() == MESSAGES);
        List<Message> saved = repository.findAll();

        Map<Integer, String> ownerByPartition = new HashMap<>();
        for (String consumer : List.of(MessageConsumers.CONSUMER_A, MessageConsumers.CONSUMER_B)) {
            for (TopicPartition partition : container(consumer).getAssignedPartitions()) {
                ownerByPartition.put(partition.partition(), consumer);
            }
        }
        // Same formula as Kafka's default partitioner for keyed records
        assertThat(saved).allSatisfy(message -> assertThat(message.savedBy())
                .isEqualTo(ownerByPartition.get(partitionOf(message.id()))));

        // With 20 random keys, both partitions (and so both consumers) are virtually always used
        Set<String> consumers = saved.stream().map(Message::savedBy).collect(Collectors.toSet());
        assertThat(consumers).containsExactlyInAnyOrder(MessageConsumers.CONSUMER_A, MessageConsumers.CONSUMER_B);
    }

    @Test
    void malformedRecordGoesToTheDeadLetterTopic() {
        byte[] invalidJson = "not json".getBytes(StandardCharsets.UTF_8);
        kafka.send(KafkaConfig.TOPIC, "poison", invalidJson).join();

        Map<String, Object> props = KafkaTestUtils.consumerProps(broker, "dlt-test", false);
        try (Consumer<String, byte[]> consumer = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new ByteArrayDeserializer()).createConsumer()) {
            consumer.subscribe(List.of(KafkaConfig.DEAD_LETTER_TOPIC));
            ConsumerRecord<String, byte[]> record = KafkaTestUtils.getSingleRecord(consumer,
                    KafkaConfig.DEAD_LETTER_TOPIC, Duration.ofSeconds(15));

            assertThat(record.key()).isEqualTo("poison");
            assertThat(record.value()).isEqualTo(invalidJson);
            assertThat(new String(record.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_FQCN).value(),
                    StandardCharsets.UTF_8)).isEqualTo(DeserializationException.class.getName());
        }
        assertThat(repository.count()).isZero();
    }

    private MessageListenerContainer container(String id) {
        return registry.getListenerContainer(id);
    }

    private int assignedCount(String id) {
        var partitions = container(id).getAssignedPartitions();
        return partitions == null ? 0 : partitions.size();
    }

    private static int partitionOf(String key) {
        return Utils.toPositive(Utils.murmur2(key.getBytes(StandardCharsets.UTF_8))) % 2;
    }
}
