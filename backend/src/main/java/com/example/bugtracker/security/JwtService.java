package com.example.bugtracker.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies JWTs using Nimbus, which ships inside
 * spring-boot-starter-oauth2-resource-server — no third-party JWT library.
 *
 * <p>The matching {@code JwtDecoder} in {@link SecurityConfig} derives from the
 * same secret, so a token minted here validates there.
 */
@Service
public class JwtService {

    /** HS256 needs at least 256 bits of key; ours is far longer. */
    private final byte[] secretBytes;

    private final Duration ttl;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.ttl-hours}") long ttlHours) {
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.ttl = Duration.ofHours(ttlHours);
    }

    /**
     * Mints a token for a user. The subject is the user id so the decoder can
     * resolve the account on every request; email is a convenience claim.
     */
    public String issueToken(Long userId, String email) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(ttl)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(new MACSigner(secretBytes));
        } catch (JOSEException e) {
            // Only thrown if the key is too short for HS256 — a startup-class bug.
            throw new IllegalStateException("Could not sign JWT", e);
        }
        return jwt.serialize();
    }

    public Instant expiresAt() {
        return Instant.now().plus(ttl);
    }

    public long ttlSeconds() {
        return ttl.toSeconds();
    }

    public byte[] secretBytes() {
        return secretBytes.clone();
    }

    /** Parses the subject without verifying the signature; callers verify first. */
    public Long extractUserId(String token) throws ParseException {
        return Long.parseLong(SignedJWT.parse(token).getJWTClaimsSet().getSubject());
    }

    /** Used by tests and tooling that need to check a token without a decoder. */
    public boolean verifySignature(String token) {
        try {
            return SignedJWT.parse(token).verify(new MACVerifier(secretBytes));
        } catch (JOSEException | ParseException e) {
            return false;
        }
    }
}