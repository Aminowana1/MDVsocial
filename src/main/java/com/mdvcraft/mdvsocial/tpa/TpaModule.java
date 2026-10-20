package com.mdvcraft.mdvsocial.tpa;

import com.mdvcraft.mdvsocial.quickaccept.QuickAcceptService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Entry point of the TPA system. The host plugin builds one instance, calls
 * {@link #enable()} once and {@link #reload()} after reloading its config.
 */
public final class TpaModule {

    private static final long EXPIRY_CHECK_TICKS = 20L;

    private final TpaHost host;
    private final TpaMessages messages;
    private final TpaTeleporter teleporter;
    private final TpaService service;
    private volatile TpaSettings settings = TpaSettings.DEFAULTS;
    private BukkitTask expiryTask;

    public TpaModule(TpaHost host, QuickAcceptService quickAccept) {
        this.host = host;
        this.messages = new TpaMessages(() -> host.plugin().getConfig().getConfigurationSection("tpa"), host::color);
        this.teleporter = new TpaTeleporter(host.plugin(), messages, this::settings);
        this.service = new TpaService(host, messages, this::settings, teleporter,
                new TpaNotifier(host, quickAccept));
    }

    /**
     * Copies the TPA settings of MDVTools into MDVSocial's config.yml the first
     * time. Call before the config is loaded.
     */
    public static boolean importLegacyConfig(JavaPlugin plugin) {
        return MDVToolsTpaImport.run(plugin);
    }

    public void enable() {
        reload();
        new TpaCommand(host, service, messages).register();
        Bukkit.getPluginManager().registerEvents(new TpaListener(() -> settings.enabled(), service, teleporter),
                host.plugin());
        expiryTask = Bukkit.getScheduler().runTaskTimer(host.plugin(), service::expireRequests,
                EXPIRY_CHECK_TICKS, EXPIRY_CHECK_TICKS);
    }

    public void reload() {
        settings = TpaSettings.from(host.plugin().getConfig().getConfigurationSection("tpa"));
    }

    public void shutdown() {
        if (expiryTask != null)
            expiryTask.cancel();
        service.shutdown();
    }

    public TpaService service() {
        return service;
    }

    TpaSettings settings() {
        return settings;
    }
}
