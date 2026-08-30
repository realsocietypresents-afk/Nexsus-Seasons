package com.nexusuniverse.seasons.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

/**
 * Soft-dependency bridge to NexusMorality's Problem+Solution chat messenger
 * (NexusMoralityMessengerApi) - same reflection + Bukkit ServicesManager
 * shape as NexusServerRules' integration/LandTrustBridge.java, and the
 * identical class NexusVitals and NexusCombat already added for the same
 * purpose. NexusSeasons never gets NexusMorality's classes on its own
 * compile classpath - it only knows the fully-qualified class name and the
 * one method signature it needs, so this keeps working (announce() just
 * becomes a no-op) whether or not NexusMorality is installed at all.
 *
 * Despite how large this plugin's "crazy weather" layer is, a full audit
 * found exactly one place that actually deals real damage to a player:
 * MeteorShowerManager's impact explosion, and only when
 * meteor-shower.damage-enabled is turned on (off by default). Everything
 * else in the weather layer - wind push, tornado lift/pull, earthquake
 * jitter, block collapses, flooding/waves - is knockback, a world/block
 * effect, or something that could only ever lead to a normal vanilla
 * damage cause (fall, drowning) that NexusMorality's own generic damage
 * listener already covers source-agnostically, so none of that is wired
 * to this bridge.
 */
public final class MoralityMessengerBridge {

    private static final String API_CLASS_NAME = "com.nexusuniverse.morality.messages.NexusMoralityMessengerApi";

    private final JavaPlugin plugin;
    private Object service;
    private Method announceMethod;
    private boolean warnedAboutInvokeFailure;

    public MoralityMessengerBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Looks up NexusMorality's messenger service. Call once from onEnable -
     * after registering NexusMorality as a softdepend in plugin.yml so, if
     * it's installed, it's already loaded and has already registered its
     * service by the time this runs.
     */
    public void resolve() {
        try {
            Class<?> apiClass = Class.forName(API_CLASS_NAME);
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(apiClass);
            if (registration == null) {
                plugin.getLogger().info("NexusSeasons: NexusMorality's messenger service isn't registered "
                        + "(plugin not installed, or not enabled yet) - meteor impact damage messages "
                        + "stay standalone this session.");
                return;
            }
            this.service = registration.getProvider();
            this.announceMethod = apiClass.getMethod("announce", Player.class, String.class, String.class, String.class);
            plugin.getLogger().info("NexusSeasons: found NexusMorality's Problem+Solution messenger - "
                    + "meteor impact damage will announce there too.");
        } catch (ClassNotFoundException e) {
            // NexusMorality isn't installed at all - expected and fine, NexusSeasons works standalone.
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("NexusSeasons: found NexusMorality but couldn't bind to its messenger "
                    + "API (" + e + ") - staying standalone this session.");
        }
    }

    /** Safe to call unconditionally - a no-op whenever resolve() didn't find a working service. */
    public void announce(Player player, String causeKey, String problem, String solution) {
        if (service == null || announceMethod == null) return;
        try {
            announceMethod.invoke(service, player, causeKey, problem, solution);
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!warnedAboutInvokeFailure) {
                warnedAboutInvokeFailure = true;
                plugin.getLogger().warning("NexusSeasons: a call into NexusMorality's messenger failed ("
                        + e + ") - won't repeat this warning again this session.");
            }
        }
    }
}
