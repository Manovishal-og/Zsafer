//
// package com.zamipter.EphemeralSharingService.controller;
//
// import java.time.LocalDateTime;
// import java.util.Set;
//
// import java.util.Arrays;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.http.HttpHeaders;
// import org.springframework.http.HttpStatus;
// import org.springframework.http.MediaType;
// import org.springframework.http.ResponseEntity;
// import org.springframework.web.bind.annotation.GetMapping;
// import org.springframework.web.bind.annotation.PathVariable;
// import org.springframework.web.bind.annotation.PostMapping;
// import org.springframework.web.bind.annotation.RequestHeader;
// import org.springframework.web.bind.annotation.RequestParam;
// import org.springframework.web.bind.annotation.RestController;
// import org.springframework.web.multipart.MultipartFile;
// import javax.crypto.SecretKey;
// import com.zamipter.EphemeralSharingService.dto.SecretResponse;
// import com.zamipter.EphemeralSharingService.exception.RateLimitException;
// import com.zamipter.EphemeralSharingService.model.EphemeralSecret;
// import com.zamipter.EphemeralSharingService.repository.SecretRepository;
// import com.zamipter.EphemeralSharingService.service.EmailService;
// import com.zamipter.EphemeralSharingService.service.KeyGenerationService;
// import com.zamipter.EphemeralSharingService.service.ApiRateLimiting;
// import jakarta.servlet.http.HttpServletRequest;
// import jakarta.servlet.http.HttpServletResponse;
//
// /**
//  * EphemeralController
//  */
// public class EphemeralControllerBackup {
//
// 	private static final Set<String> allowedExtension = Set.of("pdf", "jpg", "png", "mp3", "mp4","txt");
// 	@Autowired
// 	private KeyGenerationService keyService;
//
// 	@Autowired
// 	private ApiRateLimiting rateLimitingService;
//
// 	@Autowired
// 	private SecretRepository repository;
//
// 	@Autowired
// 	private EmailService emailService;
//
// 	@PostMapping("/secret")
// 	public ResponseEntity<?> uploadSecret(
// 		@RequestParam("file")                              MultipartFile file,
// 		@RequestParam("expiry")                            int seconds,
// 		@RequestParam(value = "password",      required = false) String password,
// 		@RequestParam(value = "duration",      required = false) Integer duration,
// 		@RequestParam(value = "receiverEmail", required = false) String toEmail,
// 		@RequestParam(value = "senderEmail",   required = false) String fromEmail,
// 		HttpServletRequest request
// 	) throws Exception {
//
// 		// 1. Rate limit before any heavy work
// 		if (!rateLimitingService.checkRequestAvailable(request.getRemoteAddr())) {
// 			throw new RateLimitException("Slow down! You've reached your upload limit.");
// 		}
//
// 		if(fromEmail == null && toEmail == null){
// 			fromEmail = "";
// 			toEmail = "";
// 		}
//
// 		// 2. Validate file name & extension
// 		String fileName = file.getOriginalFilename();
// 		if (fileName == null || !fileName.contains(".")) {
// 			return ResponseEntity.badRequest().body("Invalid file name.");
// 		}
// 		String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
// 		if (!allowedExtension.contains(extension)) {
// 			return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
// 			.body("File type ." + extension + " is not allowed for security reasons.");
// 		}
//
// 		// 3. Layer 1: optional password encryption
// 		byte[] currentData = file.getBytes();
// 		byte[] salt = null;
// 		if (password != null && !password.isBlank()) {
// 			salt = new byte[16];
// 			new java.security.SecureRandom().nextBytes(salt);
// 			SecretKey passwordKey = keyService.deriveKeyFromPassword(password, salt);
// 			currentData = keyService.encrypt(currentData, passwordKey);
// 			fromEmail =  keyService.encryptString(fromEmail, passwordKey);
// 			fileName = keyService.encryptString(fileName, passwordKey);
// 		}
//
// 		// 4. Layer 2: mandatory system encryption (key lives only in the URL)
// 		SecretKey systemKey     = keyService.getSecretKey();
// 		String    id            = keyService.convertToBase64(systemKey);
// 		String    hid           = keyService.hashKey(id);
// 		byte[]    finalEncryptedData = keyService.encrypt(currentData, systemKey);
// 		String    finalEncryptedSenderEmail = keyService.encryptString(fromEmail, systemKey);
// 		String   finalEncryptedFileName = keyService.encryptString(fileName, systemKey);
//
// 		// 5. Resolve content type & burn duration
// 		String type = org.springframework.http.MediaTypeFactory
// 		.getMediaType(fileName)
// 		.map(MediaType::toString)
// 		.orElse("application/octet-stream");
//
// 		int finalDuration;
// 		if (type.startsWith("video") || type.startsWith("audio")) {
// 			finalDuration = (duration != null) ? duration : 300;
// 		} else if (type.equals("application/pdf")) {
// 			finalDuration = 150;
// 		} else {
// 			finalDuration = 4;
// 		}
//
// 		LocalDateTime eTime = LocalDateTime.now().plusSeconds(seconds);
// 		repository.save(new EphemeralSecret(hid, finalEncryptedData, finalEncryptedFileName,
// 			LocalDateTime.now(), eTime, type, salt, finalDuration, finalEncryptedSenderEmail));
//
// 		String downloadUrl = "http://localhost:8080/secret/" + id;
//
// 		if (toEmail != null && !toEmail.isBlank()) {
// 			emailService.sendSecretNotification(toEmail, downloadUrl,seconds, password);
// 		}
//
// 		return ResponseEntity.status(201)
// 		.body(new SecretResponse("File Uploaded successfully!", id, downloadUrl, eTime));
// 	}
//
// 	@GetMapping(path = "/secret/{key}")
// 	public ResponseEntity<?> download(
// 		@PathVariable("key")                               String key,
// 		@RequestParam(value = "password", required = false) String password,
// 		@RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
// 		HttpServletRequest  request,
// 		HttpServletResponse httpResponse
// 	) throws Exception {
//
// 		// 1. Rate limit
// 		if (!rateLimitingService.checkRequestAvailable(request.getRemoteAddr())) {
// 			throw new RateLimitException("Too many attempts.");
// 		}
//
// 		// 2. Look up the secret
// 		String hid = keyService.hashKey(key);
// 		EphemeralSecret secret = repository.findById(hid)
// 		.orElseThrow(() -> new RuntimeException("Secret not found or already burned."));
//
// 		// 3. Password gate — prompt if salt exists but no password supplied
// 		if (secret.getSalt() != null && (password == null || password.isBlank())) {
// 			String html = "<html><body><script>" +
// 			"var p = prompt('Enter Password:');" +
// 			"if(p) { window.location.search = 'password=' + encodeURIComponent(p); }" +
// 			"</script></body></html>";
// 			return ResponseEntity.ok()
// 			.contentType(MediaType.TEXT_HTML)
// 			.body(html.getBytes());
// 		}
//
// 		// 4. Burn-window check
// 		if (secret.getBurnAt() != null && LocalDateTime.now().isAfter(secret.getBurnAt())) {
// 			repository.delete(secret);
// 			throw new RuntimeException("This secret has expired.");
// 		}
//
// 		// 5. Decrypt
// 		byte[] finalData;
// 		String originalSenderEmail;
// 		String originalFileName;
// 		try {
// 			byte[] intermediate = keyService.decrypt(secret.getData(), key);
// 			String partialSenderEmail = keyService.decryptStringWithString(secret.getSenderEmail(), key);
// 			String partialFileName = keyService.decryptStringWithString(secret.getFileName(), key);
// 			if (secret.getSalt() != null) {
// 				SecretKey passwordKey = keyService.deriveKeyFromPassword(password, secret.getSalt());
// 				finalData = keyService.decryptWithSecretKey(intermediate, passwordKey);
// 				originalSenderEmail = keyService.decryptStringWithSecretKey(partialSenderEmail, passwordKey);
// 				originalFileName = keyService.decryptStringWithSecretKey(partialFileName, passwordKey);
// 			} else {
// 				finalData = intermediate;
// 				originalSenderEmail = partialSenderEmail;
// 				originalFileName = partialFileName;
// 			}
// 		} catch (Exception e) {
// 			throw new RuntimeException("Incorrect password or corrupted link.");
// 		}
//
//
// 		// 6. Start burn timer on very first access
// 		if (secret.getBurnAt() == null) {
// 			secret.setIsViewed(true);
// 			secret.setBurnAt(LocalDateTime.now().plusSeconds(secret.getDuration()));
// 			secret.setFileName(originalFileName);
// 			repository.save(secret);
// 		}
//
// 		if(originalSenderEmail != null){
// 			emailService.sendAccessNotification(originalSenderEmail,secret.getFileName() );
// 		}
// 		// 7. Build response with Range support
// 		int    totalLength  = finalData.length;
// 		String contentType  = secret.getContentType();
// 		String disposition  = "inline; filename=\"" + secret.getFileName() + "\"";
//
// 		// Force Tomcat to send Content-Length (not chunked), required for video seek
// 		httpResponse.setContentLengthLong(totalLength);
//
// 		// ── Ranged request (browser seeking / buffering) ──────────────────────
// 		if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
// 			String[] bounds = rangeHeader.substring("bytes=".length()).split("-");
//
// 			int start = Integer.parseInt(bounds[0]);
// 			int end   = (bounds.length > 1 && !bounds[1].isEmpty())
// 			? Integer.parseInt(bounds[1])
// 			: totalLength - 1;
//
// 			// Clamp — browser sometimes asks for end beyond file size
// 			if (end >= totalLength) end = totalLength - 1;
//
// 			int    chunkLength = end - start + 1;
// 			byte[] chunk       = Arrays.copyOfRange(finalData, start, end + 1);
//
// 			return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
// 			.header(HttpHeaders.CONTENT_DISPOSITION, disposition)
// 			.header(HttpHeaders.ACCEPT_RANGES,       "bytes")
// 			.header(HttpHeaders.CONTENT_RANGE,       "bytes " + start + "-" + end + "/" + totalLength)
// 			.header(HttpHeaders.CONTENT_LENGTH,      String.valueOf(chunkLength))
// 			.contentType(MediaType.parseMediaType(contentType))
// 			.body(chunk);
// 		}
//
// 		// ── Full-file response (initial load, no Range header) ────────────────
// 		return ResponseEntity.ok()
// 		.header(HttpHeaders.CONTENT_DISPOSITION, disposition)
// 		.header(HttpHeaders.ACCEPT_RANGES,       "bytes")
// 		.header(HttpHeaders.CONTENT_LENGTH,      String.valueOf(totalLength))
// 		.contentType(MediaType.parseMediaType(contentType))
// 		.body(finalData);
// 	}
//
// }
//
