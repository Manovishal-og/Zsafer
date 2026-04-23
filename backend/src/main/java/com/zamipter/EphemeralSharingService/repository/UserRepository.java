package com.zamipter.EphemeralSharingService.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zamipter.EphemeralSharingService.model.User;

public interface UserRepository extends JpaRepository<User , String>{

	User findByUsernameHash(String hash);
} 

	

