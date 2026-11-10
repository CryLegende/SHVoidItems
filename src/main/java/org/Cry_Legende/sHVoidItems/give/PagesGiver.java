package org.Cry_Legende.sHVoidItems.give;

import org.Cry_Legende.sHInventoryPages.api.ISHInventoryPagesAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Remise par SHInventoryPages : l'inventaire porté d'abord, puis les autres pages du joueur.
 * Cette classe n'est chargée que si SHInventoryPages est activé.
 */
public final class PagesGiver implements ItemGiver {

    private final ItemGiver fallback = new InventoryGiver();

    private PagesGiver() {
    }

    /** Point d'entrée : le plugin ne résout cette classe qu'à cet appel, donc jamais sans SHInventoryPages. */
    public static ItemGiver open() {
        return new PagesGiver();
    }

    /** L'API est relue à chaque appel : SHInventoryPages en remplace l'instance quand il est rechargé. */
    private static ISHInventoryPagesAPI api() {
        return Bukkit.getServicesManager().load(ISHInventoryPagesAPI.class);
    }

    @Override
    public boolean fits(Player player, ItemStack template, int amount) {
        ISHInventoryPagesAPI api = api();
        if (api == null) return fallback.fits(player, template, amount);
        return api.getFreeSpace(player, template.asOne()) >= amount;
    }

    @Override
    public void give(Player player, ItemStack template, int amount) {
        ISHInventoryPagesAPI api = api();
        if (api == null) {
            fallback.give(player, template, amount);
            return;
        }
        InventoryGiver.spill(player, api.addItems(player, InventoryGiver.stacks(template, amount)).values());
    }
}
