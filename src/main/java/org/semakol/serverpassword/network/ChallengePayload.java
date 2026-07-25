package org.semakol.serverpassword.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.semakol.serverpassword.PasswordCrypto;
import org.semakol.serverpassword.Serverpassword;

/**
 * Server -> client, during the configuration phase: "prove you know this account's password".
 *
 * @param salt       the player's KDF salt (freshly generated when {@code registering})
 * @param iterations PBKDF2 iteration count the client must use
 * @param nonce      single-use challenge; the client answers with {@code HMAC(key, nonce)}
 * @param registering true when this name has no password yet, so the client should send the key
 *                    itself to claim the name
 */
public record ChallengePayload(byte[] salt, int iterations, byte[] nonce, boolean registering) implements CustomPacketPayload {
    public static final Type<ChallengePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Serverpassword.MODID, "challenge"));

    public static final StreamCodec<FriendlyByteBuf, ChallengePayload> STREAM_CODEC = StreamCodec.of(
            ChallengePayload::write, ChallengePayload::read);

    private static void write(FriendlyByteBuf buf, ChallengePayload payload) {
        buf.writeByteArray(payload.salt);
        buf.writeVarInt(payload.iterations);
        buf.writeByteArray(payload.nonce);
        buf.writeBoolean(payload.registering);
    }

    private static ChallengePayload read(FriendlyByteBuf buf) {
        byte[] salt = buf.readByteArray(PasswordCrypto.SALT_BYTES);
        int iterations = buf.readVarInt();
        byte[] nonce = buf.readByteArray(PasswordCrypto.NONCE_BYTES);
        return new ChallengePayload(salt, iterations, nonce, buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
