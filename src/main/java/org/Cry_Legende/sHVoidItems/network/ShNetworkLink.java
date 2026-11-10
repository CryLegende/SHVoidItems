package org.Cry_Legende.sHVoidItems.network;

import com.google.gson.JsonObject;
import org.Cry_Legende.sHNetwork.api.SHNetworkAPI;
import org.Cry_Legende.sHSkyBridge.api.SkyBridgeAPI;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.BiConsumer;

/** Pont vers SHSkyBridge et SHNetwork. Cette classe n'est chargée que si les deux sont installés. */
public final class ShNetworkLink implements NetworkLink {

    private static final String CHANNEL = "shvoiditems";

    private final SHNetworkAPI network;
    private final SkyBridgeAPI hosts;

    private ShNetworkLink() {
        this.network = SHNetworkAPI.get();
        this.hosts = SkyBridgeAPI.get();
    }

    /** Point d'entrée : le plugin ne résout cette classe qu'à cet appel, donc jamais sans SHNetwork ni SHSkyBridge. */
    public static NetworkLink open() {
        return new ShNetworkLink();
    }

    @Override
    public boolean isHostedHere(UUID island) {
        return hosts.isHostedHere(island);
    }

    @Override
    public String hostOf(UUID island) {
        return hosts.hostOf(island);
    }

    @Override
    public void sendToHost(Player player, UUID island) {
        JsonObject message = new JsonObject();
        message.addProperty("joueur", player.getUniqueId().toString());
        message.addProperty("ile", island.toString());
        // Publié avant le transfert : le message précède l'arrivée du joueur sur le serveur hôte
        network.publish(CHANNEL, message.toString());
        network.sendTo(player, hosts.hostOf(island));
    }

    @Override
    public void listenArrivals(BiConsumer<UUID, UUID> handler) {
        network.subscribe(CHANNEL, message -> {
            try {
                JsonObject json = message.object();
                handler.accept(UUID.fromString(json.get("joueur").getAsString()), UUID.fromString(json.get("ile").getAsString()));
            } catch (RuntimeException ignored) {
                // message illisible : sans effet
            }
        }, true);
    }
}
