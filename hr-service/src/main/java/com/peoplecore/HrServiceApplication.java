package com.peoplecore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
//확인(entity추가 후 삭제)
@SpringBootApplication(scanBasePackages = {"com.peoplecore.hr_service", "com.peoplecore.common", "com.peoplecore.employee", "com.peoplecore.auth", "com.peoplecore.grade", "com.peoplecore.title", "com.peoplecore.company"})
@EnableJpaAuditing
public class HrServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(HrServiceApplication.class, args);
	}

}
