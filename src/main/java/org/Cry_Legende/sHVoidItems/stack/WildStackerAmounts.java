package org.Cry_Legende.sHVoidItems.stack;

import com.bgsoftware.wildstacker.api.WildStackerAPI;
import com.bgsoftware.wildstacker.api.objects.StackedItem;
import org.bukkit.entity.Item;

/** Lecture des piles de WildStacker. Cette classe n'est chargée que si WildStacker est activé. */
public final class WildStackerAmounts implements StackAmounts {

    private WildStackerAmounts() {
    }

    /** Point d'entrée : le plugin ne résout cette classe qu'à cet appel, donc jamais sans WildStacker. */
    public static StackAmounts open() {
        if (WildStackerAPI.getWildStacker() == null) throw new IllegalStateException("API de WildStacker pas encore prête");
        return new WildStackerAmounts();
    }

    @Override
    public int of(Item item, int visible) {
        try {
            StackedItem stacked = WildStackerAPI.getStackedItem(item);
            return stacked == null ? visible : Math.max(visible, stacked.getStackAmount());
        } catch (RuntimeException e) {
            // WildStacker ne gère pas cet item : la quantité de l'entité fait foi
            return visible;
        }
    }
}
