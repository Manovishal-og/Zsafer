package com.zamipter.EphemeralSharingService.repository;

import com.zamipter.EphemeralSharingService.model.EphemeralSecret;

import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SecretRepository extends JpaRepository<EphemeralSecret , String>{

	@Transactional
	void deleteByExpiredAtBefore(LocalDateTime now);

	@Transactional
	void deleteByBurnAtBefore(LocalDateTime now);

	List<EphemeralSecret> findByIsViewedTrue();
}
