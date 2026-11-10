package org.Cry_Legende.sHVoidItems.give;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Remise dans l'inventaire porté : il faut autant d'emplacements libres que de piles à donner. */
public final class InventoryGiver implements ItemGiver {

    @Override
    public boolean fits(Player player, ItemStack template, int amount) {
        int stackSize = Math.max(1, template.getMaxStackSize());
        int slotsNeeded = (amount + stackSize - 1) / stackSize;
        int free = 0;
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null || slot.isEmpty()) free++;
        }
        return free >= slotsNeeded;
    }

    @Override
    public void give(Player player, ItemStack template, int amount) {
        spill(player, player.getInventory().addItem(stacks(template, amount)).values());
    }

    /** {@code amount} exemplaires découpés en piles de la taille maximale de l'item. */
    static ItemStack[] stacks(ItemStack template, int amount) {
        int stackSize = Math.max(1, template.getMaxStackSize());
        List<ItemStack> stacks = new ArrayList<>();
        for (int remaining = amount; remaining > 0; remaining -= stackSize) {
            ItemStack stack = template.clone();
            stack.setAmount(Math.min(remaining, stackSize));
            stacks.add(stack);
        }
        return stacks.toArray(new ItemStack[0]);
    }

    /** Ce qui n'a pas trouvé de place est jeté aux pieds du joueur plutôt que perdu. */
    static void spill(Player player, Collection<ItemStack> leftovers) {
        for (ItemStack leftover : leftovers) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }
}
