
package com.zamipter.EphemeralSharingService.dto;

import java.time.LocalDateTime;

public record SecretResponse( 
	String message,
	String key,
	LocalDateTime expiresAt
){}
