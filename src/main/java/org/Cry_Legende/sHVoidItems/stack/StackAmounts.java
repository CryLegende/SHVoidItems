package org.Cry_Legende.sHVoidItems.stack;

import org.bukkit.entity.Item;

/**
 * Quantité réelle d'un item au sol. Cette interface ne mentionne aucune classe de WildStacker : le plugin la manipule sans
 * jamais charger {@link WildStackerAmounts}, donc sans WildStacker c'est la quantité de l'entité qui fait foi.
 */
public interface StackAmounts {

    /**
     * Quantité réelle de l'item ; {@code visible} est celle de son ItemStack, plafonnée à la taille de pile.
     * Jamais inférieure à {@code visible}.
     */
    int of(Item item, int visible);
}
