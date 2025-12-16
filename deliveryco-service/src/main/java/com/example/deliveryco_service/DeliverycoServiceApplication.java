package com.example.deliveryco_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DeliverycoServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(DeliverycoServiceApplication.class, args);
	}

}
