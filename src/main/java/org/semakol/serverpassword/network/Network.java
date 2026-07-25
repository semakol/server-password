package org.semakol.serverpassword.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.semakol.serverpassword.client.ClientPayloadHandlers;
import org.semakol.serverpassword.server.ServerPayloadHandlers;

/**
 * Payload registration.
 *
 * <p>Both payloads are registered as required (not {@code optional()}), which is what makes the mod
 * mandatory: NeoForge refuses a connection whose other side lacks a required channel, so a vanilla
 * client cannot bypass the password by simply not having the mod.
 */
public final class Network {
    private static final String VERSION = "1";

    private Network() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.configurationToClient(
                ChallengePayload.TYPE, ChallengePayload.STREAM_CODEC, ClientPayloadHandlers::handleChallenge);
        registrar.configurationToServer(
                ProofPayload.TYPE, ProofPayload.STREAM_CODEC, ServerPayloadHandlers::handleProof);
    }
}
