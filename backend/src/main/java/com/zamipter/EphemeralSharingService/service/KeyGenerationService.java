package com.zamipter.EphemeralSharingService.service;

import org.springframework.stereotype.Service;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@Service
public class KeyGenerationService {

    private static final int IV_SIZE = 12;
    private static final int TAG_BIT_LENGTH = 128;
    private static final String ALGO = "AES/GCM/NoPadding";

    // --- KEY GENERATION & HASHING ---

    public SecretKey getSecretKey() throws Exception {
        KeyGenerator keygen = KeyGenerator.getInstance("AES");
        keygen.init(256);
        return keygen.generateKey();
    }

    public String convertToBase64(SecretKey secretKey) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(secretKey.getEncoded());
    }

    public String hashKey(String text) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    public SecretKey deriveKeyFromPassword(String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 100000, 256);
        byte[] keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    // --- ENCRYPTION ---

    public byte[] encrypt(byte[] rawData, SecretKey key) throws Exception {
        byte[] iv = new byte[IV_SIZE];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGO);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BIT_LENGTH, iv));

        byte[] cipherText = cipher.doFinal(rawData);
        return ByteBuffer.allocate(IV_SIZE + cipherText.length).put(iv).put(cipherText).array();
    }

    public String encryptString(String text, SecretKey key) throws Exception {
        if (text == null || text.isEmpty()) return "";
        return Base64.getEncoder().encodeToString(encrypt(text.getBytes(StandardCharsets.UTF_8), key));
    }

    // --- DECRYPTION (Unified Logic) ---

    public byte[] decryptWithSecretKey(byte[] encryptedDataWithIv, SecretKey key) throws Exception {
        ByteBuffer bb = ByteBuffer.wrap(encryptedDataWithIv);
        byte[] iv = new byte[IV_SIZE];
        bb.get(iv);
        byte[] cipherText = new byte[bb.remaining()];
        bb.get(cipherText);

        Cipher cipher = Cipher.getInstance(ALGO);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BIT_LENGTH, iv));
        return cipher.doFinal(cipherText);
    }

    public String decryptStringWithSecretKey(String base64CipherText, SecretKey key) throws Exception {
        if (base64CipherText == null || base64CipherText.isEmpty()) return "";
        return new String(decryptWithSecretKey(Base64.getDecoder().decode(base64CipherText), key), StandardCharsets.UTF_8);
    }

    public String decryptStringWithString(String base64CipherText, String base64Key) throws Exception {
        byte[] decodedKey = Base64.getUrlDecoder().decode(base64Key);
        return decryptStringWithSecretKey(base64CipherText, new SecretKeySpec(decodedKey, "AES"));
    }

    // Used by Controller for Layer 2 Decryption
    public byte[] decrypt(byte[] encryptedDataWithIv, String base64Key) throws Exception {
        byte[] decodedKey = Base64.getUrlDecoder().decode(base64Key);
        return decryptWithSecretKey(encryptedDataWithIv, new SecretKeySpec(decodedKey, "AES"));
    }

    // --- MASTER KEY WRAPPING ---

    public byte[] generateAndWrapMasterKey(String password, byte[] salt) throws Exception {
        SecretKey masterKey = getSecretKey();
        SecretKey wrappingKey = deriveKeyFromPassword(password, salt);
        return encrypt(masterKey.getEncoded(), wrappingKey);
    }

    public SecretKey unwrapMasterKey(byte[] blob, String password, byte[] salt) throws Exception {
        SecretKey wrappingKey = deriveKeyFromPassword(password, salt);
        return new SecretKeySpec(decryptWithSecretKey(blob, wrappingKey), "AES");
    }
}
