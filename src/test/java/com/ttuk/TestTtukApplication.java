package com.ttuk;

import com.ttuk.support.TestcontainersConfig;
import org.springframework.boot.SpringApplication;

public class TestTtukApplication {

    public static void main(String[] args) {
        SpringApplication.from(TtukApplication::main).with(TestcontainersConfig.class).run(args);
    }

}
