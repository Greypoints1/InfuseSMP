package me.hastesmp;

import org.bukkit.*;
import org.bukkit.block.Crafter;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class InfuseSmpRNG extends JavaPlugin implements Listener {
    private NamespacedKey effectKey, levelKey, upgraderKey, recipeKey;
    private enum Effect {
        RESISTANCE("Resistance", PotionEffectType.RESISTANCE, 2),
        HASTE("Haste", PotionEffectType.HASTE, 3),
        STRENGTH("Strength", PotionEffectType.STRENGTH, 3),
        REGENERATION("Regeneration", PotionEffectType.REGENERATION, 3),
        SPEED("Speed", PotionEffectType.SPEED, 3),
        INVISIBILITY("Invisibility", PotionEffectType.INVISIBILITY, 2);
        final String label; final PotionEffectType type; final int max;
        Effect(String label, PotionEffectType type, int max) {
            this.label = label; this.type = type; this.max = max;
        }
    }
    @Override public void onEnable() {
        effectKey = new NamespacedKey(this, "assigned_effect");
        levelKey = new NamespacedKey(this, "effect_level");
        upgraderKey = new NamespacedKey(this, "effect_upgrader");
        recipeKey = new NamespacedKey(this, "effect_upgrader_recipe");
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.removeRecipe(recipeKey);
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, createUpgrader());
        recipe.shape("IGI", "DSD", "IGI");
        recipe.setIngredient('I', Material.IRON_BLOCK);
        recipe.setIngredient('G', Material.GOLD_BLOCK);
        recipe.setIngredient('D', Material.DIAMOND_BLOCK);
        recipe.setIngredient('S', Material.NETHER_STAR);
        Bukkit.addRecipe(recipe);
        for (Player p : Bukkit.getOnlinePlayers()) setup(p);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) apply(p);
        }, 1L, 1L);
    }
    @Override public void onDisable() {
        if (recipeKey != null) Bukkit.removeRecipe(recipeKey);
        for (Player p : Bukkit.getOnlinePlayers()) {
            Effect e = effect(p);
            if (e != null) p.removePotionEffect(e.type);
            p.saveData();
        }
    }
    private Effect effect(Player p) {
        String s = p.getPersistentDataContainer().get(effectKey, PersistentDataType.STRING);
        if (s == null) return null;
        try { return Effect.valueOf(s); } catch (IllegalArgumentException ex) { return null; }
    }
    private int level(Player p) {
        Effect e = effect(p);
        return e == null ? 1 : Math.max(1, Math.min(e.max,
            p.getPersistentDataContainer().getOrDefault(levelKey, PersistentDataType.INTEGER, 1)));
    }
    private void setLevel(Player p, int n) {
        p.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, n);
    }
    private void assign(Player p, Effect e, int n) {
        Effect old = effect(p);
        if (old != null) p.removePotionEffect(old.type);
        p.getPersistentDataContainer().set(effectKey, PersistentDataType.STRING, e.name());
        setLevel(p, n);
        apply(p);
        p.saveData();
    }
    private void initialise(Player p) {
        if (effect(p) != null) return;
        Effect[] all = Effect.values();
        Effect e = all[ThreadLocalRandom.current().nextInt(all.length)];
        assign(p, e, 1);
        p.sendMessage(ChatColor.GOLD + "Your permanent effect is: " + e.label + " I");
    }
    private void setup(Player p) {
        initialise(p); apply(p); p.discoverRecipe(recipeKey);
    }
    private void apply(Player p) {
        Effect e = effect(p);
        if (e == null || p.isDead()) return;
        int n = level(p);
        boolean particles = e != Effect.INVISIBILITY || n == 1;
        PotionEffect current = p.getPotionEffect(e.type);
        if (current != null && current.isInfinite() && current.getAmplifier() == n-1
                && current.hasParticles() == particles) return;
        p.removePotionEffect(e.type);
        p.addPotionEffect(new PotionEffect(e.type, PotionEffect.INFINITE_DURATION, n-1, false, particles, true));
    }
    private String roman(int n) { return switch(n) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; default -> ""+n; }; }
    @EventHandler public void onJoin(PlayerJoinEvent event) { setup(event.getPlayer()); }
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity(); Effect e = effect(p); int n = level(p);
        if (e == null || n <= 1) return;
        setLevel(p, n-1);
        p.sendMessage(ChatColor.RED + "Your effect has downgraded to " + e.label + " " + roman(n-1));
        Bukkit.getScheduler().runTask(this, () -> { if (p.isOnline()) p.saveData(); });
    }
    @EventHandler public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTask(this, () -> { if (p.isOnline()) { setup(p); p.saveData(); } });
    }
    private ItemStack createUpgrader() {
        ItemStack item = new ItemStack(Material.ECHO_SHARD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RESET + "Effect Upgrader");
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(upgraderKey, PersistentDataType.BYTE, (byte)1);
        item.setItemMeta(meta); return item;
    }
    private boolean isUpgrader(ItemStack item) {
        if (item == null || item.getType() != Material.ECHO_SHARD || !item.hasItemMeta()) return false;
        Byte b = item.getItemMeta().getPersistentDataContainer().get(upgraderKey, PersistentDataType.BYTE);
        return b != null && b == 1;
    }
    private boolean containsUpgrader(ItemStack[] items) {
        for (ItemStack item : items) if (isUpgrader(item)) return true;
        return false;
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void onCraftPreview(PrepareItemCraftEvent event) {
        if (containsUpgrader(event.getInventory().getMatrix())) event.getInventory().setResult(null);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onCraft(CraftItemEvent event) {
        if (containsUpgrader(event.getInventory().getMatrix())) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onCrafter(CrafterCraftEvent event) {
        if (event.getBlock().getState() instanceof Crafter c && containsUpgrader(c.getInventory().getContents())) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void onUpgrade(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = event.getPlayer(); ItemStack item = p.getInventory().getItemInMainHand();
        if (!isUpgrader(item)) return;
        event.setCancelled(true);
        if (p.isDead()) return;
        initialise(p); Effect e = effect(p); int n = level(p);
        if (n >= e.max) { p.sendMessage(ChatColor.RED + "Your effect is already at maximum level!"); return; }
        if (item.getAmount() <= 1) p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        else { item.setAmount(item.getAmount()-1); p.getInventory().setItemInMainHand(item); }
        setLevel(p,n+1); apply(p); p.saveData();
        p.sendMessage(ChatColor.GREEN + "Your effect has upgraded to " + e.label + " " + roman(n+1));
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("effectadmin")) return false;
        if (!sender.hasPermission("hastesmp.admin")) { sender.sendMessage("No permission."); return true; }
        if (args.length < 2) return false;
        Player p = Bukkit.getPlayerExact(args[1]);
        if (p == null) { sender.sendMessage("Player is not online."); return true; }
        initialise(p);
        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "info" -> { if (args.length != 2) return false; sender.sendMessage(p.getName()+": "+effect(p).label+" "+roman(level(p))); }
                case "give" -> {
                    if (args.length != 3) return false;
                    int n = Integer.parseInt(args[2]);
                    if (n < 1 || n > 64) { sender.sendMessage("Amount must be 1-64."); return true; }
                    ItemStack item = createUpgrader(); item.setAmount(n);
                    for (ItemStack left : p.getInventory().addItem(item).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
                    p.saveData(); sender.sendMessage("Gave "+n+" Effect Upgrader(s) to "+p.getName());
                }
                case "set" -> {
                    if (args.length != 4) return false;
                    String name = args[2].toUpperCase(Locale.ROOT);
                    Effect e = Effect.valueOf(name.equals("REGEN") ? "REGENERATION" : name);
                    int n = Integer.parseInt(args[3]);
                    if (n < 1 || n > e.max) { sender.sendMessage("Valid levels: 1-"+e.max); return true; }
                    assign(p,e,n); sender.sendMessage("Set "+p.getName()+" to "+e.label+" "+roman(n));
                }
                case "reset" -> {
                    if (args.length != 2) return false;
                    Effect[] all = Effect.values(); assign(p,all[ThreadLocalRandom.current().nextInt(all.length)],1);
                    p.sendMessage("Your permanent effect is now: "+effect(p).label+" I"); sender.sendMessage("Reset effect for "+p.getName());
                }
                default -> { return false; }
            }
        } catch (IllegalArgumentException ex) { sender.sendMessage("Invalid effect, level or amount."); }
        return true;
    }
}
