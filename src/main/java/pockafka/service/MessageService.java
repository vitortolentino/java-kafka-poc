package pockafka.service;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import pockafka.controller.dto.MessageRequest;
import pockafka.controller.dto.MessageResponse;
import pockafka.domain.Message;
import pockafka.messaging.MessageCreatedEvent;
import pockafka.messaging.MessageProducer;
import pockafka.repository.MessageRepository;

@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private final MessageRepository repository;
    private final MessageProducer producer;

    public MessageService(MessageRepository repository, MessageProducer producer) {
        this.repository = repository;
        this.producer = producer;
    }

    public Page<MessageResponse> list(String city, Pageable pageable) {
        Page<Message> page = city == null ? repository.findAll(pageable) : repository.findByCity(city, pageable);
        return page.map(MessageResponse::from);
    }

    public MessageResponse get(String id) {
        return MessageResponse.from(findDocument(id));
    }

    /**
     * Does not touch the database: publishes the message to Kafka and one of the consumers
     * persists it. The id is generated here so the client can poll GET /messages/{id}.
     */
    public MessageResponse create(MessageRequest request) {
        Instant now = Instant.now();
        var event = new MessageCreatedEvent(
                UUID.randomUUID().toString(), request.name(), request.city(), request.note(), now);
        producer.publish(event);
        return new MessageResponse(event.id(), event.name(), event.city(), event.note(), now, now, null);
    }

    /**
     * Idempotent: Kafka delivers at least once, so a redelivered event must not overwrite
     * a document that was already saved (and possibly edited afterwards).
     */
    public void saveFromEvent(MessageCreatedEvent event, String consumer) {
        if (repository.existsById(event.id())) {
            log.info("Message {} already saved, ignoring redelivery", event.id());
            return;
        }
        repository.save(new Message(
                event.id(), event.name(), event.city(), event.note(), event.createdAt(), event.createdAt(), consumer));
    }

    public MessageResponse update(String id, MessageRequest request) {
        Message current = findDocument(id);
        Message updated = new Message(
                current.id(),
                request.name(),
                request.city(),
                request.note(),
                current.createdAt(),
                Instant.now(),
                current.savedBy());
        return MessageResponse.from(repository.save(updated));
    }

    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new MessageNotFoundException(id);
        }
        repository.deleteById(id);
    }

    private Message findDocument(String id) {
        return repository.findById(id).orElseThrow(() -> new MessageNotFoundException(id));
    }
}
