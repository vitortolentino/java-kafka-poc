package poc_kafka.controller.dto;

import java.time.Instant;

import poc_kafka.domain.Message;

public record MessageResponse(
        String id,
        String name,
        String city,
        String note,
        Instant createdAt,
        Instant updatedAt) {

    public static MessageResponse from(Message message) {
        return new MessageResponse(
                message.id(),
                message.name(),
                message.city(),
                message.note(),
                message.createdAt(),
                message.updatedAt());
    }
}
