package com.zamipter.EphemeralSharingService.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zamipter.EphemeralSharingService.model.User;

public interface UserRepository extends JpaRepository<User , String>{

	java.util.Optional<User> findByUsernameHash(String hash);
    java.util.Optional<User> findByApiToken(String token);
	java.util.Optional<User> findByEmailHash(String email);
} 

	

