package com.QuickPool;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableAsync
@org.springframework.scheduling.annotation.EnableScheduling
public class Quickpool {

	public static void main(String[] args) {
		SpringApplication.run(Quickpool.class, args);
	}

}
