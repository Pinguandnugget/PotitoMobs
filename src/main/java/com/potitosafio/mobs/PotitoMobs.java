package com.potitosafio.mobs;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

public final class PotitoMobs extends JavaPlugin implements Listener {
    private NamespacedKey baseHealthKey, baseSpeedKey;
    private Attribute maxHealthAttribute, speedAttribute;
    private int damageMultiplier, healthMultiplier, speedMultiplier;
    private BukkitTask scanner;

    @Override public void onEnable() {
        saveDefaultConfig();
        baseHealthKey = new NamespacedKey(this, "base_health");
        baseSpeedKey = new NamespacedKey(this, "base_speed");
        maxHealthAttribute = attribute("MAX_HEALTH", "GENERIC_MAX_HEALTH");
        speedAttribute = attribute("MOVEMENT_SPEED", "GENERIC_MOVEMENT_SPEED");
        readSettings();
        Bukkit.getPluginManager().registerEvents(this, this);
        startScanner();
        getLogger().info("PotitoMobs activado. Daño x"+damageMultiplier+", vida x"+healthMultiplier+", velocidad x"+speedMultiplier);
    }

    private Attribute attribute(String... names) {
        for (String name : names) {
            try { return Attribute.valueOf(name); } catch (IllegalArgumentException ignored) {}
        }
        getLogger().warning("No se encontró atributo: " + Arrays.toString(names));
        return null;
    }

    private void readSettings() {
        FileConfiguration c = getConfig();
        damageMultiplier = valid(c.getInt("multipliers.damage",1), 1,2,3,5) ? c.getInt("multipliers.damage",1) : 1;
        healthMultiplier = valid(c.getInt("multipliers.health",1), 1,2,3) ? c.getInt("multipliers.health",1) : 1;
        speedMultiplier = valid(c.getInt("multipliers.speed",1), 1,2) ? c.getInt("multipliers.speed",1) : 1;
    }
    private boolean valid(int n, int... allowed) { for (int v : allowed) if (n==v) return true; return false; }

