package com.example.store;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StoreSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(StoreSystemApplication.class, args);
    }
}
