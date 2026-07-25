package org.semakol.serverpassword.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.semakol.serverpassword.Serverpassword;
import org.semakol.serverpassword.client.gui.PasswordEntryScreen;

/**
 * Adds the "Password" button to the multiplayer server list, and the first time a player with no
 * password heads there, shows the password screen instead — without one they cannot join anyway.
 */
@EventBusSubscriber(modid = Serverpassword.MODID, value = Dist.CLIENT)
public final class ClientGuiEvents {
    private static final int BUTTON_WIDTH = 80;

    private ClientGuiEvents() {}

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof JoinMultiplayerScreen screen)) {
            return;
        }
        // Top-right corner: both footer rows are already full of vanilla buttons.
        event.addListener(Button.builder(Component.translatable("serverpassword.gui.button"),
                        button -> screen.getMinecraft().setScreen(new PasswordEntryScreen(screen)))
                .bounds(screen.width - BUTTON_WIDTH - 6, 6, BUTTON_WIDTH, 20)
                .tooltip(Tooltip.create(Component.translatable("serverpassword.gui.button.tooltip")))
                .build());
    }

    /**
     * Shown whenever no password is set, every time the player heads for the server list — without one
     * no password-protected server will let them in.
     *
     * <p>Substituted at Opening rather than stacked on top of the list once it is showing: that list
     * only starts its LAN scan on its first init, and coming back to an instance it had already left
     * would leave LAN servers permanently undetected. Replacing it before it ever initialises means
     * saving shows a pristine one, and Back returns to whatever the player came from.
     *
     * <p>This cannot loop: Back leads away from the server list, and after Save there is a password.
     */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof JoinMultiplayerScreen serverList) || ClientPasswordStore.has()) {
            return;
        }
        event.setNewScreen(new PasswordEntryScreen(serverList, event.getCurrentScreen()));
    }
}
