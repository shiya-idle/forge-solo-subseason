package com.forgemagic.fabric;

import com.forgemagic.claims.ClaimsService;
import com.forgemagic.claims.ImmutableClaimsService;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;

/** Connects the immutable service to server-side block interaction events. */
public final class FabricClaimsGuard {
    private final ImmutableClaimsService claims;

    public FabricClaimsGuard(ImmutableClaimsService claims) { this.claims = claims; }

    public void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) -> allowed(player, world, pos));
        AttackBlockCallback.EVENT.register(this::attackBlock);
        UseBlockCallback.EVENT.register(this::useBlock);
    }

    private boolean allowed(Player player, Level world, BlockPos pos) {
        return claims.canModify(player.getUUID(), block(world, pos));
    }

    private InteractionResult attackBlock(Player player, Level world, net.minecraft.world.InteractionHand hand,
                                          BlockPos pos, net.minecraft.core.Direction direction) {
        return allowed(player, world, pos) ? InteractionResult.PASS : InteractionResult.FAIL;
    }

    private InteractionResult useBlock(Player player, Level world, net.minecraft.world.InteractionHand hand,
                                       BlockHitResult hit) {
        BlockPos clicked = hit.getBlockPos();
        boolean placing = player.getItemInHand(hand).getItem() instanceof BlockItem;
        if (placing && !allowed(player, world, clicked.relative(hit.getDirection()))) return InteractionResult.FAIL;
        if (!placing && !allowed(player, world, clicked)) return InteractionResult.FAIL;
        return InteractionResult.PASS;
    }

    private ClaimsService.ClaimBlock block(Level world, BlockPos pos) {
        return new ClaimsService.ClaimBlock(world.dimension().identifier().toString(), pos.getX(), pos.getY(), pos.getZ());
    }
}
