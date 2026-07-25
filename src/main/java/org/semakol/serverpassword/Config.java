package org.semakol.serverpassword;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side settings. Read on the server only; the client never needs them. */
@EventBusSubscriber(modid = Serverpassword.MODID)
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue ALLOW_NEW_REGISTRATIONS = BUILDER
            .comment("Whether a name with no stored password may claim one by logging in.",
                    "Turn this off once everyone has registered to stop unknown names joining at all.")
            .define("allowNewRegistrations", true);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean allowNewRegistrations = true;

    private Config() {}

    // Subscribed to Loading and Reloading individually, never to the ModConfigEvent base class:
    // Unloading is also a subclass, and it fires as the server stops, once the values can no longer be
    // read. Reading one there throws and takes the server thread down with it.
    @SubscribeEvent
    static void onLoad(final ModConfigEvent.Loading event) {
        sync(event);
    }

    @SubscribeEvent
    static void onReload(final ModConfigEvent.Reloading event) {
        sync(event);
    }

    private static void sync(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            allowNewRegistrations = ALLOW_NEW_REGISTRATIONS.get();
        }
    }
}
