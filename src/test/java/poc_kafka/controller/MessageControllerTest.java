package poc_kafka.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import poc_kafka.repository.MessageRepository;

/** Requires the docker-compose MongoDB (docker compose run --rm app mvn test). */
@SpringBootTest
@AutoConfigureMockMvc
class MessageControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    MessageRepository repository;

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    void fullCrud() throws Exception {
        String created = mvc.perform(post("/messages").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Maria Silva", "city": "Recife", "note": "first"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/messages/")))
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        mvc.perform(get("/messages/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Maria Silva"));

        mvc.perform(put("/messages/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Maria Souza", "city": "Olinda", "note": "edited"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Olinda"));

        mvc.perform(get("/messages").param("city", "Olinda"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        mvc.perform(delete("/messages/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/messages/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void rejectsShortName() throws Exception {
        mvc.perform(post("/messages").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Ana", "city": "Recife"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void rejectsMissingName() throws Exception {
        mvc.perform(post("/messages").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void deletingMissingMessageReturns404() throws Exception {
        mvc.perform(delete("/messages/{id}", "does-not-exist")).andExpect(status().isNotFound());
    }
}
