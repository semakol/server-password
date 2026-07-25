package org.semakol.serverpassword.server;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import org.semakol.serverpassword.Config;
import org.semakol.serverpassword.PasswordCrypto;
import org.semakol.serverpassword.network.ChallengePayload;
import org.semakol.serverpassword.network.ProofPayload;
import org.slf4j.Logger;

/**
 * Server-side authentication state: who is currently mid-handshake, and the stored verifiers.
 *
 * <p>Lives only while a server is running. Everything here runs on the server thread apart from
 * {@link #rememberProfile}, which is called from the login negotiation.
 */
public final class ServerPasswordManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Nullable
    private static ServerPasswordManager instance;

    /**
     * A challenge we sent and are waiting on. {@code verifier} is null while registering, since
     * that is exactly what the client is about to supply.
     */
    private record Pending(String playerName, byte[] salt, int iterations, byte[] nonce, @Nullable byte[] verifier) {
        boolean registering() {
            return verifier == null;
        }
    }

    private final MinecraftServer server;
    private final PasswordStorage storage;

    private final Map<Connection, Pending> pending = new ConcurrentHashMap<>();

    private ServerPasswordManager(MinecraftServer server, PasswordStorage storage) {
        this.server = server;
        this.storage = storage;
    }

    public static void start(MinecraftServer server) {
        instance = new ServerPasswordManager(server, PasswordStorage.load(server));
    }

    public static void stop() {
        instance = null;
    }

    @Nullable
    public static ServerPasswordManager getOrNull() {
        return instance;
    }

    public PasswordStorage storage() {
        return storage;
    }

    /** What the configuration task should do after {@link #beginChallenge}. */
    public sealed interface ChallengeResult {
        /** Challenge sent; wait for the client's proof. */
        record Sent() implements ChallengeResult {}

        /** No password needed for this player; let them straight through. */
        record Skipped() implements ChallengeResult {}

        record Rejected(Component reason) implements ChallengeResult {}
    }

    /** Builds and sends the challenge for a connecting player. */
    public ChallengeResult beginChallenge(Connection connection, @Nullable GameProfile profile,
            Consumer<CustomPacketPayload> sender) {
        if (profile == null || profile.getName() == null) {
            LOGGER.warn("Configuration phase reached with no known profile, rejecting connection");
            return new ChallengeResult.Rejected(Component.translatable("serverpassword.disconnect.protocol_error"));
        }

        // The host of a singleplayer or LAN world is already authenticated by owning the game folder.
        if (server.isSingleplayerOwner(profile)) {
            return new ChallengeResult.Skipped();
        }

        if (storage.isUnreadable()) {
            LOGGER.error("Refusing '{}': the password file could not be read", profile.getName());
            return new ChallengeResult.Rejected(Component.translatable("serverpassword.disconnect.storage_broken"));
        }

        String name = profile.getName();
        PasswordStorage.Entry entry = storage.get(name);
        byte[] nonce = PasswordCrypto.randomBytes(PasswordCrypto.NONCE_BYTES);

        if (entry == null) {
            if (!Config.allowNewRegistrations) {
                LOGGER.info("Rejected unregistered player '{}' (new registrations are disabled)", name);
                return new ChallengeResult.Rejected(
                        Component.translatable("serverpassword.disconnect.registration_closed"));
            }
            byte[] salt = PasswordCrypto.randomBytes(PasswordCrypto.SALT_BYTES);
            pending.put(connection, new Pending(name, salt, PasswordCrypto.ITERATIONS, nonce, null));
            sender.accept(new ChallengePayload(salt, PasswordCrypto.ITERATIONS, nonce, true));
        } else {
            pending.put(connection, new Pending(name, entry.salt(), entry.iterations(), nonce, entry.verifier()));
            sender.accept(new ChallengePayload(entry.salt(), entry.iterations(), nonce, false));
        }
        return new ChallengeResult.Sent();
    }

    /** Result of checking a client's answer. */
    public sealed interface Result {
        record Accepted(String playerName, boolean registered) implements Result {}

        record Rejected(Component reason) implements Result {}
    }

    /**
     * Verifies a client's answer to the challenge. The pending challenge is consumed either way, so a
     * nonce is never usable twice and a second proof on the same connection cannot brute-force.
     */
    public Result verify(Connection connection, ProofPayload payload) {
        Pending challenge = pending.remove(connection);
        if (challenge == null) {
            LOGGER.warn("Received an unsolicited password proof, rejecting connection");
            return new Result.Rejected(Component.translatable("serverpassword.disconnect.protocol_error"));
        }

        if (challenge.registering()) {
            if (payload.data().length != PasswordCrypto.KEY_BYTES) {
                return new Result.Rejected(Component.translatable("serverpassword.disconnect.protocol_error"));
            }
            // Re-check: two clients could have raced through registration for the same name.
            if (storage.isRegistered(challenge.playerName())) {
                return new Result.Rejected(Component.translatable("serverpassword.disconnect.retry"));
            }
            storage.put(challenge.playerName(), new PasswordStorage.Entry(
                    challenge.salt(), challenge.iterations(), payload.data()));
            LOGGER.info("Registered a password for '{}'", challenge.playerName());
            return new Result.Accepted(challenge.playerName(), true);
        }

        byte[] expected = PasswordCrypto.proof(challenge.verifier(), challenge.nonce());
        boolean ok = PasswordCrypto.matches(expected, payload.data());
        PasswordCrypto.wipe(expected);
        if (!ok) {
            LOGGER.warn("Rejected '{}': wrong password", challenge.playerName());
            return new Result.Rejected(Component.translatable("serverpassword.disconnect.wrong_password"));
        }
        return new Result.Accepted(challenge.playerName(), false);
    }

    public void forget(Connection connection) {
        pending.remove(connection);
    }
}