    private void startScanner() {
        if (scanner != null) scanner.cancel();
        long interval = Math.max(20L, getConfig().getLong("scan-interval-ticks", 100));
        scanner = Bukkit.getScheduler().runTaskTimer(this, this::scanAll, 1L, interval);
    }
    private void scanAll() {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) apply(entity);
        }
    }

    private boolean excluded(LivingEntity entity) {
        if (entity instanceof Player || entity instanceof ArmorStand) return true;
        String type = entity.getType().name().toUpperCase(Locale.ROOT);
        for (String s : getConfig().getStringList("excluded-entity-types")) if (type.equalsIgnoreCase(s)) return true;
        for (String s : getConfig().getStringList("bosses.entity-types")) if (type.equalsIgnoreCase(s)) return true;
        for (String s : getConfig().getStringList("bosses.scoreboard-tags")) if (entity.getScoreboardTags().contains(s)) return true;
        String name = entity.getCustomName() == null ? "" : ChatColor.stripColor(entity.getCustomName());
        for (String s : getConfig().getStringList("bosses.name-contains")) {
            if (!s.isBlank() && (type.toLowerCase(Locale.ROOT).contains(s.toLowerCase(Locale.ROOT)) || name.toLowerCase(Locale.ROOT).contains(s.toLowerCase(Locale.ROOT)))) return true;
        }
        double threshold = getConfig().getDouble("bosses.min-base-health", 200.0);
        AttributeInstance max = maxHealthAttribute == null ? null : entity.getAttribute(maxHealthAttribute);
        if (threshold > 0 && max != null) {
            Double saved = entity.getPersistentDataContainer().get(baseHealthKey, PersistentDataType.DOUBLE);
            if ((saved != null ? saved : max.getBaseValue()) >= threshold) return true;
        }
        return false;
    }

    private void apply(LivingEntity entity) {
        if (!entity.isValid()) return;
        // Si una entidad pasa a ser excluida, se restauran sus atributos previos.
        boolean skip = excluded(entity);
        updateAttribute(entity, maxHealthAttribute, baseHealthKey, skip ? 1 : healthMultiplier, true);
        updateAttribute(entity, speedAttribute, baseSpeedKey, skip ? 1 : speedMultiplier, false);
    }

    private void updateAttribute(LivingEntity entity, Attribute attribute, NamespacedKey key, int multiplier, boolean health) {
        if (attribute == null) return;
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) return;
        Double stored = entity.getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
        if (stored == null && multiplier == 1) return;
        double original = stored == null ? instance.getBaseValue() : stored;
        if (stored == null) entity.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, original);
        double target = original * multiplier;
        if (Math.abs(instance.getBaseValue()-target) > 0.00001) {
            double fraction = 1.0;
            if (health) fraction = entity.getHealth() / Math.max(0.01, entity.getMaxHealth());
            try {
                instance.setBaseValue(target);
                if (health) entity.setHealth(Math.max(0.01, Math.min(entity.getMaxHealth(), fraction * entity.getMaxHealth())));
            } catch (IllegalArgumentException ex) { getLogger().fine("Atributo no compatible: " + entity.getType()); }
        }
        if (multiplier == 1) entity.getPersistentDataContainer().remove(key);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof LivingEntity living) {
            Bukkit.getScheduler().runTask(this, () -> { if (living.isValid()) apply(living); });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (damageMultiplier == 1) return;
        Entity attacker = event.getDamager();
        if (attacker instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) attacker = shooter;
        if (!(attacker instanceof LivingEntity living) || excluded(living)) return;
        // Multiplica el daño del mob, nunca el daño de jugadores ni de bosses excluidos.
        event.setDamage(event.getDamage() * damageMultiplier);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("potitomobs.admin")) { sender.sendMessage("§cSin permiso."); return true; }
        if (args.length == 0 || args[0].equalsIgnoreCase("estado")) {
            sender.sendMessage("§5§l✦ POTITOSAFIO — MOBS ✦");
            sender.sendMessage("§dDaño: §fx"+damageMultiplier+" §d| Vida: §fx"+healthMultiplier+" §d| Velocidad: §fx"+speedMultiplier);
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("reset")) {
            getConfig().set("multipliers.damage", 1);
            getConfig().set("multipliers.health", 1);
            getConfig().set("multipliers.speed", 1);
        } else if (action.equals("recargar")) {
            reloadConfig(); readSettings(); startScanner(); scanAll(); sender.sendMessage("§aConfiguración recargada."); return true;
        } else {
            if (args.length != 2) { sender.sendMessage("§eUso: /potitomobs <dano|vida|velocidad> <valor>"); return true; }
            int n;
            try { n = Integer.parseInt(args[1]); } catch (NumberFormatException e) { sender.sendMessage("§cNúmero inválido."); return true; }
            String path;
            switch (action) {
                case "dano": if (!valid(n,1,2,3,5)) { sender.sendMessage("§cDaño permitido: 1, 2, 3, 5."); return true; } path="damage"; break;
                case "vida": if (!valid(n,1,2,3)) { sender.sendMessage("§cVida permitida: 1, 2, 3."); return true; } path="health"; break;
                case "velocidad": if (!valid(n,1,2)) { sender.sendMessage("§cVelocidad permitida: 1, 2."); return true; } path="speed"; break;
                default: sender.sendMessage("§eUso: /potitomobs <dano|vida|velocidad|estado|reset|recargar>"); return true;
            }
            getConfig().set("multipliers."+path, n);
        }
        saveConfig(); readSettings(); scanAll();
        sender.sendMessage("§aPotitoMobs actualizado. §7Daño x"+damageMultiplier+", vida x"+healthMultiplier+", velocidad x"+speedMultiplier);
        return true;
    }
    @Override public void onDisable() {
        if (scanner != null) scanner.cancel();
        // Restaurar atributos si se elimina o desactiva el plugin; configuración permanece guardada.
        for (org.bukkit.World world : Bukkit.getWorlds()) for (LivingEntity entity : world.getLivingEntities()) {
            updateAttribute(entity, maxHealthAttribute, baseHealthKey, 1, true);
            updateAttribute(entity, speedAttribute, baseSpeedKey, 1, false);
        }
    }
}
