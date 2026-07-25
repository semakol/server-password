package org.semakol.serverpassword.server;

import com.mojang.authlib.GameProfile;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import org.semakol.serverpassword.Serverpassword;

/**
 * Holds the connection in the configuration phase until the password checks out, so a wrong password
 * never results in a player entity being spawned into the world.
 *
 * <p>The task deliberately does not finish itself: {@link ServerPayloadHandlers} finishes it once the
 * client's proof is accepted.
 */
public record PasswordConfigurationTask(ServerConfigurationPacketListener listener) implements ICustomConfigurationTask {
    public static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type(Serverpassword.MODID + ":authenticate");

    @Override
    public void run(Consumer<CustomPacketPayload> sender) {
        ServerPasswordManager manager = ServerPasswordManager.getOrNull();
        if (manager == null) {
            listener.disconnect(Component.translatable("serverpassword.disconnect.not_ready"));
            return;
        }
        // playerProfile() is reachable via this mod's access transformer. Fail closed rather than
        // throwing mid-handshake if some other mod ever supplies a different implementation.
        if (!(listener instanceof ServerConfigurationPacketListenerImpl impl)) {
            listener.disconnect(Component.translatable("serverpassword.disconnect.protocol_error"));
            return;
        }
        GameProfile profile = impl.playerProfile();
        switch (manager.beginChallenge(listener.getConnection(), profile, sender)) {
            case ServerPasswordManager.ChallengeResult.Sent ignored -> {
                // Waiting on the client; ServerPayloadHandlers finishes this task.
            }
            case ServerPasswordManager.ChallengeResult.Skipped ignored ->
                listener.finishCurrentTask(TYPE);
            case ServerPasswordManager.ChallengeResult.Rejected rejected ->
                listener.disconnect(rejected.reason());
        }
    }

    @Override
    public ConfigurationTask.Type type() {
        return TYPE;
    }
}
