package org.semakol.serverpassword;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.semakol.serverpassword.network.Network;
import org.semakol.serverpassword.server.PasswordConfigurationTask;

/**
 * Per-player passwords for offline-mode servers.
 *
 * <p>Clients set a password per server from the multiplayer screen. On the first login the server
 * stores a verifier derived from it; every login after that has to prove knowledge of the same
 * password or the connection is dropped before the player enters the world.
 */
@Mod(Serverpassword.MODID)
public class Serverpassword {
    public static final String MODID = "serverpassword";

    public Serverpassword(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(RegisterPayloadHandlersEvent.class, Network::register);
        modEventBus.addListener(RegisterConfigurationTasksEvent.class, Serverpassword::registerConfigurationTasks);

        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    }

    /** Fired per incoming connection; queues the password check before the player reaches the world. */
    private static void registerConfigurationTasks(RegisterConfigurationTasksEvent event) {
        event.register(new PasswordConfigurationTask(event.getListener()));
    }
}
