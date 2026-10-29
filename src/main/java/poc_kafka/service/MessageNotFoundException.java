package poc_kafka.service;

public class MessageNotFoundException extends RuntimeException {

    public MessageNotFoundException(String id) {
        super("Message not found: " + id);
    }
}
