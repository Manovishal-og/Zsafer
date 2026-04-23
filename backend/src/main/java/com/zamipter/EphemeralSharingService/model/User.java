package com.zamipter.EphemeralSharingService.model;

import java.util.List;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// Hashed for searching during login (SHA-256)
	@Column(unique = true, nullable = false)
	private String usernameHash;

	// Hashed for verifying identity (BCrypt)
	@Column(nullable = false)
	private String passwordHash;

	// THE BRIDGE: This is the Master Key, encrypted by your password.
	// You need this to unlock the fields below.
	@Lob
	@Column(columnDefinition = "BLOB")
	private byte[] masterKeyBlob; 

	// These are encrypted using the Master Key (NOT the password directly)
	private String encryptedUsername; 
	private String encryptedEmail;

	@Lob
	@Column(columnDefinition = "BLOB")
	private byte[] salt;

	@OneToMany(mappedBy = "sender", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<EphemeralSecret> mySecrets;

	public User() {}

	public byte[] getSalt() { return salt; }
	public void setSalt(byte[] salt) { this.salt = salt; }

	// Getters and Setters
	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public String getUsernameHash() { return usernameHash; }
	public void setUsernameHash(String usernameHash) { this.usernameHash = usernameHash; }

	public String getPasswordHash() { return passwordHash; }
	public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

	public byte[] getMasterKeyBlob() { return masterKeyBlob; }
	public void setMasterKeyBlob(byte[] masterKeyBlob) { this.masterKeyBlob = masterKeyBlob; }

	public String getEncryptedUsername() { return encryptedUsername; }
	public void setEncryptedUsername(String encryptedUsername) { this.encryptedUsername = encryptedUsername; }

	public String getEncryptedEmail() { return encryptedEmail; }
	public void setEncryptedEmail(String encryptedEmail) { this.encryptedEmail = encryptedEmail; }

	public List<EphemeralSecret> getMySecrets() { return mySecrets; }
	public void setMySecrets(List<EphemeralSecret> mySecrets) { this.mySecrets = mySecrets; }

	@Override
	public String toString() {
		return "User{" +
		"id=" + id +
		", usernameHash='" + usernameHash + '\'' +
		", email='" + encryptedEmail + '\'' +
		'}';
	}
}
