package com.ttuk;

import com.ttuk.support.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfig.class)
@SpringBootTest
class TtukApplicationTests {

    @Test
    void contextLoads() {
    }

}
