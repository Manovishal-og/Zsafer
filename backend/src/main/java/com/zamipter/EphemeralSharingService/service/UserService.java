package com.zamipter.EphemeralSharingService.service;

import java.util.ArrayList;
import java.util.List;

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
		User user = userRepository.findByApiToken(keyGenerationService.hashKey(apiToken))
		.orElseThrow(() -> new RuntimeException("Invalid or expired session token"));


		// 2. Verify the token belongs to this specific username
		if (!user.getUsernameHash().equals(usernameHash)) {
			throw new RuntimeException("Token does not match the provided username");
		}

		return user;
	}

	public User decryptUser(User user) {
		try {
			User decryptedUser = new User();

			// Pass-through fields
			decryptedUser.setId(user.getId());
			decryptedUser.setUsernameHash(user.getUsernameHash());
			decryptedUser.setEmailHash(user.getEmailHash());
			decryptedUser.setApiToken(user.getApiToken());
			decryptedUser.setUsername(user.getUsername());
			decryptedUser.setMySecrets(user.getMySecrets());

			decryptedUser.setEmail(
				keyGenerationService.decrypt(user.getEmail())
			);
			decryptedUser.setUsername(
				keyGenerationService.decrypt(user.getUsername())
			);

			List<String> plainNotifications = new ArrayList<>();
			if (user.getNotification() != null) {
				for (String enc : user.getNotification()) {
					plainNotifications.add(keyGenerationService.decrypt(enc));
				}
			}
			decryptedUser.setNotification(plainNotifications);

			return decryptedUser;

		} catch (Exception e) {
			System.err.println("Decryption failed for user ID: " + user.getId());
			e.printStackTrace();
			throw new RuntimeException("Could not decrypt user data.", e);
		}
	}


	public User encryptUser(User user) {
		try {
			User encryptedUser = new User();

			encryptedUser.setId(user.getId());
			encryptedUser.setUsernameHash(user.getUsernameHash());
			encryptedUser.setEmailHash(user.getEmailHash());
			encryptedUser.setApiToken(user.getApiToken());
			encryptedUser.setMySecrets(user.getMySecrets());

			encryptedUser.setEmail(
				keyGenerationService.encrypt(user.getEmail())
			);
			encryptedUser.setUsername(
				keyGenerationService.encrypt(user.getUsername())
			);

			// Encrypt each notification individually
			List<String> encryptedNotifications = new ArrayList<>();
			if (user.getNotification() != null) {
				for (String plain : user.getNotification()) {
					encryptedNotifications.add(keyGenerationService.encrypt(plain));
				}
			}
			encryptedUser.setNotification(encryptedNotifications);

			return encryptedUser;   

		} catch (Exception e) {
			System.err.println("Encryption failed for user ID: " + user.getId());
			e.printStackTrace();
			throw new RuntimeException("Could not encrypt user data.", e);
		}
	}

}
