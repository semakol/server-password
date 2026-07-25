package org.semakol.serverpassword.server;

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
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.semakol.serverpassword.PasswordCrypto;
import org.slf4j.Logger;

/**
 * The server's password file, stored next to the world as {@code serverpassword/passwords.json}.
 *
 * <p>Keyed by lowercased player name rather than UUID: on an offline server the UUID is derived from
 * the name anyway, and a name-keyed file is the one an admin can actually read and edit.
 *
 * <p>All access happens on the server thread except {@link #save()}, so a concurrent map plus
 * atomic file replacement is enough.
 */
public final class PasswordStorage {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int FORMAT_VERSION = 1;

    /** One registered player. {@code verifier} is the PBKDF2 output, never the password. */
    public record Entry(byte[] salt, int iterations, byte[] verifier) {}

    private final Path file;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    /**
     * Set when a file exists but could not be parsed. An empty in-memory store would otherwise be
     * indistinguishable from "nobody has registered yet", so the next login would happily re-register
     * a name and overwrite everyone else's password on save.
     */
    private volatile boolean unreadable;

    private PasswordStorage(Path file) {
        this.file = file;
    }

    public static PasswordStorage load(MinecraftServer server) {
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("serverpassword").resolve("passwords.json");
        PasswordStorage storage = new PasswordStorage(file);
        storage.read();
        return storage;
    }

    private void read() {
        if (!Files.isRegularFile(file)) {
            LOGGER.info("No password file yet, starting empty ({})", file);
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            int version = root.has("version") ? root.get("version").getAsInt() : FORMAT_VERSION;
            if (version != FORMAT_VERSION) {
                throw new IOException("Unsupported password file version " + version);
            }
            JsonObject players = root.getAsJsonObject("players");
            if (players != null) {
                for (String name : players.keySet()) {
                    JsonObject entry = players.getAsJsonObject(name);
                    entries.put(PasswordCrypto.normalizeName(name), new Entry(
                            Base64.getDecoder().decode(entry.get("salt").getAsString()),
                            entry.get("iterations").getAsInt(),
                            Base64.getDecoder().decode(entry.get("verifier").getAsString())));
                }
            }
            LOGGER.info("Loaded {} stored password(s) from {}", entries.size(), file);
        } catch (Exception e) {
            // Fail closed: keep the original file untouched and turn logins away until an admin fixes
            // it, rather than letting anyone re-register a name that already has a password.
            LOGGER.error("Could not read {} — logins are refused until this file is fixed or removed", file, e);
            entries.clear();
            unreadable = true;
        }
    }

    /** True when the on-disk file could not be parsed, so no login can be trusted. */
    public boolean isUnreadable() {
        return unreadable;
    }

    /** Writes the file atomically so a crash mid-save cannot truncate everyone's password. */
    public synchronized void save() {
        if (unreadable) {
            LOGGER.error("Refusing to overwrite {}: it could not be read at startup", file);
            return;
        }
        JsonObject players = new JsonObject();
        // Sorted so hand-edits and diffs stay readable.
        for (Map.Entry<String, Entry> e : new TreeMap<>(entries).entrySet()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("salt", Base64.getEncoder().encodeToString(e.getValue().salt()));
            entry.addProperty("iterations", e.getValue().iterations());
            entry.addProperty("verifier", Base64.getEncoder().encodeToString(e.getValue().verifier()));
            players.add(e.getKey(), entry);
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", FORMAT_VERSION);
        root.add("players", players);

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
    public Entry get(String playerName) {
        return entries.get(PasswordCrypto.normalizeName(playerName));
    }

    public boolean isRegistered(String playerName) {
        return get(playerName) != null;
    }

    public void put(String playerName, Entry entry) {
        entries.put(PasswordCrypto.normalizeName(playerName), entry);
        save();
    }

    /** @return true if a password existed and was removed */
    public boolean remove(String playerName) {
        if (entries.remove(PasswordCrypto.normalizeName(playerName)) == null) {
            return false;
        }
        save();
        return true;
    }

    public Set<String> registeredNames() {
        return Collections.unmodifiableSet(entries.keySet());
    }
}
