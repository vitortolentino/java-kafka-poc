package poc_kafka;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;

@SpringBootTest
@EmbeddedKafka(bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class PocKafkaApplicationTests {

	@Test
	void contextLoads() {
	}

}
