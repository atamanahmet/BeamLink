package com.atamanahmet.beamlink.agent;

import com.atamanahmet.beamlink.agent.service.RegistrationService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootTest
class BeamlinkAgentApplicationTests {

    @MockBean
    private RegistrationService registrationService;

    @BeforeAll
    static void setup() throws IOException {
        Files.createDirectories(Path.of("./data/database"));
    }

    @Test
    void contextLoads() {
    }

}
