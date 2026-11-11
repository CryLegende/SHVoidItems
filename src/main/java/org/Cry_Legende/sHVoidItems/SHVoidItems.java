package org.Cry_Legende.sHVoidItems;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.Cry_Legende.sHVoidItems.give.InventoryGiver;
import org.Cry_Legende.sHVoidItems.give.ItemGiver;
import org.Cry_Legende.sHVoidItems.give.PagesGiver;
import org.Cry_Legende.sHVoidItems.network.NetworkLink;
import org.Cry_Legende.sHVoidItems.network.ShNetworkLink;
import org.Cry_Legende.sHVoidItems.stack.StackAmounts;
import org.Cry_Legende.sHVoidItems.stack.WildStackerAmounts;
import org.Cry_Legende.sHVoidItems.utils.ActionLog;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class SHVoidItems extends JavaPlugin implements Listener, TabExecutor {

    // /shvoiditems : sous-commande -> permission (shvoiditems.admin est le parent de toutes)
    private static final Map<String, String> ADMIN_SUBCOMMANDS = Map.of(
        "view", "shvoiditems.admin.view",
        "reload", "shvoiditems.admin.reload");
    private static final List<String> ADMIN_ORDER = List.of("view", "reload");
    // Ligne de 'lore' remplacée par le lore d'origine de l'item
    private static final String ORIGINAL_LORE = "%lore%";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private Economy economy;
    private final Map<UUID, Map<String, TrackedItem>> islandsItems = new ConcurrentHashMap<>();
    private File dataFile;
    private NamespacedKey itemIdKey;
    // data.yml n'est réécrit que si quelque chose a changé, et jamais avant d'avoir été relu
    private final AtomicBoolean dirty = new AtomicBoolean();
    private volatile boolean dataLoaded;

    // Réglages lus dans config.yml et messages.yml : remplacés tous ensemble par /shvoiditems reload, et lus par les
    // threads des mondes (chute dans le vide) comme par la sauvegarde asynchrone
    private volatile String menuTitle;
    private volatile int itemsPerPage;
    private volatile Map<String, MenuItemDef> menuItems;
    private volatile String currencyType;
    private volatile Material currencyItem;
    private volatile long defaultExpireSeconds;
    private volatile double defaultPrice;
    private volatile Map<String, Double> customPrices;
    private volatile Sound buySound;
    private volatile Set<Material> blacklist;
    private volatile Map<String, String> messages;
    private volatile String countdownFormat;
    private volatile List<String> loreTemplate;
    private volatile double margin;
    private volatile String purchaseLogFile;
    private volatile String purchaseLogFormat;
    // Valeur de reseau.actif au démarrage : le mode réseau ne se branche ni ne se débranche à chaud
    private boolean networkConfigured;
    // Tâches planifiées, reprogrammées par le rechargement avec les périodes de config.yml
    private BukkitTask saveTask;
    private BukkitTask purgeTask;
    private BukkitTask refreshTask;

    // Quantité réelle des piles au sol : celle de l'entité, ou celle de WildStacker quand il est là
    private StackAmounts stackAmounts = (item, visible) -> visible;
    // Remise des items rachetés : l'inventaire porté, ou toutes les pages du joueur avec SHInventoryPages
    private ItemGiver giver = new InventoryGiver();
    // Non nul seulement avec SHSkyBridge et SHNetwork (plusieurs serveurs se partagent les îles) : un serveur ne garde
    // les items tombés dans le vide que des îles qu'il héberge, et n'en retire rien pour les autres
    private NetworkLink network;
    // Joueurs envoyés ici par un autre serveur pour ouvrir le menu de leur île : joueur -> fin de validité (ms)
    private final Map<UUID, Long> pendingMenus = new ConcurrentHashMap<>();
    private volatile long arrivalKeepMs = 60_000L;

    @Override
    public void onEnable() {
        itemIdKey = new NamespacedKey(this, "void_item_id");
        saveDefaultConfig();
        if (!new File(getDataFolder(), "messages.yml").exists()) saveResource("messages.yml", false);
        FileConfiguration cfg = getConfig();
        networkConfigured = cfg.getBoolean("reseau.actif", true);
        applySettings(cfg, loadMessages());
        if (!setupEconomy()) return;
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("void")).setExecutor(this);
        Objects.requireNonNull(getCommand("shvoiditems")).setExecutor(this);
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) saveResource("data.yml", false);
        loadData();
        dataLoaded = true;
        enableStackAmounts();
        enableGiver();
        enableNetwork();
        scheduleTasks(cfg);
        getLogger().info("SHVoidItems activé.");
    }

    /** (Re)programme la sauvegarde, la purge et le rafraîchissement des menus avec les périodes de config.yml. */
    private void scheduleTasks(FileConfiguration cfg) {
        for (BukkitTask task : new BukkitTask[]{saveTask, purgeTask, refreshTask}) {
            if (task != null) task.cancel();
        }
        long saveTicks = Math.max(1, cfg.getLong("data.save-seconds", 60)) * 20L;
        long expireTicks = Math.max(1, cfg.getLong("data.expire-check-seconds", 60)) * 20L;
        long refreshTicks = Math.max(1, cfg.getLong("menu.refresh-ticks", 20));
        saveTask = getServer().getScheduler().runTaskTimerAsynchronously(this, this::saveData, saveTicks, saveTicks);
        purgeTask = getServer().getScheduler().runTaskTimerAsynchronously(this, this::purgeExpired, 0, expireTicks);
        refreshTask = getServer().getScheduler().runTaskTimer(this, this::refreshMenus, refreshTicks, refreshTicks);
    }

    @Override
    public void onDisable() {
        saveData();
        // Une fois les écouteurs retirés, un menu resté ouvert ne serait plus protégé
        for (Player viewer : getServer().getOnlinePlayers()) {
            if (menuOf(viewer) != null) viewer.closeInventory();
        }
    }

    /** Lit les quantités réelles des piles avec WildStacker, s'il est activé : sa classe n'est chargée qu'ici. */
    private void enableStackAmounts() {
        if (!getServer().getPluginManager().isPluginEnabled("WildStacker")) return;
        try {
            stackAmounts = WildStackerAmounts.open();
            getLogger().info("WildStacker détecté : la quantité réelle des piles d'items est lue avec son API.");
        } catch (Exception | LinkageError e) {
            getLogger().log(Level.SEVERE, "WildStacker présent mais inutilisable, quantité des entités utilisée : " + e);
        }
    }

    /** Range les items rachetés dans toutes les pages du joueur avec SHInventoryPages, s'il est activé. */
    private void enableGiver() {
        if (!getServer().getPluginManager().isPluginEnabled("SHInventoryPages")) return;
        try {
            giver = PagesGiver.open();
            getLogger().info("SHInventoryPages détecté : les items rachetés sont rangés dans toutes les pages du joueur.");
        } catch (Exception | LinkageError e) {
            getLogger().log(Level.SEVERE, "SHInventoryPages présent mais inutilisable, inventaire porté seul utilisé : " + e);
        }
    }

    /**
     * Passe en mode réseau si SHSkyBridge et SHNetwork sont installés et 'reseau.actif' est vrai. Les classes qui touchent
     * à eux ne sont chargées qu'ici : sans eux, le plugin se comporte exactement comme avant.
     */
    private void enableNetwork() {
        if (!getConfig().getBoolean("reseau.actif", true)) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("SHNetwork") || !Bukkit.getPluginManager().isPluginEnabled("SHSkyBridge")) return;
        try {
            NetworkLink link = ShNetworkLink.open();
            arrivalKeepMs = Math.max(5, getConfig().getInt("reseau.arrivee-secondes", 60)) * 1000L;
            link.listenArrivals((player, island) -> {
                if (link.isHostedHere(island)) pendingMenus.put(player, System.currentTimeMillis() + arrivalKeepMs);
            });
            network = link;
            getLogger().info("SHSkyBridge détecté : les items tombés dans le vide ne concernent que les îles hébergées par ce serveur.");
        } catch (Exception | LinkageError e) {
            getLogger().log(Level.SEVERE, "SHSkyBridge ou SHNetwork présent mais inutilisable, fonctionnement d'un serveur seul : " + e);
        }
    }

    /** Vrai si l'île est hébergée par ce serveur (toujours vrai sans mode réseau). */
    private boolean isHosted(UUID island) {
        return network == null || network.isHostedHere(island);
    }

    /**
     * Île de ce joueur hébergée par un autre serveur : il y est envoyé, et son menu s'ouvre à l'arrivée.
     * Vrai si le joueur est parti (ou va partir), faux si le menu peut s'ouvrir ici.
     */
    private boolean forwardToHost(Player player) {
        if (network == null) return false;
        Island island = SuperiorSkyblockAPI.getPlayer(player).getIsland();
        if (island == null || network.isHostedHere(island.getUniqueId())) return false;
        send(player, "network-forward", "{serveur}", network.hostOf(island.getUniqueId()));
        network.sendToHost(player, island.getUniqueId());
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Long until = pendingMenus.remove(e.getPlayer().getUniqueId());
        if (until == null || until < System.currentTimeMillis()) return;
        Player player = e.getPlayer();
        // Un instant après l'arrivée : le joueur doit être chargé (inventaire synchronisé) avant d'ouvrir le menu
        getServer().getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) openOwnMenu(player);
        }, 20L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("shvoiditems")) {
            adminCommand(sender, args);
            return true;
        }
        if (!(sender instanceof Player player)) {
            send(sender, "player-only");
            return true;
        }
        if (args.length > 1) return false;

        try {
            if (args.length == 0) {
                if (!forwardToHost(player)) openOwnMenu(player);
            } else {
                // Ancienne forme /void <joueur> : même effet que /shvoiditems view <joueur>
                openMenuOf(player, args[0]);
            }
        } catch (Exception e) {
            send(player, "internal-error");
            getLogger().log(Level.SEVERE, "Erreur lors de l'exécution de /" + label + " avec l'argument " + Arrays.toString(args), e);
        }
        return true;
    }

    /** /shvoiditems view|reload : chaque sous-commande a sa permission, la console peut recharger. */
    private void adminCommand(CommandSender sender, String[] args) {
        List<String> allowed = allowedSubcommands(sender);
        if (allowed.isEmpty()) {
            send(sender, "no-permission");
            return;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (!allowed.contains(sub)) {
            send(sender, "usage", "{subcommands}", String.join("|", allowed));
            return;
        }
        switch (sub) {
            case "view" -> {
                if (!(sender instanceof Player viewer)) {
                    send(sender, "player-only");
                } else if (args.length != 2) {
                    send(sender, "usage", "{subcommands}", String.join("|", allowed));
                } else {
                    try {
                        openMenuOf(viewer, args[1]);
                    } catch (Exception e) {
                        send(viewer, "internal-error");
                        getLogger().log(Level.SEVERE, "Erreur lors de /shvoiditems view " + args[1], e);
                    }
                }
            }
            case "reload" -> reload(sender);
            default -> send(sender, "usage", "{subcommands}", String.join("|", allowed));
        }
    }

    /** Sous-commandes de /shvoiditems que l'expéditeur a le droit d'utiliser. */
    private List<String> allowedSubcommands(CommandSender sender) {
        return ADMIN_ORDER.stream().filter(sub -> sender.hasPermission(ADMIN_SUBCOMMANDS.get(sub))).toList();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String start = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        if (command.getName().equalsIgnoreCase("shvoiditems")) {
            List<String> allowed = allowedSubcommands(sender);
            if (args.length == 1) return allowed.stream().filter(sub -> sub.startsWith(start)).toList();
            if (args.length != 2 || !allowed.contains("view") || !args[0].equalsIgnoreCase("view")) return List.of();
            return visiblePlayers(sender, start);
        }
        if (args.length != 1 || !sender.hasPermission(ADMIN_SUBCOMMANDS.get("view"))) return List.of();
        return visiblePlayers(sender, start);
    }

    /** Noms des joueurs en ligne que l'expéditeur peut voir, commençant par {@code start}. */
    private List<String> visiblePlayers(CommandSender sender, String start) {
        return getServer().getOnlinePlayers().stream()
            .filter(target -> !(sender instanceof Player viewer) || viewer.canSee(target))
            .map(Player::getName)
            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(start))
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
    }

    /**
     * /shvoiditems reload : relit config.yml et messages.yml. Les deux fichiers sont lus avant que quoi que ce soit change :
     * une erreur YAML garde la configuration en place. data.yml n'est jamais relu.
     */
    private void reload(CommandSender sender) {
        File configFile = new File(getDataFolder(), "config.yml");
        File messagesFile = new File(getDataFolder(), "messages.yml");
        YamlConfiguration config;
        YamlConfiguration messageFile;
        try {
            config = parseConfig(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            send(sender, "reload-error", "{file}", configFile.getName(), "{error}", shortError(e));
            return;
        }
        try {
            messageFile = parse(messagesFile);
        } catch (IOException | InvalidConfigurationException e) {
            send(sender, "reload-error", "{file}", messagesFile.getName(), "{error}", shortError(e));
            return;
        }
        reloadConfig();
        applySettings(config, readMessages(messageFile));
        scheduleTasks(config);
        // Le titre et les boutons d'un menu ouvert viennent de la configuration : on le rouvre avec la nouvelle
        for (Player viewer : getServer().getOnlinePlayers()) {
            VoidMenu menu = menuOf(viewer);
            if (menu != null) openMenu(viewer, menu.island(), menu.page());
        }
        send(sender, "reload-done");
        if (config.getBoolean("reseau.actif", true) != networkConfigured) send(sender, "reload-restart");
        getLogger().info("Configuration rechargée par " + sender.getName());
    }

    /** Lit un fichier YAML sans rien avaler : une erreur de syntaxe est relancée. */
    private static YamlConfiguration parse(File file) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        return yaml;
    }

    /** config.yml lu strictement, avec les valeurs du jar pour toute clé absente (comme getConfig()). */
    private YamlConfiguration parseConfig(File file) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = parse(file);
        try (Reader defaults = new InputStreamReader(Objects.requireNonNull(getResource("config.yml")), StandardCharsets.UTF_8)) {
            yaml.setDefaults(YamlConfiguration.loadConfiguration(defaults));
        }
        return yaml;
    }

    /** Message d'erreur YAML sur une ligne, sans les '&' qui seraient lus comme des codes couleur. */
    private static String shortError(Exception e) {
        String text = String.valueOf(e.getMessage()).replace("&", "").replaceAll("\\s+", " ").trim();
        return text.length() > 220 ? text.substring(0, 220) + "..." : text;
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getPluginManager().getPlugin("Vault") == null
            ? null : getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null || (economy = rsp.getProvider()) == null) {
            getLogger().severe("Vault ou provider introuvable !");
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
        return true;
    }

    /**
     * Lit les réglages de config.yml et les textes, puis les publie d'un bloc : les threads des mondes et la sauvegarde
     * asynchrone lisent ces champs pendant un rechargement.
     */
    private void applySettings(FileConfiguration cfg, Map<String, String> loadedMessages) {
        String title = cfg.getString("menu.title", "");
        int perPage = Math.min(VoidMenu.SIZE, Math.max(1, cfg.getInt("menu.items-per-page", 45)));
        Map<String, MenuItemDef> items = loadMenuItems(cfg.getConfigurationSection("menu.items"));
        String currency = cfg.getString("currency.type");
        Material currencyMaterial = Material.matchMaterial(cfg.getString("currency.item", ""));
        if ("item".equals(currency) && currencyMaterial == null) getLogger().warning("currency.item invalide : aucun achat ne sera possible.");
        Map<String, Double> prices = new HashMap<>();
        ConfigurationSection custom = cfg.getConfigurationSection("custom");
        if (custom != null) {
            for (String k : custom.getKeys(false)) {
                if (custom.contains(k + ".price")) prices.put(k, custom.getDouble(k + ".price"));
            }
        }
        Sound sound = sound(cfg.getString("options.buy-sound"));
        if (sound == null) getLogger().warning("options.buy-sound invalide : aucun son ne sera joué à l'achat.");

        menuTitle = title;
        itemsPerPage = perPage;
        menuItems = items;
        currencyType = currency;
        currencyItem = currencyMaterial;
        defaultExpireSeconds = cfg.getLong("defaults.expire-seconds", 86400L);
        defaultPrice = cfg.getDouble("defaults.price", 1D);
        customPrices = prices;
        buySound = sound;
        blacklist = cfg.getStringList("blacklist").stream().map(Material::matchMaterial).filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
        messages = loadedMessages;
        countdownFormat = cfg.getString("countdown-format", "");
        loreTemplate = List.copyOf(cfg.getStringList("lore"));
        margin = cfg.getDouble("void-margin", 3D);
        purchaseLogFile = cfg.getString("logs.purchase.file", "rachats");
        purchaseLogFormat = cfg.getString("logs.purchase.format", "%player% -> rachète %amount% %item% (%price% éclats d'écho)");
    }

    private Map<String, MenuItemDef> loadMenuItems(ConfigurationSection section) {
        Map<String, MenuItemDef> defs = new HashMap<>();
        if (section == null) return defs;
        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null) continue;
            Material material = Material.matchMaterial(s.getString("material", ""));
            int slot = s.getInt("slot");
            if (material == null || material.isAir() || slot < 0 || slot >= VoidMenu.SIZE) {
                getLogger().warning("menu.items." + key + " ignoré : matériau ou slot invalide.");
                continue;
            }
            ItemStack icon = new ItemStack(material);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(itemText(s.getString("display-name", "")));
            icon.setItemMeta(meta);
            defs.put(key, new MenuItemDef(icon, slot, s.getString("command")));
        }
        return defs;
    }

    private Map<String, String> loadMessages() {
        return readMessages(YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml")));
    }

    /** Textes de messages.yml, avec la valeur du jar pour ceux qu'il n'a pas. */
    private Map<String, String> readMessages(YamlConfiguration file) {
        Map<String, String> loaded = new HashMap<>();
        for (String key : file.getKeys(false)) {
            String text = file.getString(key);
            if (text != null) loaded.put(key, text);
        }
        // Les textes absents d'un messages.yml plus ancien prennent la valeur du jar
        try (Reader defaults = new InputStreamReader(Objects.requireNonNull(getResource("messages.yml")), StandardCharsets.UTF_8)) {
            YamlConfiguration jarMessages = YamlConfiguration.loadConfiguration(defaults);
            for (String key : jarMessages.getKeys(false)) {
                String text = jarMessages.getString(key);
                if (text != null) loaded.putIfAbsent(key, text);
            }
        } catch (IOException | RuntimeException e) {
            getLogger().log(Level.WARNING, "messages.yml de référence illisible : " + e.getMessage());
        }
        return loaded;
    }

    private void loadData() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        islandsItems.clear();
        for (String islandKey : yaml.getKeys(false)) {
            UUID island = UUID.fromString(islandKey);
            Map<String, TrackedItem> map = new ConcurrentHashMap<>();
            ConfigurationSection islandSection = yaml.getConfigurationSection(islandKey);
            if (islandSection != null) {
                for (String key : islandSection.getKeys(false)) {
                    String base = islandKey + "." + key;
                    ItemStack template = yaml.getItemStack(base + ".template");
                    if (template != null) {
                        map.put(key, new TrackedItem(key, template, yaml.getInt(base + ".amount"), yaml.getLong(base + ".expireAt")));
                    }
                }
            }
            islandsItems.put(island, map);
        }
    }

    private synchronized void saveData() {
        // Rien n'a changé depuis la dernière écriture, ou data.yml n'a pas encore été relu : on n'y touche pas
        if (!dataLoaded || !dirty.getAndSet(false)) return;
        File tmp = new File(dataFile.getParentFile(), dataFile.getName() + ".tmp");
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            islandsItems.forEach((id, map) -> map.forEach((k, item) -> {
                String base = id + "." + k;
                yaml.set(base + ".template", item.template());
                yaml.set(base + ".amount", item.amount());
                yaml.set(base + ".expireAt", item.expireAt());
            }));
            // Écriture dans un fichier temporaire puis remplacement : un arrêt en plein milieu ne tronque pas data.yml
            yaml.save(tmp);
            Files.move(tmp.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            dirty.set(true);
            getLogger().log(Level.SEVERE, "Erreur sauvegarde data.yml", e);
        }
    }

    /** Retire les items expirés des îles hébergées ici, et les annonces d'arrivée qui n'ont plus de raison d'être. */
    private void purgeExpired() {
        long now = System.currentTimeMillis() / 1000L;
        // Les entrées d'une île hébergée ailleurs sont gardées telles quelles : c'est son hôte qui les fait expirer
        islandsItems.forEach((id, map) -> {
            if (isHosted(id) && map.values().removeIf(item -> item.expireAt() <= now)) dirty.set(true);
        });
        pendingMenus.values().removeIf(until -> until < System.currentTimeMillis());
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Items tombés dans le vide
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Île à laquelle appartient un point, marge comprise : celle qui contient le point, sinon celle qui contient l'un des
     * quatre points décalés de la marge en X et en Z (les îles sont assez éloignées pour qu'il n'y en ait qu'une).
     */
    private Island islandAround(Location loc) {
        Island island = SuperiorSkyblockAPI.getIslandAt(loc);
        if (island != null || margin <= 0) return island;
        World world = loc.getWorld();
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                island = SuperiorSkyblockAPI.getIslandAt(new Location(world, loc.getX() + dx * margin, loc.getY(), loc.getZ() + dz * margin));
                if (island != null) return island;
            }
        }
        return null;
    }

    /**
     * Un item sort du monde par le bas : seul OUT_OF_WORLD est un vrai passage dans le vide. Les fusions de piles et les
     * déchargements de chunks retirent aussi des items, et les compteraient une seconde fois.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemVoid(EntityRemoveEvent e) {
        if (e.getCause() != EntityRemoveEvent.Cause.OUT_OF_WORLD || !(e.getEntity() instanceof Item item)) return;
        ItemStack stack = item.getItemStack();
        if (blacklist.contains(stack.getType())) return;
        Island island = islandAround(item.getLocation());
        if (island == null) return;
        UUID id = island.getUniqueId();
        // Chunk d'une île hébergée ailleurs, chargé ici par hasard : ses items sont ceux de son hôte
        if (!isHosted(id)) return;
        track(id, stack.asOne(), stackAmounts.of(item, stack.getAmount()));
    }

    /** Ajoute {@code quantity} exemplaires de {@code template} aux items tombés dans le vide de l'île. */
    private void track(UUID island, ItemStack template, int quantity) {
        Map<String, TrackedItem> map = islandsItems.computeIfAbsent(island, k -> new ConcurrentHashMap<>());
        long now = System.currentTimeMillis() / 1000L;
        // Les mondes tiquent en parallèle : la recherche puis le cumul (ou la création) se font sous le verrou de la map de l'île
        synchronized (map) {
            for (Map.Entry<String, TrackedItem> entry : map.entrySet()) {
                TrackedItem existing = entry.getValue();
                if (existing.expireAt() <= now || !existing.template().isSimilar(template)) continue;
                // Le cumul remplace l'entrée seulement si elle y est encore : un item racheté entre-temps n'est jamais remis
                if (map.computeIfPresent(entry.getKey(), (key, current) -> current.plus(quantity)) != null) {
                    dirty.set(true);
                    return;
                }
            }
            String key = UUID.randomUUID().toString();
            map.put(key, new TrackedItem(key, template, quantity, now + defaultExpireSeconds));
        }
        dirty.set(true);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Menu
    // ---------------------------------------------------------------------------------------------------------------

    private void openOwnMenu(Player player) {
        Island island = SuperiorSkyblockAPI.getPlayer(player).getIsland();
        if (island == null) {
            send(player, "no-island");
            return;
        }
        openMenu(player, island.getUniqueId(), 1);
    }

    /** /void &lt;joueur&gt; (et /shvoiditems view) : le menu de l'île d'un autre joueur, réservé aux admins. */
    private void openMenuOf(Player viewer, String targetName) {
        if (!viewer.hasPermission(ADMIN_SUBCOMMANDS.get("view"))) {
            send(viewer, "no-permission");
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            send(viewer, "player-not-found");
            return;
        }
        Island island = SuperiorSkyblockAPI.getPlayer(target).getIsland();
        if (island == null) {
            send(viewer, "target-no-island");
            return;
        }
        if (!isHosted(island.getUniqueId())) {
            send(viewer, "network-other-server", "{serveur}", network.hostOf(island.getUniqueId()));
            return;
        }
        openMenu(viewer, island.getUniqueId(), 1);
    }

    /** Ouvre le menu de l'île à la page demandée (ramenée entre la première et la dernière). */
    private void openMenu(Player viewer, UUID island, int requestedPage) {
        List<TrackedItem> entries = entriesOf(island);
        int pages = pageCount(entries.size());
        int page = Math.min(Math.max(1, requestedPage), pages);
        VoidMenu menu = new VoidMenu(island, page, pages, LEGACY.deserialize(
            menuTitle.replace("%page%", String.valueOf(page)).replace("%max%", String.valueOf(pages))));
        placeButtons(menu);
        fill(menu, entries);
        viewer.openInventory(menu.getInventory());
    }

    private int pageCount(int entries) {
        return Math.max(1, (entries + itemsPerPage - 1) / itemsPerPage);
    }

    /** Items de l'île, les plus proches de l'expiration d'abord. */
    private List<TrackedItem> entriesOf(UUID island) {
        Map<String, TrackedItem> map = islandsItems.get(island);
        if (map == null) return List.of();
        return map.values().stream()
            .filter(item -> !item.template().getType().isAir())
            .sorted(Comparator.comparingLong(TrackedItem::expireAt).thenComparing(TrackedItem::id))
            .toList();
    }

    /** Boutons de navigation et de retour : fixes pour la vie du menu, puisque sa page et son nombre de pages le sont. */
    private void placeButtons(VoidMenu menu) {
        Inventory inv = menu.getInventory();
        MenuItemDef prev = menuItems.get("prev-page");
        MenuItemDef next = menuItems.get("next-page");
        MenuItemDef close = menuItems.get("close");
        if (prev != null && menu.page() > 1) inv.setItem(prev.slot(), prev.icon());
        if (next != null && menu.page() < menu.pages()) inv.setItem(next.slot(), next.icon());
        if (close != null) inv.setItem(close.slot(), close.icon());
    }

    /** Écrit les items de la page dans leurs emplacements, et vide ceux qui ne servent plus. */
    private void fill(VoidMenu menu, List<TrackedItem> entries) {
        Inventory inv = menu.getInventory();
        long now = System.currentTimeMillis() / 1000L;
        int first = (menu.page() - 1) * itemsPerPage;
        for (int slot = 0; slot < itemsPerPage; slot++) {
            int index = first + slot;
            inv.setItem(slot, index < entries.size() ? icon(entries.get(index), now) : null);
        }
    }

    private ItemStack icon(TrackedItem entry, long now) {
        try {
            ItemStack stack = entry.template().clone();
            stack.setAmount(Math.min(entry.amount(), stack.getMaxStackSize()));
            ItemMeta meta = stack.getItemMeta();
            List<Component> lore = new ArrayList<>();
            for (String line : loreTemplate) {
                if (ORIGINAL_LORE.equals(line)) {
                    List<Component> original = meta.lore();
                    if (original != null) lore.addAll(original);
                } else {
                    lore.add(itemText(line
                        .replace("%quantity%", String.valueOf(entry.amount()))
                        .replace("%countdown%", countdown(entry.expireAt() - now))
                        .replace("%price%", String.valueOf((int) Math.ceil(getPrice(stack.getType()))))));
                }
            }
            meta.lore(lore);
            meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, entry.id());
            stack.setItemMeta(meta);
            return stack;
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Erreur lors du traitement de l'item " + entry.id()
                + " (template=" + entry.template() + ", amount=" + entry.amount() + ")", ex);
            return null;
        }
    }

    /** Menu ouvert par ce joueur, ou null s'il regarde autre chose. */
    private VoidMenu menuOf(Player viewer) {
        // false : pas de copie de l'état du bloc quand le joueur a un coffre ouvert
        return viewer.getOpenInventory().getTopInventory().getHolder(false) instanceof VoidMenu menu ? menu : null;
    }

    /** Une seule tâche pour tous les menus ouverts : compte à rebours et quantités à jour, sans état par joueur. */
    private void refreshMenus() {
        for (Player viewer : getServer().getOnlinePlayers()) {
            VoidMenu menu = menuOf(viewer);
            if (menu == null) continue;
            List<TrackedItem> entries = entriesOf(menu.island());
            // Le nombre de pages a changé : le titre est périmé, et la page n'existe peut-être plus
            if (pageCount(entries.size()) != menu.pages()) openMenu(viewer, menu.island(), menu.page());
            else fill(menu, entries);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof VoidMenu menu)) return;
        e.setCancelled(true);
        // Un clic dans l'inventaire du joueur ne doit rien acheter
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.isEmpty()) return;

        int slot = e.getSlot();
        MenuItemDef close = menuItems.get("close");
        if (close != null && slot == close.slot()) {
            if (close.command() != null && !close.command().isEmpty()) p.performCommand(close.command());
            else p.closeInventory();
            return;
        }
        MenuItemDef prev = menuItems.get("prev-page");
        MenuItemDef next = menuItems.get("next-page");
        if (prev != null && slot == prev.slot()) {
            openMenu(p, menu.island(), menu.page() - 1);
            return;
        }
        if (next != null && slot == next.slot()) {
            openMenu(p, menu.island(), menu.page() + 1);
            return;
        }

        // Identification exacte par l'id posé en PDC : deux items du même type ne se confondent pas
        String itemId = clicked.getItemMeta().getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);
        if (itemId != null) buy(p, menu.island(), itemId);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        // Un item glissé dans le menu serait perdu à la fermeture
        if (e.getView().getTopInventory().getHolder(false) instanceof VoidMenu) e.setCancelled(true);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Rachat
    // ---------------------------------------------------------------------------------------------------------------

    private void buy(Player p, UUID island, String itemId) {
        Map<String, TrackedItem> items = islandsItems.get(island);
        TrackedItem item = items == null ? null : items.get(itemId);
        if (item == null) return;
        double price = getPrice(item.template().getType());
        if (!hasSufficientFunds(p, price)) {
            send(p, "purchase-no-funds", "%currency%", currencyName());
            return;
        }
        // Toute la quantité doit tenir chez le joueur : ce qui n'y tient pas serait déjà payé
        if (!giver.fits(p, item.template(), item.amount())) {
            send(p, "purchase-no-space");
            return;
        }
        // Retrait de la map avant de payer et de donner : un second clic ne trouve plus l'item, et un cumul arrivé
        // depuis la lecture (l'entrée a changé) annule ce rachat plutôt que de donner autre chose que ce qui a été vu
        if (!items.remove(itemId, item)) return;
        dirty.set(true);
        withdrawFunds(p, price);
        giver.give(p, item.template(), item.amount());
        ActionLog.write(this, purchaseLogFile, purchaseLogFormat
            .replace("%player%", p.getName())
            .replace("%amount%", String.valueOf(item.amount()))
            .replace("%item%", itemName(item.template()))
            .replace("%price%", String.valueOf((int) Math.ceil(price))));
        if (buySound != null) p.playSound(p.getLocation(), buySound, 1f, 1f);
        send(p, "purchase-success");
        p.closeInventory();
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

    private double getPrice(Material mat) {
        return customPrices.getOrDefault(mat.name(), defaultPrice);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Textes
    // ---------------------------------------------------------------------------------------------------------------

    /** Texte du compte à rebours d'un item, en codes '&amp;' (le temps restant, ou « expiré »). */
    private String countdown(long secs) {
        if (secs <= 0) return message("expired");
        return countdownFormat
            .replace("%h%", String.valueOf(secs / 3600))
            .replace("%m%", String.valueOf((secs % 3600) / 60))
            .replace("%s%", String.valueOf(secs % 60));
    }

    private String message(String key) {
        return messages.getOrDefault(key, key);
    }

    /** Envoie le message {@code key} ; {@code replacements} alterne texte cherché et texte mis à sa place. */
    private void send(CommandSender to, String key, String... replacements) {
        String text = message(key);
        for (int i = 0; i + 1 < replacements.length; i += 2) text = text.replace(replacements[i], replacements[i + 1]);
        to.sendMessage(LEGACY.deserialize(text));
    }

    /** Texte d'un item (nom, lore) : pas d'italique, sauf là où le texte le demande avec '&amp;o'. */
    private static Component itemText(String legacy) {
        return LEGACY.deserialize(legacy).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Monnaie du menu, pour le placeholder %currency% de purchase-no-funds. */
    private String currencyName() {
        return "money".equals(currencyType) ? message("currency-money") : currencyItem == null ? "" : prettyName(currencyItem);
    }

    /** Nom personnalisé de l'objet, sinon son type écrit lisiblement (Diamond Sword). */
    private static String itemName(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) return PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(meta.displayName()));
        return prettyName(stack.getType());
    }

    private static String prettyName(Material material) {
        StringBuilder name = new StringBuilder();
        for (String word : material.getKey().getKey().split("_")) {
            if (!name.isEmpty()) name.append(' ');
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.toString();
    }

    /** Son lu en config : ancien nom (ENTITY_PLAYER_LEVELUP) ou clé (minecraft:entity.player.levelup). */
    private static Sound sound(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        if (s.indexOf(':') >= 0 || s.indexOf('.') >= 0) {
            NamespacedKey key = NamespacedKey.fromString(s.toLowerCase(Locale.ROOT));
            return key == null ? null : Registry.SOUND_EVENT.get(key);
        }
        try {
            return (Sound) Sound.class.getField(s.toUpperCase(Locale.ROOT)).get(null);
        } catch (ReflectiveOperationException | ClassCastException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Données
    // ---------------------------------------------------------------------------------------------------------------

    /** Bouton du menu : son icône toute faite, son emplacement et, pour le retour, la commande lancée au clic. */
    private record MenuItemDef(ItemStack icon, int slot, String command) {
    }

    /**
     * Item tombé dans le vide, regroupé par île. Immuable : un cumul remplace l'entrée par une copie de plus grande quantité,
     * ce qui laisse la sauvegarde asynchrone et les mondes tiqués en parallèle lire une entrée toujours cohérente.
     *
     * @param id       clé dans la map de l'île, posée en PDC sur l'icône du menu
     * @param template un exemplaire de l'item
     * @param amount   quantité totale
     * @param expireAt fin de validité, en secondes depuis l'epoch
     */
    private record TrackedItem(String id, ItemStack template, int amount, long expireAt) {

        TrackedItem plus(int quantity) {
            return new TrackedItem(id, template, (int) Math.min(Integer.MAX_VALUE, (long) amount + quantity), expireAt);
        }
    }
}
