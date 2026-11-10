package org.Cry_Legende.sHVoidItems;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Menu du Marchand des abîmes : l'île affichée et la page montrée, fixées à l'ouverture. Un menu dont le nombre de pages
 * a changé (titre périmé) ou qui change de page est remplacé par un nouveau, jamais modifié.
 */
final class VoidMenu implements InventoryHolder {

    static final int SIZE = 54;

    private final UUID island;
    private final int page;
    private final int pages;
    private final Inventory inventory;

    VoidMenu(UUID island, int page, int pages, Component title) {
        this.island = island;
        this.page = page;
        this.pages = pages;
        this.inventory = Bukkit.createInventory(this, SIZE, title);
    }

    UUID island() {
        return island;
    }

    /** Page montrée, à partir de 1. */
    int page() {
        return page;
    }

    /** Nombre de pages au moment de l'ouverture (celui affiché dans le titre). */
    int pages() {
        return pages;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
