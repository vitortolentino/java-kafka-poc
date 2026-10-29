package poc_kafka.controller;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import poc_kafka.controller.dto.MessageRequest;
import poc_kafka.controller.dto.MessageResponse;
import poc_kafka.service.MessageService;

@RestController
@RequestMapping("/messages")
public class MessageController {

    private final MessageService service;

    public MessageController(MessageService service) {
        this.service = service;
    }

    @GetMapping
    public PagedModel<MessageResponse> list(@RequestParam(required = false) String city, Pageable pageable) {
        return new PagedModel<>(service.list(city, pageable));
    }

    @GetMapping("/{id}")
    public MessageResponse get(@PathVariable String id) {
        return service.get(id);
    }

    /** 202 Accepted: the message was queued; a Kafka consumer saves it shortly after. */
    @PostMapping
    public ResponseEntity<MessageResponse> create(@Valid @RequestBody MessageRequest request) {
        MessageResponse accepted = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(accepted.id()).toUri();
        return ResponseEntity.accepted().location(location).body(accepted);
    }

    @PutMapping("/{id}")
    public MessageResponse update(@PathVariable String id, @Valid @RequestBody MessageRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
