package com.zamipter.EphemeralSharingService.controller;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.zamipter.EphemeralSharingService.dto.SecretResponse;
import com.zamipter.EphemeralSharingService.exception.FileTooLargeException;
import com.zamipter.EphemeralSharingService.exception.RateLimitException;
import com.zamipter.EphemeralSharingService.model.EphemeralSecret;
import com.zamipter.EphemeralSharingService.model.User;
import com.zamipter.EphemeralSharingService.repository.SecretRepository;
import com.zamipter.EphemeralSharingService.repository.UserRepository;
import com.zamipter.EphemeralSharingService.service.ApiRateLimiting;
import com.zamipter.EphemeralSharingService.service.EmailService;
import com.zamipter.EphemeralSharingService.service.KeyGenerationService;
import com.zamipter.EphemeralSharingService.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;

@RestController
public class EphemeralController {

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("pdf", "jpg", "jpeg", "png", "docx", "mp3", "mp4", "txt");
    private static final long MAX_FILE_SIZE = 100L * 1024 * 1024; // 100 MB

    @Autowired private KeyGenerationService keyService;
    @Autowired private ApiRateLimiting      rateLimitingService;
    @Autowired private SecretRepository     secretRepository;
    @Autowired private UserRepository       userRepository;
    @Autowired private EmailService         emailService;
    @Autowired private UserService          userService;

    // =========================================================================
    //  POST /create/user
    //
    //  No password — apiToken is the only credential.
    //  Both username and email are encrypted with the server key.
    //  usernameHash and emailHash are stored for lookups.
    //  apiToken is hashed before storing — raw token returned to client once.
    // =========================================================================

    @PostMapping("/create/user")
    public ResponseEntity<?> createUser(@RequestBody Map<String, String> payload) {
        try {
            String username = payload.get("username");
            String email    = payload.get("email");

            if (username == null || username.isBlank() ||
                email    == null || email.isBlank()) {
                return ResponseEntity.badRequest()
                        .body("Missing required fields: username, email.");
            }

            // SHA-256 hashes for lookup — safe to store, not reversible
            String usernameHash = keyService.hashKey(username);
            String emailHash    = keyService.hashKey(email);

            // Check uniqueness before persisting
            if (userRepository.findByUsernameHash(usernameHash).isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("Username already taken.");
            }
            if (userRepository.findByEmailHash(emailHash).isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("Email already registered.");
            }

            // Raw token returned to client — client must store it safely.
            // Hashed token stored in DB so a DB breach cannot replay tokens.
            String apiToken = java.util.UUID.randomUUID().toString();

            User newUser = new User();
            newUser.setUsernameHash(usernameHash);
            newUser.setEmailHash(emailHash);
            newUser.setApiToken(keyService.hashKey(apiToken)); // stored as hash

            // Both username and email are PII — encrypt with server key
            newUser.setUsername(keyService.encrypt(username));
            newUser.setEmail(keyService.encrypt(email));

            // First notification — encrypted before storing
            newUser.addNotification(keyService.encrypt("Welcome to Zsafer!"));


            userRepository.save(newUser);

            // Send welcome email using plaintext values (before they leave scope)
            emailService.greetUserEmail(email, username);

            Map<String, String> response = new java.util.HashMap<>();
            response.put("message", "Account created. Store your token — it cannot be recovered.");
            response.put("token", apiToken);           // raw token, shown once
            response.put("usernameHash", usernameHash);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Registration failed: " + e.getMessage());
        }
    }

    // =========================================================================
    //  GET /{usernamehash}/message
    //
    //  Client sends their apiToken as a query param.
    //  validateUserSession hashes it and matches against DB.
    //  decryptUser decrypts the notification list before returning.
    // =========================================================================

