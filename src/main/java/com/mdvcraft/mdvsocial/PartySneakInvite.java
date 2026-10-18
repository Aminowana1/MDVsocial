package com.mdvcraft.mdvsocial;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Crouch + right-click (Bedrock: interact / long press) another player with an
 * empty hand to invite them to your adventure party. A party is created first
 * when you have none; inside a party only the leader can invite.
 *
 * Config: {@code party-invites.sneak-interact.enabled} (true by default) and
 * {@code party-invites.sneak-interact.cooldown-millis}.
 */
final class PartySneakInvite implements Listener {

    enum Outcome { CREATE_AND_INVITE, INVITE, NOT_LEADER, ALREADY_MEMBER, FULL }

    private static final String PATH = "party-invites.sneak-interact.";

    private final MDVSocialPlugin plugin;
    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();

    PartySneakInvite(MDVSocialPlugin plugin) {
        this.plugin = plugin;
    }

    void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        // The off hand fires a second event for the same click.
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Player target))
            return;
        Player player = event.getPlayer();
        if (!player.isSneaking() || !player.getInventory().getItemInMainHand().getType().isAir())
            return;
        // Citizens and similar NPCs are Player entities too.
        if (target.hasMetadata("NPC") || !player.canSee(target))
            return;
        if (!plugin.getConfig().getBoolean(PATH + "enabled", true) || !player.hasPermission("mdvsocial.party.use")
                || !Bukkit.getPluginManager().isPluginEnabled("MMOCore"))
            return;
        event.setCancelled(true);
        long now = System.currentTimeMillis();
        if (!cooledDown(player.getUniqueId(), now, plugin.getConfig().getLong(PATH + "cooldown-millis", 1500L)))
            return;
        invite(player, target);
    }

    private void invite(Player player, Player target) {
        Object party = plugin.getMMOCoreParty(player);
        Outcome outcome = party == null
                ? decide(false, false, false, false)
                : decide(true, plugin.isMMOCorePartyLeader(player, party),
                        plugin.isMMOCorePartyMember(party, target.getUniqueId()),
                        plugin.countMMOCorePartyMembers(party) >= plugin.getConfiguredPartyMaxMembers());
        Map<String, String> tokens = Map.of("target", target.getName(),
                "max", String.valueOf(plugin.getConfiguredPartyMaxMembers()));
        switch (outcome) {
            case NOT_LEADER -> plugin.msg(player, "party-sneak-invite-not-leader", tokens);
            case ALREADY_MEMBER -> plugin.msg(player, "party-target-already-member", tokens);
            case FULL -> plugin.msg(player, "party-full", tokens);
            case CREATE_AND_INVITE -> {
                if (!plugin.createPartyFor(player)) {
                    plugin.msg(player, "party-create-error");
                    return;
                }
                plugin.msg(player, "party-created");
                plugin.invitePlayerToParty(player, target);
            }
            case INVITE -> plugin.invitePlayerToParty(player, target);
        }
    }

    static Outcome decide(boolean hasParty, boolean leader, boolean targetIsMember, boolean full) {
        if (!hasParty)
            return Outcome.CREATE_AND_INVITE;
        if (targetIsMember)
            return Outcome.ALREADY_MEMBER;
        if (!leader)
            return Outcome.NOT_LEADER;
        return full ? Outcome.FULL : Outcome.INVITE;
    }

    /** Rate limit per player: a held click or a double event sends one invite. */
    boolean cooledDown(UUID player, long now, long cooldownMillis) {
        Long last = lastUse.get(player);
        if (last != null && now - last < cooldownMillis)
            return false;
        lastUse.put(player, now);
        return true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastUse.remove(event.getPlayer().getUniqueId());
    }
}
