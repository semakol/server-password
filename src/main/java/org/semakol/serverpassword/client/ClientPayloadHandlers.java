package org.semakol.serverpassword.client;

import com.mojang.logging.LogUtils;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.semakol.serverpassword.PasswordCrypto;
import org.semakol.serverpassword.network.ChallengePayload;
import org.semakol.serverpassword.network.ProofPayload;
import org.slf4j.Logger;

/** Answers the server's login challenge with a proof derived from the locally saved password. */
public final class ClientPayloadHandlers {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ClientPayloadHandlers() {}

    public static void handleChallenge(ChallengePayload payload, IPayloadContext context) {
        @Nullable String password = ClientPasswordStore.get();
        if (password == null) {
            context.disconnect(Component.translatable(payload.registering()
                    ? "serverpassword.disconnect.client_no_password_new"
                    : "serverpassword.disconnect.client_no_password"));
            return;
        }

        // PBKDF2 at 210k iterations takes a couple of hundred milliseconds — too long for the client
        // thread. Netty writes are thread-safe, so the reply can be sent from the worker.
        CompletableFuture.runAsync(() -> {
            try {
                byte[] key = PasswordCrypto.deriveKey(password, payload.salt(), payload.iterations());
                if (payload.registering()) {
                    // Claiming the name: the server needs the verifier itself, once.
                    context.reply(new ProofPayload(key));
                } else {
                    byte[] proof = PasswordCrypto.proof(key, payload.nonce());
                    PasswordCrypto.wipe(key);
                    context.reply(new ProofPayload(proof));
                }
            } catch (Exception e) {
                LOGGER.error("Failed to answer the password challenge", e);
                context.disconnect(Component.translatable("serverpassword.disconnect.client_error"));
            }
        });
    }
}
