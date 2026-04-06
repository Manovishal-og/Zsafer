
package com.zamipter.EphemeralSharingService.DTO;

import java.time.LocalDateTime;

public record SecretResponse( 
	String message,
	String key,
	String downloadUrl,
	LocalDateTime expiresAt
){}
