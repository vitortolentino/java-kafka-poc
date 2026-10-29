package poc_kafka.service;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import poc_kafka.controller.dto.MessageRequest;
import poc_kafka.controller.dto.MessageResponse;
import poc_kafka.domain.Message;
import poc_kafka.repository.MessageRepository;

@Service
public class MessageService {

    private final MessageRepository repository;

    public MessageService(MessageRepository repository) {
        this.repository = repository;
    }

    public Page<MessageResponse> list(String city, Pageable pageable) {
        Page<Message> page = city == null
                ? repository.findAll(pageable)
                : repository.findByCity(city, pageable);
        return page.map(MessageResponse::from);
    }

    public MessageResponse get(String id) {
        return MessageResponse.from(findDocument(id));
    }

    public MessageResponse create(MessageRequest request) {
        Instant now = Instant.now();
        Message message = new Message(null, request.name(), request.city(), request.note(), now, now);
        return MessageResponse.from(repository.save(message));
    }

    public MessageResponse update(String id, MessageRequest request) {
        Message current = findDocument(id);
        Message updated = new Message(current.id(), request.name(), request.city(), request.note(),
                current.createdAt(), Instant.now());
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
