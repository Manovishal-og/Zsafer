package com.zamipter.EphemeralSharingService.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;


@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Async // Runs in background so the API stays fast
    public void sendSecretNotification(String toEmail, String downloadUrl, int expirySeconds, String password) {
		String time;
		if(TimeUnit.SECONDS.toDays(expirySeconds) > 1){
			 time = TimeUnit.SECONDS.toDays(expirySeconds) + " days";
		}
		else if(TimeUnit.SECONDS.toHours(expirySeconds) > 1){
			 time = TimeUnit.SECONDS.toHours(expirySeconds)+ " hours"; 
		}
		else if(TimeUnit.SECONDS.toMinutes(expirySeconds) > 1){
			 time = TimeUnit.SECONDS.toMinutes(expirySeconds)+ " minutes";
		}
		else{
			 time = expirySeconds +" seconds";
		}

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("🔒 Someone shared a secure file with you!");
        message.setText("You have received an ephemeral file.\n\n" +
			"Link: " + downloadUrl + "\n\n" +
			"Password: "+ password + "\n\n"+
			"Note: This link will expire in " + time +
			" and will be PERMANENTLY BURNED after viewing.\n\n" +
			"Sent via Salem Ephemeral Service.");
        
        mailSender.send(message);
    }

	public void sendAccessNotification(String recipientEmail, String fileName) {
        if (recipientEmail == null || recipientEmail.isEmpty()) return;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipientEmail);
        message.setSubject("File Access Alert: " + fileName);
        message.setText("Your file '" + fileName + "' was accessed and opened at " + 
                        java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a")) + ".");
        
        mailSender.send(message);
    }
}
