package poc_kafka.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import poc_kafka.domain.Message;

public interface MessageRepository extends MongoRepository<Message, String> {

    Page<Message> findByCity(String city, Pageable pageable);
}
