package com.zamipter.EphemeralSharingService.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.zamipter.EphemeralSharingService.model.User;
import com.zamipter.EphemeralSharingService.repository.UserRepository;

/**
 * UserService
 */
@Service
public class UserService {


	@Autowired
	private KeyGenerationService keyGenerationService;

	@Autowired
	private UserRepository userRepository;



	public User validateUserSession(String apiToken, String usernameHash) throws Exception {
		if (apiToken == null || apiToken.isBlank()) {
			throw new RuntimeException("Missing API Token");
		}

		// 1. Find user by token
		User user = userRepository.findByApiToken(apiToken)
		.orElseThrow(() -> new RuntimeException("Invalid or expired session token"));


		// 2. Verify the token belongs to this specific username
		if (!user.getUsernameHash().equals(usernameHash)) {
			throw new RuntimeException("Token does not match the provided username");
		}

		return user;
	}

	public User decryptUser(User user , String apiToken){
		try{
			User decryptedUser = new User();
			decryptedUser.setId(user.getId());
			decryptedUser.setPasswordHash(user.getPasswordHash());
			decryptedUser.setUsernameHash(user.getUsernameHash());
			decryptedUser.setEmail(
				keyGenerationService.decrypt(user.getEmail(), apiToken)
			);
			decryptedUser.setUsername(
				keyGenerationService.decrypt(user.getUsername(), apiToken)
			);
			decryptedUser.setNotification(
				keyGenerationService.decrypt(user.getNotification(), apiToken)
			);
			decryptedUser.setMySecrets(user.getMySecrets());
			return decryptedUser;
		}
		catch(Exception e){
			System.err.println("Decryption failed for user ID: " + user.getId());
			e.printStackTrace(); 
			throw new RuntimeException("Could not decrypt user data. Internal error or invalid token.", e);

		}
	}

}
