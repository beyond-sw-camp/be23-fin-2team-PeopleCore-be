package com.peoplecore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
		"com.peoplecore",
		"com.peoplecore.*"
})
@EnableJpaAuditing
@EnableScheduling
public class CollaborationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(CollaborationServiceApplication.class, args);
	}
}