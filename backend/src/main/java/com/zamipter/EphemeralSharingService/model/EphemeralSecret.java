package com.zamipter.EphemeralSharingService.model;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.Column;

@Entity
public class EphemeralSecret {

	@Id
	private String id;

	private byte[] salt;

	@Lob
	@Column(columnDefinition = "LONGBLOB")
	private byte[] data;

	private String fileName;
	private String ContentType;
	private LocalDateTime createdAt;
	private LocalDateTime expiredAt;
	@Column(nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
	private Boolean isViewed = false;
	private int duration;
	private LocalDateTime burnAt;
	
@JoinColumn(name = "user_id")
    private User user;
	// Default Constructor is needed for Spring JPA
	public EphemeralSecret() {}

	public EphemeralSecret( String id ,  byte[] data , String fileName , LocalDateTime cTime , LocalDateTime eTime , String ContentType , byte[] salt, int duration , User user){
		this.id = id;
		this.data = data;
		this.fileName = fileName;
		this.createdAt = cTime;
		this.expiredAt = eTime ;
		this.ContentType = ContentType;
		this.salt = salt;
		this.duration = duration;
		this.user = user;
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }

	public byte[] getData() { return data; }
	public void setData(byte[] data) { this.data = data; }

	public String getFileName() { return fileName; }
	public void setFileName(String fileName) { this.fileName = fileName; }

	public String getContentType() { return ContentType; }
	public void setContentType(String contentType) { ContentType = contentType; }

	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

	public LocalDateTime getExpiredAt() { return expiredAt; }
	public void setExpiredAt(LocalDateTime expiredAt) { this.expiredAt = expiredAt; }

	public Boolean getIsViewed() { return isViewed; }
	public void setIsViewed(Boolean isViewed) { this.isViewed = isViewed; }

	public byte[] getSalt(){ return salt;}
	public void setSalt(byte[] salt){ this.salt = salt;}
		
	public int getDuration(){ return duration;}
	public void setDuration(int duration){ this.duration = duration;	}
	
	public LocalDateTime getBurnAt(){ return burnAt;}
	public void setBurnAt (LocalDateTime duration){ this.burnAt = duration;	}

	public User getuser() { return user; }
    public void setuser(User user) { this.user = user; }

}
