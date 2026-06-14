package org.leafhold.combatUtils.listeners;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedChatComponent; // Added this import
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import org.leafhold.combatUtils.CombatUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class DamageIndicatorManager implements Listener {
    private final CombatUtils combatUtils;
    private final AtomicInteger entityIdCounter = new AtomicInteger(2000000);

    public DamageIndicatorManager(CombatUtils combatUtils) {
        this.combatUtils = combatUtils;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;

        LivingEntity victim = (LivingEntity) event.getEntity();
        Location loc = victim.getEyeLocation().add(ThreadLocalRandom.current().nextDouble(-0.5, 0.5), 0.5, ThreadLocalRandom.current().nextDouble(-0.5, 0.5));
        double damage = Math.round(event.getFinalDamage() * 10.0) / 10.0;
        boolean isCritical = event.isCritical();

        Bukkit.getScheduler().runTaskAsynchronously(combatUtils, () -> spawnHoloPacket(attacker, loc, damage, isCritical));
    }

    private void spawnHoloPacket(Player player, Location loc, double damage, boolean isCritical) {
        int fakeId = entityIdCounter.getAndIncrement();
        UUID uuid = UUID.randomUUID();

        // 1. Setup the Spawn Packet
        PacketContainer spawnPacket = new PacketContainer(PacketType.Play.Server.SPAWN_ENTITY);
        spawnPacket.getIntegers().write(0, fakeId);
        spawnPacket.getUUIDs().write(0, uuid);
        spawnPacket.getEntityTypeModifier().write(0, EntityType.TEXT_DISPLAY);
        spawnPacket.getDoubles().write(0, loc.getX()).write(1, loc.getY()).write(2, loc.getZ());

        // 2. Setup the Metadata Packet
        PacketContainer metaPacket = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);
        metaPacket.getIntegers().write(0, fakeId);

        List<WrappedDataValue> metadata = new ArrayList<>();
        TextComponent text = isCritical
                ? Component.text(String.valueOf(damage)).color(NamedTextColor.RED).decorate(TextDecoration.BOLD)
                : Component.text(String.valueOf(damage)).color(NamedTextColor.YELLOW);

        // Serialize Kyori component to JSON and wrap into ProtocolLib Chat handle
        String jsonText = GsonComponentSerializer.gson().serialize(text);
        WrappedChatComponent chatComponent = WrappedChatComponent.fromJson(jsonText);

        // FIX: Use non-deprecated, type-safe ChatComponent and Byte serializers
        metadata.add(new WrappedDataValue(23, WrappedDataWatcher.Registry.getChatComponentSerializer(false), chatComponent.getHandle()));
        metadata.add(new WrappedDataValue(15, WrappedDataWatcher.Registry.get(Byte.class), (byte) 3));

        // FIX: Use dedicated DataValue modifier to prevent ClassCastExceptions in Netty
        metaPacket.getDataValueCollectionModifier().write(0, metadata);

        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, spawnPacket);
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, metaPacket);

            // FIX: Changed to runTaskLaterAsynchronously to support tick delays properly
            Bukkit.getScheduler().runTaskLaterAsynchronously(combatUtils, () -> {
                PacketContainer destroyPacket = new PacketContainer(PacketType.Play.Server.ENTITY_DESTROY);
                destroyPacket.getIntLists().write(0, Collections.singletonList(fakeId));
                try {
                    ProtocolLibrary.getProtocolManager().sendServerPacket(player, destroyPacket);
                } catch (Exception e) {
                    combatUtils.getLogger().warning("Failed to clear holo indicator: " + e.getMessage());
                }
            }, 20L);

        } catch (Exception e) {
            combatUtils.getLogger().warning("Failed to dispatch indicator packets: " + e.getMessage());
        }
    }
}