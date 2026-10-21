package org.Cry_Legende.sHVoidItems;

import com.bgsoftware.superiorskyblock.api.world.Dimension;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SHVoidItems extends JavaPlugin implements Listener, CommandExecutor {

    private Economy economy;
    private final Map<UUID, Map<String, TrackedItem>> islandsItems = new ConcurrentHashMap<>();
    private File dataFile;
    private org.bukkit.configuration.file.YamlConfiguration dataConfig;
    private String menuTitleTemplate;
    private int itemsPerPage;
    private String currencyType;
    private Material currencyItem;
    private long defaultExpireSeconds;
    private double defaultPrice;
    private Sound buySound;
    private Set<Material> blacklist;
    private Map<String, String> messages;
    private String countdownFormat;
    private List<String> loreTemplate;
    private Map<String, MenuItemDef> menuItems;
    private double margin;
    private final Map<UUID, BukkitTask> refreshTasks = new HashMap<>();
    private final Map<UUID, Integer> playerPages = new HashMap<>();
    private final Map<UUID, String> playerMenuTitles = new HashMap<>();
    // Île affichée dans le menu de chaque viewer (la sienne, ou celle d'un joueur pour /shvoid <joueur>)
    private final Map<UUID, Island> viewedIslands = new HashMap<>();
    private NamespacedKey itemIdKey;

    @Override
    public void onEnable() {
        itemIdKey = new NamespacedKey(this, "void_item_id");
        saveDefaultConfig();
        loadConfigValues();
        setupEconomy();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("shvoid").setExecutor(this);
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) saveResource("data.yml", false);
        reloadData();
        new BukkitRunnable() {
            public void run() {
                saveData();
            }
        }.runTaskTimerAsynchronously(this, 1200, 1200);
        new ExpireTask().runTaskTimerAsynchronously(this, 0, 1200);
        getLogger().info("SHVoidItems activé.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(colorize(messages.getOrDefault("player-only", "&cCette commande ne peut être utilisée que par un joueur.")));
            return true;
        }

        try {
            if (args.length == 0) {
                openVoidMenu(player, 1);
            } else if (args.length == 1) {
                if (!player.hasPermission("shvoiditems.admin")) {
                    player.sendMessage(colorize(messages.getOrDefault("no-permission", "&cVous n'avez pas la permission d'utiliser cette commande.")));
                    return true;
                }

                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    player.sendMessage(colorize(messages.getOrDefault("player-not-found", "&cJoueur non trouvé.")));
                    return true;
                }

                Island targetIsland = SuperiorSkyblockAPI.getPlayer(target).getIsland();
                if (targetIsland == null) {
                    player.sendMessage(colorize(messages.getOrDefault("target-no-island", "&cCe joueur n'a pas d'île.")));
                    return true;
                }

                openOtherVoidMenu(player, target, targetIsland, 1);
            } else {
                return false;
            }
        } catch (Throwable t) {
            player.sendMessage(ChatColor.RED + "Une erreur interne est survenue, consulte la console pour plus de détails.");
            getLogger().log(Level.SEVERE, "Erreur lors de l'exécution de /" + label + " avec l'argument " + Arrays.toString(args), t);
        }

        return true;
    }

    @Override
    public void onDisable() {
        saveData();
        refreshTasks.values().forEach(BukkitTask::cancel);
    }

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null ||
            (economy = getServer().getServicesManager().getRegistration(Economy.class).getProvider()) == null) {
            getLogger().severe("Vault ou provider introuvable !");
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void loadConfigValues() {
        FileConfiguration cfg = getConfig();
        menuTitleTemplate = cfg.getString("menu.title");
        itemsPerPage = cfg.getInt("menu.items-per-page");
        menuItems = cfg.getConfigurationSection("menu.items").getKeys(false).stream()
            .collect(Collectors.toMap(k -> k, k -> {
                ConfigurationSection s = cfg.getConfigurationSection("menu.items." + k);
                String command = s.getString("command", null);
                return new MenuItemDef(Material.matchMaterial(s.getString("material")), s.getString("display-name"), s.getInt("slot"), command);
            }));
        currencyType = cfg.getString("currency.type");
        currencyItem = Material.matchMaterial(cfg.getString("currency.item"));
        defaultExpireSeconds = cfg.getLong("defaults.expire-seconds");
        defaultPrice = cfg.getDouble("defaults.price");
        buySound = Sound.valueOf(cfg.getString("options.buy-sound"));
        blacklist = cfg.getStringList("blacklist").stream().map(Material::matchMaterial).filter(Objects::nonNull).collect(Collectors.toSet());
        messages = cfg.getConfigurationSection("messages").getKeys(false).stream()
            .collect(Collectors.toMap(k -> k, k -> cfg.getString("messages." + k)));
        countdownFormat = cfg.getString("countdown-format");
        loreTemplate = cfg.getStringList("lore");
        margin = cfg.getDouble("void-margin", 3.0);
    }

    private void reloadData() {
        dataConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(dataFile);
        islandsItems.clear();
        dataConfig.getKeys(false).forEach(islKey -> {
            UUID islId = UUID.fromString(islKey);
            Map<String, TrackedItem> map = new ConcurrentHashMap<>();
            ConfigurationSection islSec = dataConfig.getConfigurationSection(islKey);
            islSec.getKeys(false).forEach(k -> {
                String base = islKey + "." + k;
                ItemStack t = dataConfig.getItemStack(base + ".template");
                if (t != null)
                    map.put(k, new TrackedItem(k, t, dataConfig.getInt(base + ".amount"), dataConfig.getLong(base + ".expireAt")));
            });
            islandsItems.put(islId, map);
        });
    }

    private void saveData() {
        try {
            org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
            islandsItems.forEach((id, map) -> map.forEach((k, ti) -> {
                String base = id + "." + k;
                yaml.set(base + ".template", ti.template);
                yaml.set(base + ".amount", ti.amount);
                yaml.set(base + ".expireAt", ti.expireAt);
            }));
            yaml.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Erreur sauvegarde data.yml");
            e.printStackTrace();
        }
    }

    private Island getIslandWithMargin(Location loc) {
        Island isl = SuperiorSkyblockAPI.getIslandAt(loc);
        if (isl != null) return isl;
        return SuperiorSkyblockAPI.getGrid().getIslands().stream()
            .filter(i -> {
                Location c = i.getCenter(dimension(loc.getWorld().getEnvironment()));
                double r = i.getIslandSize() / 2.0;
                return loc.getX() >= c.getX() - r - margin && loc.getX() <= c.getX() + r + margin &&
                    loc.getZ() >= c.getZ() - r - margin && loc.getZ() <= c.getZ() + r + margin;
            }).findFirst().orElse(null);
    }

    @EventHandler
    public void onItemVoid(EntityRemoveFromWorldEvent e) {
        if (!(e.getEntity() instanceof Item it) || it.getLocation().getY() > -70) return;
        Island isl = getIslandWithMargin(it.getLocation());
        if (isl == null || blacklist.contains(it.getItemStack().getType())) return;
        UUID id = isl.getUniqueId();
        ItemStack dropped = it.getItemStack().clone();
        dropped.setAmount(1);
        Map<String, TrackedItem> map = islandsItems.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
        map.values().stream()
            .filter(t -> t.template.isSimilar(dropped))
            .findFirst()
            .ifPresentOrElse(t -> t.amount += it.getItemStack().getAmount(),
                () -> {
                    String key = UUID.randomUUID().toString();
                    map.put(key, new TrackedItem(key, dropped, it.getItemStack().getAmount(), Instant.now().getEpochSecond() + defaultExpireSeconds));
                });
    }

    private Void openVoidMenu(Player p, int page) {
        return openVoidMenu(p, page, true);
    }

    private Void openVoidMenu(Player p, int page, boolean refresh) {
        UUID puid = p.getUniqueId();
        playerPages.put(puid, page);
        Island isl = SuperiorSkyblockAPI.getPlayer(p).getIsland();
        if (isl == null) {
            p.sendMessage(colorize(messages.get("no-island")));
            return null;
        }
        return openIslandVoidMenu(p, isl, page, refresh);
    }

    private Void openOtherVoidMenu(Player viewer, Player target, Island targetIsland, int page) {
        return openOtherVoidMenu(viewer, target, targetIsland, page, true);
    }

    private Void openOtherVoidMenu(Player viewer, Player target, Island targetIsland, int page, boolean refresh) {
        UUID vuid = viewer.getUniqueId();
        playerPages.put(vuid, page);
        return openIslandVoidMenu(viewer, targetIsland, page, refresh);
    }

    private Void openIslandVoidMenu(Player p, Island isl, int page, boolean refresh) {
        UUID puid = p.getUniqueId();
        List<TrackedItem> list = islandsItems
            .getOrDefault(isl.getUniqueId(), Collections.emptyMap())
            .values().stream()
            .filter(ti -> ti.template.getType() != Material.AIR)
            .collect(Collectors.toList());

        int totalPages = Math.max(1, (list.size() + itemsPerPage - 1) / itemsPerPage);
        page = Math.min(Math.max(1, page), totalPages);
        String title = colorize(menuTitleTemplate.replace("%page%", String.valueOf(page)).replace("%max%", String.valueOf(totalPages)));

        Inventory inv = Bukkit.createInventory(null, 54, title);
        populateMenu(inv, list, page);
        p.openInventory(inv);
        // Après openInventory : la fermeture du menu précédent (changement de page) passe par
        // onInventoryClose, qui vide l'état du viewer et annule sa tâche de rafraîchissement.
        playerMenuTitles.put(puid, title);
        playerPages.put(puid, page);
        viewedIslands.put(puid, isl);

        if (refresh) {
            Optional.ofNullable(refreshTasks.remove(puid)).ifPresent(BukkitTask::cancel);
            refreshTasks.put(puid, new BukkitRunnable() {
                public void run() {
                    if (!p.isOnline() || p.getOpenInventory() == null || !playerMenuTitles.getOrDefault(puid, "").equals(p.getOpenInventory().getTitle())) {
                        cancel();
                        refreshTasks.remove(puid);
                    } else updateMenu(p, playerPages.getOrDefault(puid, 1), isl);
                }
            }.runTaskTimer(this, 0, 20));
        }
        return null;
    }

    private void updateMenu(Player p, int page) {
        Island isl = SuperiorSkyblockAPI.getPlayer(p).getIsland();
        if (isl == null) return;
        updateMenu(p, page, isl);
    }

    private void updateMenu(Player p, int page, Island isl) {
        List<TrackedItem> list = new ArrayList<>(islandsItems.getOrDefault(isl.getUniqueId(), Collections.emptyMap()).values());
        int totalPages = Math.max(1, (list.size() + itemsPerPage - 1) / itemsPerPage);
        page = Math.min(Math.max(1, page), totalPages);
        Inventory inv = p.getOpenInventory().getTopInventory();
        inv.clear();
        populateMenu(inv, list, page);
        p.updateInventory();
    }

    private void populateMenu(Inventory inv, List<TrackedItem> list, int page) {
        list = list.stream()
            .filter(ti -> ti.template.getType() != Material.AIR)
            .collect(Collectors.toList());

        long now = Instant.now().getEpochSecond();
        int start = (page - 1) * itemsPerPage, end = Math.min(start + itemsPerPage, list.size());

        for (int i = start; i < end; i++) {
            TrackedItem ti = list.get(i);

            try {
                ItemStack stack = ti.template.clone();
                stack.setAmount(Math.min(ti.amount, stack.getMaxStackSize()));

                ItemMeta meta = stack.getItemMeta();
                if (meta == null) {
                    meta = Bukkit.getItemFactory().getItemMeta(stack.getType());
                    if (meta == null) {
                        getLogger().warning("Aucun ItemMeta pour " + stack.getType() + " (template=" + ti.template + ")");
                        continue;
                    }
                }

                ItemMeta finalMeta = meta;
                List<String> lore = loreTemplate.stream()
                    .flatMap(line -> {
                        if ("%lore%".equals(line)) {
                            return Optional.ofNullable(finalMeta.getLore())
                                .map(List::stream)
                                .orElseGet(Stream::empty)
                                .map(this::colorize);
                        }
                        String text = line
                            .replace("%quantity%", String.valueOf(ti.amount))
                            .replace("%countdown%", formatCountdown(ti.expireAt - now))
                            .replace("%price%", String.valueOf((int) Math.ceil(getPrice(stack.getType()))));
                        return Stream.of(colorize(text));
                    })
                    .collect(Collectors.toList());

                meta.setLore(lore);
                meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, ti.id);
                stack.setItemMeta(meta);
                inv.addItem(stack);

            } catch (Exception ex) {
                getLogger().log(Level.SEVERE,
                    "Erreur lors du traitement de l'item à l'index " + i +
                        " (template=" + ti.template + ", amount=" + ti.amount + ")", ex);
            }
        }
        int totalPages = Math.max(1, (list.size() + itemsPerPage - 1) / itemsPerPage);
        if (page > 1)
            Optional.ofNullable(menuItems.get("prev-page")).ifPresent(d -> inv.setItem(d.slot, createNavItem("prev-page")));
        if (page < totalPages)
            Optional.ofNullable(menuItems.get("next-page")).ifPresent(d -> inv.setItem(d.slot, createNavItem("next-page")));
        Optional.ofNullable(menuItems.get("close")).ifPresent(d -> inv.setItem(d.slot, createNavItem("close")));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !p.getOpenInventory().getTitle().startsWith(colorize(menuTitleTemplate.split("%")[0])))
            return;
        e.setCancelled(true);
        // Un clic dans l'inventaire du joueur ne doit rien acheter
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        MenuItemDef closeDef = menuItems.get("close");
        if (closeDef != null && e.getSlot() == closeDef.slot) {
            if (closeDef.command != null && !closeDef.command.isEmpty()) {
                p.performCommand(closeDef.command);
            } else {
                p.closeInventory();
            }
            return;
        }

        Island isl = viewedIslands.getOrDefault(p.getUniqueId(), SuperiorSkyblockAPI.getPlayer(p).getIsland());
        if (isl == null) return;

        int page = playerPages.getOrDefault(p.getUniqueId(), 1);
        MenuItemDef prevDef = menuItems.get("prev-page");
        MenuItemDef nextDef = menuItems.get("next-page");
        if (prevDef != null && e.getSlot() == prevDef.slot) {
            openIslandVoidMenu(p, isl, page - 1, true);
            return;
        }
        if (nextDef != null && e.getSlot() == nextDef.slot) {
            openIslandVoidMenu(p, isl, page + 1, true);
            return;
        }

        // Identification exacte par l'id posé en PDC : deux items du même type ne se confondent plus
        ItemMeta clickedMeta = clicked.getItemMeta();
        String itemId = clickedMeta == null ? null : clickedMeta.getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);
        if (itemId == null) return;
        Map<String, TrackedItem> items = islandsItems.get(isl.getUniqueId());
        TrackedItem ti = items == null ? null : items.get(itemId);
        if (ti == null) return;
        double price = getPrice(ti.template.getType());
        if (!hasSufficientFunds(p, price)) {
            p.sendMessage(colorize(messages.get("purchase-no-funds").replace("%currency%", "money".equals(currencyType) ? "argent" : currencyItem.name())));
            return;
        }
        // Toute la quantité doit tenir dans l'inventaire : addItem() jetterait le surplus déjà payé
        int maxStack = Math.max(1, ti.template.getMaxStackSize());
        int slotsNeeded = (ti.amount + maxStack - 1) / maxStack;
        if (countFreeSlots(p) < slotsNeeded) {
            p.sendMessage(colorize(messages.get("purchase-no-space")));
            return;
        }
        // Retrait de la map avant de payer et de donner : un second clic ne trouve plus l'item
        if (!items.remove(itemId, ti)) return;
        withdrawFunds(p, price);
        int remaining = ti.amount;
        while (remaining > 0) {
            ItemStack toGive = ti.template.clone();
            int amount = Math.min(remaining, maxStack);
            toGive.setAmount(amount);
            p.getInventory().addItem(toGive);
            remaining -= amount;
        }
        p.playSound(p.getLocation(), buySound, 1f, 1f);
        p.sendMessage(colorize(messages.get("purchase-success")));
        p.closeInventory();
    }

    private int countFreeSlots(Player p) {
        int free = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it == null || it.getType() == Material.AIR) free++;
        }
        return free;
    }

    private boolean hasSufficientFunds(Player p, double price) {
        if ("money".equals(currencyType)) return economy.has(p, price);
        if ("item".equals(currencyType)) {
            int needed = (int) Math.ceil(price);
            return Arrays.stream(p.getInventory().getContents())
                .filter(Objects::nonNull)
                .filter(i -> i.getType() == currencyItem)
                .mapToInt(ItemStack::getAmount)
                .sum() >= needed;
        }
        return false;
    }

    private void withdrawFunds(Player p, double price) {
        if ("money".equals(currencyType)) economy.withdrawPlayer(p, price);
        else if ("item".equals(currencyType)) {
            int needed = (int) Math.ceil(price);
            for (int i = 0; i < p.getInventory().getSize() && needed > 0; i++) {
                ItemStack item = p.getInventory().getItem(i);
                if (item != null && item.getType() == currencyItem) {
                    int amt = item.getAmount();
                    if (amt <= needed) {
                        p.getInventory().setItem(i, null);
                        needed -= amt;
                    } else {
                        item.setAmount(amt - needed);
                        needed = 0;
                    }
                }
            }
            p.updateInventory();
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        UUID puid = e.getPlayer().getUniqueId();
        Optional.ofNullable(refreshTasks.remove(puid)).ifPresent(BukkitTask::cancel);
        playerPages.remove(puid);
        playerMenuTitles.remove(puid);
        viewedIslands.remove(puid);
    }

    private String formatCountdown(long secs) {
        return secs <= 0 ? colorize("&cExpiré") : colorize(countdownFormat
            .replace("%h%", String.valueOf(secs / 3600))
            .replace("%m%", String.valueOf((secs % 3600) / 60))
            .replace("%s%", String.valueOf(secs % 60)));
    }

    private double getPrice(Material mat) {
        String path = "custom." + mat.name() + ".price";
        return getConfig().contains(path) ? getConfig().getDouble(path) : defaultPrice;
    }

    private ItemStack createNavItem(String key) {
        MenuItemDef def = menuItems.get(key);
        ItemStack it = new ItemStack(def.material);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(colorize(def.displayName));
        it.setItemMeta(m);
        return it;
    }

    private String colorize(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    private static class MenuItemDef {
        Material material;
        String displayName;
        int slot;
        String command;

        MenuItemDef(Material m, String n, int s, String c) {
            material = m;
            displayName = n;
            slot = s;
            command = c;
        }
    }

    private static class TrackedItem {
        final String id; // clé dans la map de l'île, posée en PDC sur l'icône du menu
        ItemStack template;
        int amount;
        long expireAt;

        TrackedItem(String id, ItemStack t, int a, long e) {
            this.id = id;
            template = t;
            amount = a;
            expireAt = e;
        }
    }

    private class ExpireTask extends BukkitRunnable {
        public void run() {
            long now = Instant.now().getEpochSecond();
            islandsItems.values().forEach(m -> m.values().removeIf(t -> t.expireAt <= now));
        }
    }

    // SSB2 2026 : getCenter prend une Dimension et non plus un World.Environment
    private static Dimension dimension(World.Environment env) {
        for (Dimension d : Dimension.values()) {
            if (d.getEnvironment() == env) return d;
        }
        return Dimension.getByName("NORMAL");
    }
}
