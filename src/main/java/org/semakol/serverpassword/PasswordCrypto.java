package org.semakol.serverpassword;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Crypto used by the login handshake.
 *
 * <p>An offline-mode server never enables packet encryption (that only happens as part of the
 * Mojang auth flow), so the plain password must never travel over the wire. Instead:
 *
 * <ol>
 * <li>the client stretches the password into a 32-byte key with PBKDF2 and a per-player salt,</li>
 * <li>the server stores that key as a verifier and answers every later login with a fresh nonce,</li>
 * <li>the client proves it knows the key by returning {@code HMAC-SHA256(key, nonce)}.</li>
 * </ol>
 *
 * <p>Only the registration packet carries the key itself; every subsequent login carries a
 * single-use proof, so sniffing one login does not let an attacker replay it. The verifier on disk
 * is enough to log in, so {@code passwords.json} deserves the same protection as {@code ops.json} —
 * but PBKDF2 keeps it from revealing the password itself if the file leaks.
 */
public final class PasswordCrypto {
    /** OWASP's 2023 floor for PBKDF2-HMAC-SHA256. Only the client pays this cost, once per login. */
    public static final int ITERATIONS = 210_000;
    public static final int SALT_BYTES = 16;
    public static final int NONCE_BYTES = 32;
    public static final int KEY_BYTES = 32;

    private static final String KDF = "PBKDF2WithHmacSHA256";
    private static final String MAC = "HmacSHA256";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordCrypto() {}

    public static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    /** Stretches a password into the verifier key. Takes ~200ms, so never call this on a game thread. */
    public static byte[] deriveKey(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BYTES * 8);
        try {
            return SecretKeyFactory.getInstance(KDF).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2 is unavailable on this JVM", e);
        } finally {
            spec.clearPassword();
        }
    }

    /** {@code HMAC-SHA256(key, nonce)} — the single-use proof a returning player sends. */
    public static byte[] proof(byte[] key, byte[] nonce) {
        try {
            Mac mac = Mac.getInstance(MAC);
            mac.init(new SecretKeySpec(key, MAC));
            return mac.doFinal(nonce);
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable on this JVM", e);
        }
    }

    /** Length-independent, timing-safe comparison. */
    public static boolean matches(byte[] a, byte[] b) {
        return MessageDigest.isEqual(a, b);
    }

    public static void wipe(byte[] secret) {
        if (secret != null) {
            Arrays.fill(secret, (byte) 0);
        }
    }

    /** Normalises a player name so lookups are case-insensitive, matching vanilla's own behaviour. */
    public static String normalizeName(String name) {
        return name.toLowerCase(java.util.Locale.ROOT);
    }

    public static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
