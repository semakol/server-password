package org.semakol.serverpassword.server;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.semakol.serverpassword.network.ProofPayload;

/** Handles the client's answer to the login challenge. Runs on the server thread. */
public final class ServerPayloadHandlers {
    private ServerPayloadHandlers() {}

    public static void handleProof(ProofPayload payload, IPayloadContext context) {
        ServerPasswordManager manager = ServerPasswordManager.getOrNull();
        if (manager == null) {
            context.disconnect(Component.translatable("serverpassword.disconnect.not_ready"));
            return;
        }

        ServerPasswordManager.Result result = manager.verify(context.connection(), payload);
        if (result instanceof ServerPasswordManager.Result.Rejected rejected) {
            context.disconnect(rejected.reason());
            return;
        }
        // Accepted: release the connection into the rest of the configuration phase.
        context.finishCurrentTask(PasswordConfigurationTask.TYPE);
    }
}
