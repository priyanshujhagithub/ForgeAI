package com.forgeai.project.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey secretKey;
    private final long expirationTime;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.expiration}") long expirationTime){
        this.secretKey=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationTime = expirationTime;
    }

    public String generateToken(Long userId,String email,String role) {
        Date now=new Date();
        Date expiration=new Date(now.getTime() + expirationTime);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email",email)
                .claim("role",role)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    public String extractUserId(String token){
        return extractAllClaims(token).getSubject();
    }

    public String extractEmail(String token){
        return extractAllClaims(token).get("email",String.class);
    }

    public String extractRole(String token){
        return extractAllClaims(token).get("role",String.class);
    }

    public boolean isTokenValid(String token){
        try{
            extractAllClaims(token);
            return true;
        }catch(Exception exception){
            return false;
        }
    }

    private Claims extractAllClaims(String token){
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
