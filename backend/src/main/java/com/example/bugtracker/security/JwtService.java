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
 *
 * <p>The secret is required, not defaulted. See {@link #requireUsableSecret}: an
 * image that carries a built-in signing key is an image where every deployment
 * shares a publicly-known key.
 */
@Service
public class JwtService {

    /**
     * The dev-only default that used to live in {@code application.yml}. Kept here
     * only so we can explicitly reject it — anyone upgrading still has it sitting in
     * a local shell, a compose file or a CI variable, and silently continuing to
     * accept it would mean the "fix" only applies to fresh installs.
     */
    private static final String LEGACY_DEV_SECRET =
            "dev-only-secret-change-me-0123456789abcdefghijklmnopqrstuvwxyz";

    /** HS256 signs with HMAC-SHA256, which RFC 7518 requires to be >= 256 bits. */
    private static final int MIN_SECRET_BYTES = 32;

    /** HS256 needs at least 256 bits of key; ours is far longer. */
    private final byte[] secretBytes;

    private final Duration ttl;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.ttl-hours}") long ttlHours) {
        this.secretBytes = requireUsableSecret(secret);
        this.ttl = Duration.ofHours(ttlHours);
    }

    /**
     * Validates the signing secret at construction, which happens during context
     * startup. Failing here is deliberate: a service that starts with a guessable
     * key looks perfectly healthy, logs no warnings, and returns HTTP 200 to
     * attackers. Failing loudly at boot is the only safe default.
     *
     * @throws IllegalStateException if the secret is absent, too short for HS256, or
     *                               the historical dev placeholder
     */
    private static byte[] requireUsableSecret(String secret) {
        String problem = null;

        if (secret == null || secret.isBlank()) {
            problem = "it is not set";
        } else if (secret.equals(LEGACY_DEV_SECRET)) {
            problem = "it is still the old development placeholder, which is public "
                    + "knowledge for anyone who has seen this repository or image";
        } else if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            problem = "it is shorter than " + MIN_SECRET_BYTES + " bytes, which is the "
                    + "minimum HS256 key length (RFC 7518 §3.2)";
        }

        if (problem != null) {
            throw new IllegalStateException(
                    "Refusing to start: the JWT signing secret is invalid because " + problem + ".\n"
                    + "\n"
                    + "Anyone who knows this secret can mint a valid token for any user id and\n"
                    + "bypass authentication and the project-membership checks entirely. There is\n"
                    + "deliberately no built-in default, so this has to be supplied explicitly.\n"
                    + "\n"
                    + "  Generate one:  openssl rand -base64 48\n"
                    + "  No openssl?    python3 -c 'import secrets;print(secrets.token_urlsafe(48))'\n"
                    + "  Then export it: export JWT_SECRET=\"$(openssl rand -base64 48)\"\n"
                    + "\n"
                    + "On Railway, set it as a service variable instead of exporting it.");
        }

        return secret.getBytes(StandardCharsets.UTF_8);
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