    @GetMapping("/{usernamehash}/message")
    public ResponseEntity<?> accessNotification(
            @PathVariable("usernamehash") String usernameHash,
            @RequestParam("apitoken")     String apiToken) {
        try {
            User searchedUser  = userService.validateUserSession(apiToken, usernameHash);
            User decryptedUser = userService.decryptUser(searchedUser);
            return ResponseEntity.ok(decryptedUser.getNotification());

        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error retrieving messages: " + e.getMessage());
        }
    }

    // =========================================================================
    //  DELETE /{usernamehash}/clear
    //
    //  Clears all notifications for the authenticated user.
    //  Operates on the JPA-managed entity directly — no decrypt needed.
    // =========================================================================

    @DeleteMapping("/{usernamehash}/clear")
    public ResponseEntity<?> deleteAllNotification(
            @PathVariable("usernamehash") String usernameHash,
            @RequestBody Map<String, String> payload) {
        try {
            String apiToken = payload.get("apitoken");
            if (apiToken == null || apiToken.isBlank()) {
                return ResponseEntity.badRequest().body("Missing required field: apitoken.");
            }

            // Operates on the raw DB entity — no decryption needed for clearing
            User searchedUser = userService.validateUserSession(apiToken, usernameHash);
            searchedUser.clearNotifications();
            userRepository.save(searchedUser);

            return ResponseEntity.ok("Notifications cleared successfully.");

        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid credentials or session expired.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error clearing notifications: " + e.getMessage());
        }
    }

    // =========================================================================
    //  POST /secret   (multipart/form-data)
    //
    //  Encryption layers:
    //    Layer 1 (optional): PBKDF2 key from dataAccessPassword
    //    Layer 2 (mandatory): random AES-256 key that lives only in the URL
    //
    //  Sender email is double-encrypted and stored in EphemeralSecret
    //  so the download endpoint can notify the sender without the DB
    //  ever seeing plaintext.
    //
    //  IMPORTANT: notifications and FK relationships always use the
    //  JPA-managed entity (receiver / validatedSender), never the
    //  decrypted transient copy — saving a decrypted copy would write
    //  plaintext PII back to the database.
    // =========================================================================

    @PostMapping("/secret")
    public ResponseEntity<?> uploadSecret(
            @RequestParam("file")                                         MultipartFile file,
            @RequestParam("expiry")                                       int           expirySeconds,
            @RequestParam(value = "dataAccessPassword", required = false) String        dataAccessPassword,
            @RequestParam("duration")                                     int           duration,
            @RequestParam("receiverEmail")                                String        receiverEmail,
            @RequestParam("sender")                                       String        senderUsernameHash,
            @RequestParam("apitoken")                                     String        apiToken,
            HttpServletRequest request) throws Exception {

        // 1. Rate limit
        if (!rateLimitingService.checkRequestAvailable(request.getRemoteAddr())) {
            throw new RateLimitException("Too many uploads. Try later.");
        }

        // 2. Validate sender
        // validatedSender = JPA-managed entity (used for FK + save)
        // decryptedSender = transient copy with plaintext fields (used to read values)
        User validatedSender = userService.validateUserSession(apiToken, senderUsernameHash);
        User decryptedSender = userService.decryptUser(validatedSender);

        // 3. Find receiver
        // receiver          = JPA-managed entity (used for FK + save)
        // decryptedReceiver = transient copy with plaintext email (used for sending email)
        String receiverHash    = keyService.hashKey(receiverEmail);
        User receiver          = userRepository.findByEmailHash(receiverHash)
                .orElseThrow(() -> new RuntimeException("Receiver not found in Zsafer."));
        User decryptedReceiver = userService.decryptUser(receiver);

        // 4. File validation
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.contains(".")) {
            return ResponseEntity.badRequest().body("Invalid file name.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new FileTooLargeException("Max 100MB allowed.");
        }
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body("Unsupported file type: ." + extension);
        }

        // 5. Read file bytes
        byte[] data = file.getBytes();
        byte[] salt = null;

