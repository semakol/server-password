package org.semakol.serverpassword.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

/**
 * The single password this client sends to every server that asks for one.
 *
 * <p>Stored in plain text in {@code config/serverpassword-local.json}, because the password itself is
 * the input to the KDF and there is nowhere on the client to keep a key that the client could not
 * also read. This is the same trust model as any launcher's saved session: whoever can read your game
 * folder can read this. The file says so too.
 */
public final class ClientPasswordStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String WARNING =
            "This password is NOT encrypted. Anyone who can read this file can log in as you.";

    @Nullable
    private static String password;
    private static boolean loaded;

    private ClientPasswordStore() {}

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("serverpassword-local.json");
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path file = file();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("password") && !root.get("password").isJsonNull()) {
                String stored = root.get("password").getAsString();
                password = stored.isEmpty() ? null : stored;
            }
        } catch (Exception e) {
            LOGGER.error("Could not read {}, starting with no saved password", file, e);
        }
    }

    private static void save() {
        JsonObject root = new JsonObject();
        root.addProperty("_warning", WARNING);
        root.addProperty("password", password == null ? "" : password);

        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("Failed to save {}", file, e);
        }
    }

    @Nullable
    public static synchronized String get() {
        ensureLoaded();
        return password;
    }

    public static synchronized boolean has() {
        return get() != null;
    }

    public static synchronized void set(String newPassword) {
        ensureLoaded();
        password = newPassword;
        save();
    }
}
