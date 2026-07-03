package com.api.trekkey;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class TrekkeyApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrekkeyApplication.class, args);
    }
}
