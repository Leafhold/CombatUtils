package org.leafhold.combatUtils;

import org.bukkit.plugin.java.JavaPlugin;

import org.leafhold.combatUtils.listeners.DamageIndicatorManager;

public final class CombatUtils extends JavaPlugin {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new DamageIndicatorManager(this), this);
        getLogger().info("CombatUtils successfully enabled!");
    }

    @Override
    public void onDisable() {
        getServer().getPluginManager().disablePlugin(this);
        getLogger().info("CombatUtils successfully disabled!");
    }
}
