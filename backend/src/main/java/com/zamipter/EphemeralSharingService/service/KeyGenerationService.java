package com.zamipter.EphemeralSharingService.service;

import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;

import java.security.SecureRandom;
import java.util.Base64;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import javax.crypto.spec.SecretKeySpec;

@Service
public class KeyGenerationService {

	private static final int IV_SIZE = 12;
	private static final int TAG_BIT_LENGTH = 128;

	public KeyGenerator getGenerator() throws Exception{
		KeyGenerator keyGen = KeyGenerator.getInstance("AES");
		return keyGen;
	}

	public SecretKey getSecretKey() throws Exception{
		KeyGenerator keygen = getGenerator();
		keygen.init(256);
		return keygen.generateKey();
	}

	public String convertToBase64(SecretKey secretKey)throws Exception{
		return Base64.getUrlEncoder().withoutPadding().encodeToString(secretKey.getEncoded());
	}

	public String hashKey(String base64) throws Exception{
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		byte[] hashBytes = digest.digest(base64.getBytes());
		return HexFormat.of().formatHex(hashBytes);
	}

	public byte[] encrypt(byte[] rawData, SecretKey key) throws Exception {
		byte[] iv = new byte[IV_SIZE];
		new SecureRandom().nextBytes(iv);

		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		GCMParameterSpec spec = new GCMParameterSpec(TAG_BIT_LENGTH, iv);
		cipher.init(Cipher.ENCRYPT_MODE, key, spec);

		byte[] cipherText = cipher.doFinal(rawData);

		return ByteBuffer.allocate(iv.length + cipherText.length)
		.put(iv)
		.put(cipherText)
		.array();
	}

	public String encryptString(String text, SecretKey key) throws Exception {
		byte[] encryptedData = encrypt(text.getBytes(StandardCharsets.UTF_8), key);
		return Base64.getEncoder().encodeToString(encryptedData);
	}

	public String decryptStringWithSecretKey(String base64CipherText, SecretKey key) throws Exception {
		byte[] encryptedDataWithIv = Base64.getDecoder().decode(base64CipherText);
		byte[] decryptedBytes = decryptWithSecretKey(encryptedDataWithIv, key);
		return new String(decryptedBytes, StandardCharsets.UTF_8);
	}

	public String decryptStringWithString(String base64CipherText, String base64Key) throws Exception {
		byte[] decodedKey = Base64.getUrlDecoder().decode(base64Key);
		SecretKey originalKey = new SecretKeySpec(decodedKey, "AES");
		return decryptStringWithSecretKey(base64CipherText, originalKey);
	}



	public byte[] decrypt(byte[] encryptedDataWithIv, String base64Key) throws Exception {
		// Decode the key from the URL
		byte[] decodedKey = Base64.getUrlDecoder().decode(base64Key);
		javax.crypto.spec.SecretKeySpec originalKey = new javax.crypto.spec.SecretKeySpec(decodedKey, "AES");

		// Slice the IV from the start (12 bytes)
		java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(encryptedDataWithIv);
		byte[] iv = new byte[12];
		bb.get(iv);
		byte[] cipherText = new byte[bb.remaining()];
		bb.get(cipherText);

		// Decrypt
		javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
		javax.crypto.spec.GCMParameterSpec spec = new javax.crypto.spec.GCMParameterSpec(128, iv);
		cipher.init(javax.crypto.Cipher.DECRYPT_MODE, originalKey, spec);

		return cipher.doFinal(cipherText);
	}

	public SecretKey deriveKeyFromPassword(String password, byte[] salt) throws Exception {
		PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 100000, 256);
		SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
		return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
	}

	// Overloaded method to handle SecretKey objects directly
	public byte[] decryptWithSecretKey(byte[] encryptedDataWithIv, SecretKey key) throws Exception {
		ByteBuffer bb = ByteBuffer.wrap(encryptedDataWithIv);
		byte[] iv = new byte[IV_SIZE];
		bb.get(iv);
		byte[] cipherText = new byte[bb.remaining()];
		bb.get(cipherText);

		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		GCMParameterSpec spec = new GCMParameterSpec(TAG_BIT_LENGTH, iv);
		cipher.init(Cipher.DECRYPT_MODE, key, spec);

		return cipher.doFinal(cipherText);
	}


}
