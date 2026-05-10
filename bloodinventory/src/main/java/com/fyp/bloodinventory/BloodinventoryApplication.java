package com.fyp.bloodinventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BloodinventoryApplication {

	public static void main(String[] args) {
		SpringApplication.run(BloodinventoryApplication.class, args);
	}

}
