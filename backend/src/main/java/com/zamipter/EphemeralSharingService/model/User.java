package com.zamipter.EphemeralSharingService.model;

import java.util.ArrayList;
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

	@Column(unique = true, nullable = false)
	private String emailHash;


	// These are encrypted using the Master Key (NOT the password directly)
	private String username; 
	private String email;
	private ArrayList<String> notification;


	@OneToMany(mappedBy = "sender", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<EphemeralSecret> mySecrets;

	public User() {}


	// Getters and Setters
	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public String getUsernameHash() { return usernameHash; }
	public void setUsernameHash(String usernameHash) { this.usernameHash = usernameHash; }

	public String getPasswordHash() { return passwordHash; }
	public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }


	public String getUsername() { return username; }
	public void setUsername(String username) { this.username = username; }

	public String getEmail() { return email; }
	public void setEmail(String email) { this.email = email; }

	public ArrayList<String> getNotification() { return notification; }
	public void setNotification(String notification) { this.notification.add(notification); }
	public void setNotification(ArrayList<String> notification) {
		if (notification == null) {
			this.notification = new ArrayList<>();
		} else {
			this.notification = notification;
		}
	}
	public void deleteViewedNotification(){ this.notification.clear();}

	public List<EphemeralSecret> getMySecrets() { return mySecrets; }
	public void setMySecrets(List<EphemeralSecret> mySecrets) { this.mySecrets = mySecrets; }


	@Override
	public String toString() {
		return "User{" +
		"id=" + id +
		", usernameHash='" + usernameHash + '\'' +
		", email='" + email + '\'' +
		'}';
	}
}
