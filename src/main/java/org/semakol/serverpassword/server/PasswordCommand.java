package org.semakol.serverpassword.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.semakol.serverpassword.Serverpassword;

/**
 * {@code /serverpassword} — operator tools for the stored passwords. There is deliberately no way to
 * read or set a password from here: the server only ever holds a verifier, and a password typed into
 * chat would end up in logs.
 */
public final class PasswordCommand {
    private PasswordCommand() {}

    private static final SuggestionProvider<CommandSourceStack> REGISTERED_NAMES = (context, builder) -> {
        ServerPasswordManager manager = ServerPasswordManager.getOrNull();
        return SharedSuggestionProvider.suggest(
                manager == null ? java.util.List.<String>of() : manager.storage().registeredNames(), builder);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(Serverpassword.MODID)
                .requires(source -> source.hasPermission(3))
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(REGISTERED_NAMES)
                                .executes(context -> reset(context.getSource(),
                                        StringArgumentType.getString(context, "player")))))
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource()))));
    }

    /**
     * Clears a player's password so their next login registers a new one. Kicks them first: leaving
     * them connected would let whoever is online keep the session they can no longer prove.
     */
    private static int reset(CommandSourceStack source, String playerName) {
        ServerPasswordManager manager = ServerPasswordManager.getOrNull();
        if (manager == null) {
            source.sendFailure(Component.translatable("serverpassword.command.unavailable"));
            return 0;
        }
        if (!manager.storage().remove(playerName)) {
            source.sendFailure(Component.translatable("serverpassword.command.reset.unknown", playerName));
            return 0;
        }

        ServerPlayer online = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (online != null) {
            online.connection.disconnect(Component.translatable("serverpassword.disconnect.password_reset"));
        }
        source.sendSuccess(() -> Component.translatable("serverpassword.command.reset.success", playerName), true);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        ServerPasswordManager manager = ServerPasswordManager.getOrNull();
        if (manager == null) {
            source.sendFailure(Component.translatable("serverpassword.command.unavailable"));
            return 0;
        }
        var names = manager.storage().registeredNames();
        if (names.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("serverpassword.command.list.empty"), false);
            return 0;
        }
        String joined = names.stream().sorted().reduce((a, b) -> a + ", " + b).orElse("");
        source.sendSuccess(() -> Component.translatable("serverpassword.command.list", names.size(), joined), false);
        return names.size();
    }
}
