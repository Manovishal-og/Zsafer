package com.zamipter.EphemeralSharingService.controller;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.Arrays;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import javax.crypto.SecretKey;
import com.zamipter.EphemeralSharingService.dto.SecretResponse;
import com.zamipter.EphemeralSharingService.exception.FileTooLargeException;
import com.zamipter.EphemeralSharingService.exception.RateLimitException;
import com.zamipter.EphemeralSharingService.model.EphemeralSecret;
import com.zamipter.EphemeralSharingService.model.User;
import com.zamipter.EphemeralSharingService.repository.SecretRepository;
import com.zamipter.EphemeralSharingService.repository.UserRepository;
import com.zamipter.EphemeralSharingService.service.EmailService;
import com.zamipter.EphemeralSharingService.service.KeyGenerationService;
import com.zamipter.EphemeralSharingService.service.ApiRateLimiting;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * EphemeralController - Manages secure, burn-on-read file sharing.
 */
@RestController
public class EphemeralController {

	private static final Set<String> allowedExtension = Set.of("pdf", "jpg", "png", "docx", "mp3", "mp4", "txt");
	private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100 MB

	@Autowired
	private KeyGenerationService keyService;

	@Autowired
	private ApiRateLimiting rateLimitingService;

	@Autowired
	private SecretRepository secretRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EmailService emailService;


	@PostMapping("/create/secret")
	public ResponseEntity<?> createUser(@RequestBody Map<String, String> payload) {
		try {
			String username = payload.get("username");
			String password = payload.get("password");
			String email = payload.get("email");

			if (username == null || password == null || email == null) {
				return ResponseEntity.badRequest().body("Missing required fields");
			}

			// 1. Generate Shadow Hashes (SHA-256) for database searching
			String usernameHash = keyService.hashKey(username);

			// 2. Securely Hash Password for authentication (BCrypt)
			String passwordHash = keyService.hashKey(password);

			// 3. Generate random salt specifically for the Master Key PBKDF2 process
			byte[] salt = new byte[16];
			new SecureRandom().nextBytes(salt);

			// 4. Generate and Wrap the Master Key
			// This random key is encrypted using a key derived from the password + salt
			byte[] masterKeyBlob = keyService.generateAndWrapMasterKey(password, salt);

			// 5. Encrypt Identity Fields using the Master Key
			var masterKey = keyService.unwrapMasterKey(masterKeyBlob, password, salt);
			String encryptedUsername = keyService.encryptString(username, masterKey);
			String encryptedEmail = keyService.encryptString(email, masterKey);

			// 6. Persist the User
			User newUser = new User();
			newUser.setUsernameHash(usernameHash);
			newUser.setPasswordHash(passwordHash);
			newUser.setMasterKeyBlob(masterKeyBlob);
			newUser.setSalt(salt); // CRITICAL: Saved for future logins
			newUser.setEncryptedUsername(encryptedUsername);
			newUser.setEncryptedEmail(encryptedEmail);


			userRepository.save(newUser);

			return ResponseEntity.status(HttpStatus.CREATED).body("User created successfully");

		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body("Registration failed: " + e.getMessage());
		}
	}


		@PostMapping("/secret")
		public ResponseEntity<?> uploadSecret(
		@RequestParam("file") MultipartFile file,
		@RequestParam("expiry") int seconds,
		@RequestParam(value = "password", required = false) String password,
		@RequestParam(value = "duration") Integer duration,
		@RequestParam(value = "receiverEmail" ) String toEmail,
		@RequestParam(value = "senderEmail" ) String fromEmail,
		HttpServletRequest request
	) throws Exception {

			// 1. Rate Limiting
			if (!rateLimitingService.checkRequestAvailable(request.getRemoteAddr())) {
				throw new RateLimitException("Slow down! You've reached your upload limit.");
			}

			// 2. Validation
			String fileName = file.getOriginalFilename();
			if (fileName == null || !fileName.contains(".")) {
				return ResponseEntity.badRequest().body("Invalid file name.");
			}

			String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
			if (!allowedExtension.contains(extension)) {
				return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
				.body("File type ." + extension + " is not allowed.");
			}

			if (file.getSize() > MAX_FILE_SIZE) {
				throw new FileTooLargeException("File exceeds 100MB limit.");
			}

			// 3. Layer 1 Encryption: User Password
			byte[] currentData = file.getBytes();
			byte[] salt = null;
			String processingFromEmail = (fromEmail != null) ? fromEmail : "";

			if (password != null && !password.isBlank()) {
				salt = new byte[16];
				new java.security.SecureRandom().nextBytes(salt);
				SecretKey passwordKey = keyService.deriveKeyFromPassword(password, salt);
				currentData = keyService.encrypt(currentData, passwordKey);

				if (!processingFromEmail.isEmpty()) {
					processingFromEmail = keyService.encryptString(processingFromEmail, passwordKey);
				}
			}

			// 4. Layer 2 Encryption: System Key (Lives only in URL)
			SecretKey systemKey = keyService.getSecretKey();
			String id = keyService.convertToBase64(systemKey);
			String hid = keyService.hashKey(id);

			byte[] finalEncryptedData = keyService.encrypt(currentData, systemKey);
			String finalEncryptedSenderEmail = keyService.encryptString(processingFromEmail, systemKey);

			// 5. Metadata and Storage
			String type = org.springframework.http.MediaTypeFactory
			.getMediaType(fileName)
			.map(MediaType::toString)
		.orElse("application/octet-stream");

		LocalDateTime eTime = LocalDateTime.now().plusSeconds(seconds);
		String currentUsernameHash = keyService.hashKey(request.getHeader("X-ZSafer-Username"));
		User sender = userRepository.findByUsernameHash(currentUsernameHash)
					.orElseThrow(() -> new RuntimeException("User not found"));

		secretRepository.save(new EphemeralSecret(
			hid, finalEncryptedData, fileName, LocalDateTime.now(), 
			eTime, type, salt, duration , sender
		));

			// 6. Notify Receiver
			emailService.sendSecretNotification(toEmail, id, seconds, password);

			return ResponseEntity.status(HttpStatus.CREATED)
			.body(new SecretResponse("File Uploaded successfully!", id, eTime));
		}