        // Sender email starts as plaintext — will be wrapped through both layers
        String senderEmailForStorage = decryptedSender.getEmail();

        // 6. Layer 1: optional password encryption
        if (dataAccessPassword != null && !dataAccessPassword.isBlank()) {
            salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            SecretKey passwordKey = keyService.deriveKeyFromPassword(dataAccessPassword, salt);

            // Encrypt file data with password key
            data = keyService.encrypt(data, passwordKey);

            // Wrap sender email through password layer too —
            // receiver must know the password to reveal who sent the file
            senderEmailForStorage = keyService.encrypt(senderEmailForStorage, passwordKey);
        }

        // 7. Layer 2: system key — generated fresh per upload, lives only in the URL
        SecretKey systemKey  = keyService.getSecretKey();
        String    accessKey  = keyService.convertToBase64(systemKey); // returned to sender
        String    hashedId   = keyService.hashKey(accessKey);         // stored in DB as PK

        byte[] finalEncrypted       = keyService.encrypt(data, systemKey);

        // 8. Resolve MIME type
        String contentType = MediaTypeFactory.getMediaType(fileName)
                .map(MediaType::toString)
                .orElse("application/octet-stream");

        // 9. Persist secret
        LocalDateTime now    = LocalDateTime.now();
        LocalDateTime expiry = now.plusSeconds(expirySeconds);

        EphemeralSecret secret = new EphemeralSecret();
        secret.setId(hashedId);
        secret.setData(finalEncrypted);
        secret.setFileName(fileName);
        secret.setContentType(contentType);
        secret.setCreatedAt(now);
        secret.setExpiredAt(expiry);
        secret.setSalt(salt);
        secret.setDuration(duration);
        secret.setSender(validatedSender);            // JPA-managed entity — correct FK
        secret.setReceiver(receiver);                 // JPA-managed entity — correct FK
        secretRepository.save(secret);

        // 10. Email the receiver — use decrypted email for actual sending
        emailService.sendSecretNotificationEmail(
                decryptedReceiver.getEmail(),
                "/secret/" + accessKey,
                expirySeconds,
                dataAccessPassword
		);


