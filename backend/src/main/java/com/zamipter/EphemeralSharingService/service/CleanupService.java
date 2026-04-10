
package com.zamipter.EphemeralSharingService.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.zamipter.EphemeralSharingService.model.EphemeralSecret;
import com.zamipter.EphemeralSharingService.repository.SecretRepository;

@Service
public class CleanupService {

	@Autowired
	private SecretRepository repository;

	@Scheduled(fixedDelay = 3600000 )
	public void cleanUnusedData(){
		repository.deleteByExpiredAtBefore(LocalDateTime.now());

	}

	@Scheduled(fixedDelay = 30000)
	public void performMaintenance(){
		repository.deleteByBurnAtBefore(LocalDateTime.now());
	}
	
}
