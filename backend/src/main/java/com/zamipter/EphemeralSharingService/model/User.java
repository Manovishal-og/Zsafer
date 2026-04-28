package com.zamipter.EphemeralSharingService.model;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                    // internal only — never encrypted, never exposed

    @Column(unique = true, nullable = false)
    private String usernameHash;        // SHA-256 hash — already not reversible, no encryption needed

    @Column(unique = true, nullable = false)
    private String emailHash;           // SHA-256 hash — same, no encryption needed

    private String apiToken;            // authentication token — not encrypted, but never returned after registration

    private String username;            // not sensitive — no encryption needed

    @Column(columnDefinition = "TEXT")
    private String email;               // ✅ ENCRYPT — plaintext email is PII

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_notifications", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "notification", columnDefinition = "TEXT")
    private List<String> notification; 

	@OneToMany(mappedBy = "sender", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<EphemeralSecret> mySecrets;

	public User() {}


	// Getters and Setters
	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public String getUsernameHash() { return usernameHash; }
	public void setUsernameHash(String usernameHash) { this.usernameHash = usernameHash; }

	public String getApiToken() { return apiToken; }
	public void setApiToken(String apiToken) { this.apiToken = apiToken; }

	public String getEmailHash() { return emailHash; }
	public void setEmailHash(String emailHash) { this.emailHash = emailHash; }

	public String getUsername() { return username; }
	public void setUsername(String username) { this.username = username; }

	public String getEmail() { return email; }
	public void setEmail(String email) { this.email = email; }

	public List<String> getNotification() { 	return notification; }
	public void addNotification(String notification) { this.notification.add(notification); }
	public void setNotification(List<String> notification) {this.notification = (notification != null) ? notification : new ArrayList<>();}
	public void clearNotifications() { 	this.notification.clear(); 
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