		@GetMapping(path = "/secret/{key}")
		public ResponseEntity<?> download(
		@PathVariable("key") String key,
		@RequestParam(value = "password", required = false) String password,
		@RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
		@RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
		HttpServletRequest request,
		HttpServletResponse httpResponse
	) throws Exception {

			// 1. Rate Limiting
			if (!rateLimitingService.checkRequestAvailable(request.getRemoteAddr())) {
				throw new RateLimitException("Too many attempts.");
			}

			// 2. Repository Lookup
			String hid = keyService.hashKey(key);
			EphemeralSecret secret = secretRepository.findById(hid)
			.orElseThrow(() -> new RuntimeException("Secret not found or already burned."));

			// 3. Password Check / Gatekeeper
			if (secret.getSalt() != null && (password == null || password.isBlank())) {
				// If request comes from CLI, return 401 instead of HTML
				if (userAgent != null && userAgent.contains("zsafer-cli")) {
					return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Password Required");
				}

				String html = "<html><body><script>" +
				"var p = prompt('This file is password protected. Enter Password:');" +
				"if(p) { window.location.search = 'password=' + encodeURIComponent(p); }" +
				"</script></body></html>";
				return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html.getBytes());
			}

			// 4. Burn-window Check
			if (secret.getBurnAt() != null && LocalDateTime.now().isAfter(secret.getBurnAt())) {
				secretRepository.delete(secret);
				throw new RuntimeException("Secret has expired.");
			}

			// Handle Password Gate for CLI
			if (secret.getSalt() != null && (password == null || password.isBlank())) {
				if (userAgent != null && userAgent.contains("zsafer-cli")) {
					return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Password Required");
				}
				// HTML for Browser users
				return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body("...JS Prompt...".getBytes());
			}

			// 5. Decryption
			byte[] finalData;
			String originalSenderEmail = "";
			try {
				// System Layer
				byte[] intermediate = keyService.decrypt(secret.getData(), key);
				String partialSenderEmail = keyService.decryptStringWithString(secret.getSenderEmail(), key);

				// Password Layer
				if (secret.getSalt() != null) {
					SecretKey passwordKey = keyService.deriveKeyFromPassword(password, secret.getSalt());
					finalData = keyService.decryptWithSecretKey(intermediate, passwordKey);
					if (partialSenderEmail != null && !partialSenderEmail.isEmpty()) {
						originalSenderEmail = keyService.decryptStringWithSecretKey(partialSenderEmail, passwordKey);
					}
				} else {
					finalData = intermediate;
					originalSenderEmail = partialSenderEmail;
				}
			} catch (Exception e) {
				throw new RuntimeException("Decryption failed. Wrong password or corrupted link.");
			}

			// 6. Access Notification
			if (originalSenderEmail != null && !originalSenderEmail.isBlank()) {
				emailService.sendAccessNotification(originalSenderEmail, secret.getFileName());
			}

			// 7. Initial Access: Start the Burn Timer
			if (secret.getBurnAt() == null) {
				secret.setIsViewed(true);
				if (secret.getDuration() != -1) {
					secret.setBurnAt(LocalDateTime.now().plusSeconds(secret.getDuration()));
				}
				secretRepository.save(secret);
			}


			// Standard Response
			return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + secret.getFileName() + "\"")
			.contentType(MediaType.parseMediaType(secret.getContentType()))
			.body(finalData);
		}
	}
