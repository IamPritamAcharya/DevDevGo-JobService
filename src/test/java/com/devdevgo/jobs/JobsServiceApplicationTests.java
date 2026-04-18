package com.devdevgo.jobs;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "firebase.enabled=false",
        "jobs.sync.enabled=false"
})
class JobsServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
