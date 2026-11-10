package org.Cry_Legende.sHVoidItems.network;

import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Ce dont le mode réseau a besoin de SHSkyBridge et de SHNetwork. Cette interface ne mentionne aucune de leurs classes :
 * le plugin la manipule sans jamais charger {@link ShNetworkLink}, donc sans eux il ne se passe rien.
 */
public interface NetworkLink {

    /** Vrai si l'île est hébergée par ce serveur : seul cas où ses données sont à jour ici. */
    boolean isHostedHere(UUID island);

    /** Serveur hôte de l'île. */
    String hostOf(UUID island);

    /** Annonce au serveur hôte l'arrivée du joueur (ouverture du menu de l'île), puis l'y envoie. */
    void sendToHost(Player player, UUID island);

    /** Écoute les arrivées annoncées par les autres serveurs ; le gestionnaire reçoit (joueur, île) sur le fil principal. */
    void listenArrivals(BiConsumer<UUID, UUID> handler);
}
