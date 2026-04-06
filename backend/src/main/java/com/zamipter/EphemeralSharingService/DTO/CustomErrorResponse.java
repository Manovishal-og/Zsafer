
package com.zamipter.EphemeralSharingService.DTO;

import java.time.LocalDateTime;

public record CustomErrorResponse(
	int status,
	String error,
	String message,
	LocalDateTime timestamp
) {}