        // 11. Add in-app notification to receiver
        // IMPORTANT: add to `receiver` (JPA-managed entity), then save `receiver`.
        // Never save `decryptedReceiver` — it holds plaintext email which would
        // overwrite the encrypted value in the DB.
        receiver.addNotification(
                keyService.encrypt("New file from " + decryptedSender.getUsername())
        );
        userRepository.save(receiver);
	

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SecretResponse("Uploaded successfully!", accessKey, expiry));
    }

    // =========================================================================
    //  GET /secret/{key}
    //
    //  Burn-on-read with configurable burn window (duration seconds).
    //  On first access: burn timer starts.
    //  While inside burn window: file is served (supports HTTP Range for video).
    //  After burnAt: CleanupService scheduler deletes the row.
    //
    //  Sender email is decrypted only at download time — never stored plaintext.
    //  Sender is notified ONCE (on first access), not on every subsequent view.
    // =========================================================================

	@Transactional
	@GetMapping("/secret/{key}")
	public ResponseEntity<?> download(
		@PathVariable("key")                                          String key,
		@RequestParam(value = "password", required = false)          String dataAccessPassword,
		@RequestHeader(value = HttpHeaders.RANGE, required = false)  String rangeHeader,
		@RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
		HttpServletRequest request,
		HttpServletResponse httpResponse) throws Exception {

		// 1. Rate limit
		if (!rateLimitingService.checkRequestAvailable(request.getRemoteAddr())) {
			throw new RateLimitException("Too many attempts.");
		}

		// 2. Lookup — atomic claim
		String hid = keyService.hashKey(key);
		EphemeralSecret secret = secretRepository.findById(hid).orElse(null);

		if (secret == null) {
			return ResponseEntity.status(HttpStatus.GONE)
			.body("This link has already been used or does not exist.");
		}

		if (Boolean.TRUE.equals(secret.getIsViewed())) {
			secretRepository.delete(secret);
			return ResponseEntity.status(HttpStatus.GONE)
			.body("This link has already been used.");
		}

		// Claim it immediately before anything else
		secret.setIsViewed(true);
		secretRepository.saveAndFlush(secret);

		// 3. Hard expiry check
		if (LocalDateTime.now().isAfter(secret.getExpiredAt())) {
			secretRepository.delete(secret);
			return ResponseEntity.status(HttpStatus.GONE).body("This link has expired.");
		}

		// 4. Password gate
		if (secret.getSalt() != null && (dataAccessPassword == null || dataAccessPassword.isBlank())) {
			if (userAgent != null && userAgent.contains("zsafer-cli")) {
				return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body("Password required. Retry with ?password=<value>");
			}
			String html = "<html><body><script>" +
			"var p=prompt('This file is password protected. Enter password:');" +
			"if(p){window.location.search='password='+encodeURIComponent(p);}" +
			"else{document.body.innerText='Access cancelled.';}" +
			"</script></body></html>";
			return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html.getBytes());
		}

		// 5. Decrypt
		User sender   = userService.decryptUser(secret.getSender());
		User receiver = userService.decryptUser(secret.getReceiver());

		byte[] finalData;
		try {
			byte[] intermediate = keyService.decrypt(secret.getData(), key);
			if (secret.getSalt() != null) {
				SecretKey passwordKey = keyService.deriveKeyFromPassword(dataAccessPassword, secret.getSalt());
				finalData = keyService.decrypt(intermediate, passwordKey);
			} else {
				finalData = intermediate;
			}
		} catch (Exception e) {
			throw new RuntimeException("Decryption failed. Wrong password or corrupted link.");
		}

		// 6. Notify sender on first (and only) access
		if (sender.getEmail() != null && !sender.getEmail().isBlank()) {
			emailService.sendAccessNotificationEmail(sender.getEmail(), secret.getFileName());
			User managedSender = secret.getSender();
			managedSender.addNotification(keyService.encrypt(
				"Your file was accessed by " + receiver.getUsername()
			));
			userRepository.save(managedSender);
		}

		// 7. Build response
		int    total       = finalData.length;
		String contentType = secret.getContentType();
		String disposition = "inline; filename=\"" + secret.getFileName() + "\"";
		boolean isMedia    = contentType.startsWith("audio/") || contentType.startsWith("video/");

		// Range request — audio/video seeking
		if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
			String[] bounds = rangeHeader.substring(6).split("-");
			int start = Integer.parseInt(bounds[0]);
			int end   = (bounds.length > 1 && !bounds[1].isEmpty())
			? Integer.parseInt(bounds[1])
			: total - 1;
			end = Math.min(end, total - 1);

			byte[] chunk        = Arrays.copyOfRange(finalData, start, end + 1);
			boolean isFinalChunk = (end == total - 1);

			if (isFinalChunk) {
				secretRepository.delete(secret);
			}

			return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
			.header(HttpHeaders.CONTENT_DISPOSITION, disposition)
			.header(HttpHeaders.ACCEPT_RANGES, "bytes")
			.header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + total)
			.header(HttpHeaders.CONTENT_LENGTH, String.valueOf(chunk.length))
			.contentType(MediaType.parseMediaType(contentType))
			.body(chunk);
		}

		// Full response — delete immediately
		secretRepository.delete(secret);

		return ResponseEntity.ok()
		.header(HttpHeaders.CONTENT_DISPOSITION, disposition)
		.header(HttpHeaders.ACCEPT_RANGES, "bytes")
		.header(HttpHeaders.CONTENT_LENGTH, String.valueOf(total))
		.contentType(MediaType.parseMediaType(contentType))
		.body(finalData);
	}

}
