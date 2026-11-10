package org.Cry_Legende.sHVoidItems.give;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Remise des items rachetés au joueur. Cette interface ne mentionne pas SHInventoryPages : le plugin la manipule sans
 * jamais charger {@link PagesGiver}, donc sans lui seul l'inventaire porté reçoit les items.
 */
public interface ItemGiver {

    /** Vrai si toute la quantité tient chez le joueur : à vérifier avant de le faire payer. */
    boolean fits(Player player, ItemStack template, int amount);

    /** Donne exactement {@code amount} exemplaires ; ce qui ne trouve pas de place tombe aux pieds du joueur. */
    void give(Player player, ItemStack template, int amount);
}
