package com.zamipter.EphemeralSharingService.service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;

@Service
public class ApiRateLimiting {

	private final Map<String , Bucket> buckets = new ConcurrentHashMap<>();

	public Bucket getBucketForIP(String ip){
		return buckets.computeIfAbsent(ip, k -> {
			Bandwidth limit = Bandwidth.classic(20, Refill.greedy(20, Duration.ofMinutes(1)));
			return Bucket.builder().addLimit(limit).build();
		});
	}

	public boolean checkRequestAvailable(String ip){
		Bucket bucket = getBucketForIP(ip);
		if(bucket.tryConsume(1)) return true;
		else return false;
	}
}
