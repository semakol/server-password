package org.semakol.serverpassword.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.semakol.serverpassword.PasswordCrypto;
import org.semakol.serverpassword.Serverpassword;

/**
 * Client -> server answer to {@link ChallengePayload}.
 *
 * <p>{@code data} is the verifier key when the server asked to register the name, and
 * {@code HMAC(key, nonce)} on every later login. The server knows which one it asked for, so the
 * client cannot pick.
 */
public record ProofPayload(byte[] data) implements CustomPacketPayload {
    public static final Type<ProofPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Serverpassword.MODID, "proof"));

    public static final StreamCodec<FriendlyByteBuf, ProofPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeByteArray(payload.data),
            buf -> new ProofPayload(buf.readByteArray(PasswordCrypto.KEY_BYTES)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
