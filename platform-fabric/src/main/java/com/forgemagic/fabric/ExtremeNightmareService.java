package com.forgemagic.fabric;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;

public final class ExtremeNightmareService {
    public static final double HARD_MULTIPLIER = 5.0;
    private boolean enabled;
    public ExtremeNightmareService(boolean enabled) { this.enabled = enabled; }
    public boolean enabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /** Applies a refreshed server-side combat modifier to nearby hostile mobs. */
    public void tick(ServerPlayer player) {
        if (!enabled) return;
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(48), Mob::isAlive)) {
            mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 40, 4, false, false, true));
            mob.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 1, false, false, true));
        }
    }
}
