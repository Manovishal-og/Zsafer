package com.zamipter.EphemeralSharingService.exception;

import org.springframework.web.bind.annotation.ResponseStatus;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONTENT_TOO_LARGE)
public class FileTooLargeException extends RuntimeException{
	public FileTooLargeException(String message){
		super(message);
	}
}
