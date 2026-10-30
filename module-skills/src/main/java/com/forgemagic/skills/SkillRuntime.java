package com.forgemagic.skills;

import com.forgemagic.api.ManaState;
import com.forgemagic.api.SkillDefinition;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-authoritative mana/cooldown state with effect-before-commit semantics. */
public final class SkillRuntime {
    private final Clock clock;
    private final Map<UUID, ManaState> mana = new ConcurrentHashMap<>();
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<String, CastTicket> reservations = new ConcurrentHashMap<>();
    private final Map<UUID, Double> cooldownReductions = new ConcurrentHashMap<>();

    public SkillRuntime(Clock clock) { this.clock = clock; }

    public synchronized Optional<CastTicket> beginCast(UUID player, SkillDefinition skill, int playerLevel,
                                                         int skillLevel, double stageManaBonus, double stageCdr) {
        ManaState state = mana.computeIfAbsent(player, id -> {
            double max = SkillMath.manaMaximum(playerLevel, stageManaBonus);
            return new ManaState(max, max);
        });
        long now = clock.millis();
        String key = player + ":" + skill.id();
        if (cooldowns.getOrDefault(key, 0L) > now || reservations.containsKey(key)) return Optional.empty();
        double cost = SkillMath.manaCost(skill, playerLevel, stageManaBonus);
        if (state.current() < cost) return Optional.empty();
        double playerCdr = Math.max(0.0, Math.min(0.30, cooldownReductions.getOrDefault(player, 0.0)));
        CastTicket ticket = new CastTicket(UUID.randomUUID(), player, skill.id(), cost,
                now + (long) (SkillMath.cooldownSeconds(skill, skillLevel, stageCdr + playerCdr) * 1000));
        reservations.put(key, ticket);
        return Optional.of(ticket);
    }

    public synchronized boolean commit(CastTicket ticket) {
        String key = ticket.player() + ":" + ticket.skillId();
        CastTicket active = reservations.get(key);
        if (active == null || !active.token().equals(ticket.token())) return false;
        ManaState state = mana.get(ticket.player());
        long now = clock.millis();
        if (state == null || state.current() < ticket.cost() || cooldowns.getOrDefault(key, 0L) > now) {
            reservations.remove(key);
            return false;
        }
        mana.put(ticket.player(), state.spend(ticket.cost()));
        cooldowns.put(key, ticket.cooldownUntilMillis());
        reservations.remove(key);
        return true;
    }

    public synchronized void discard(CastTicket ticket) {
        String key = ticket.player() + ":" + ticket.skillId();
        CastTicket active = reservations.get(key);
        if (active != null && active.token().equals(ticket.token())) reservations.remove(key);
    }

    public synchronized boolean cast(UUID player, SkillDefinition skill, int playerLevel, int skillLevel,
                                     double stageManaBonus, double stageCdr) {
        Optional<CastTicket> ticket = beginCast(player, skill, playerLevel, skillLevel, stageManaBonus, stageCdr);
        return ticket.isPresent() && commit(ticket.get());
    }

    public void regenerate(UUID player, double amount) { mana.computeIfPresent(player, (id, state) -> state.regenerate(amount)); }
    public void setMana(UUID player, ManaState state) { mana.put(player, state); }
    public ManaState mana(UUID player) { return mana.get(player); }
    public void setCooldownReduction(UUID player, double reduction) { cooldownReductions.put(player, Math.max(0.0, Math.min(0.30, reduction))); }
    public double cooldownReduction(UUID player) { return cooldownReductions.getOrDefault(player, 0.0); }

    public record CastTicket(UUID token, UUID player, String skillId, double cost, long cooldownUntilMillis) {}
}
