package pockafka.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import pockafka.domain.Message;

public interface MessageRepository extends MongoRepository<Message, String> {

    Page<Message> findByCity(String city, Pageable pageable);
}
