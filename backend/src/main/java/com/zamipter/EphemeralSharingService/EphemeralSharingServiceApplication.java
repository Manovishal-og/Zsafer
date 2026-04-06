package com.zamipter.EphemeralSharingService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.zamipter.EphemeralSharingService.Service.KeyGenerationService;

@SpringBootApplication
@EnableScheduling
public class EphemeralSharingServiceApplication {


	public static void main(String[] args) {
		
		SpringApplication.run(EphemeralSharingServiceApplication.class, args);
	}

}
