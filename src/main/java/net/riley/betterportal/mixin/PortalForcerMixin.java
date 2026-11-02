package net.riley.betterportal.mixin;

import java.util.Optional;

import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.portal.PortalForcer;
import net.riley.betterportal.config.PortalConfig;
import net.riley.betterportal.portal.CustomPortalShape;
import net.riley.betterportal.portal.PortalBlueprint;
import net.riley.betterportal.portal.PortalDestinationContext;
import net.riley.betterportal.portal.PortalLinkData;
import net.riley.betterportal.portal.PortalTravel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// 传送目的地改造（Side: SERVER）：
// 1) createPortal：有源门上下文时，优先按蓝图在对面复制同形状门；失败放行原版矩形门。
// 2) findClosestPortalPosition：过滤掉已属于其他源门的链接，避免附近门互相抢人。
@Mixin(PortalForcer.class)
public abstract class PortalForcerMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @Inject(method = "createPortal", at = @At("HEAD"), cancellable = true)
    private void riley$createBlueprintPortal(BlockPos pos, Direction.Axis axis,
            CallbackInfoReturnable<Optional<BlockUtil.FoundRectangle>> cir) {
        PortalDestinationContext.Source source = PortalDestinationContext.peek();
        if (source == null || !PortalConfig.cfg(PortalConfig.PORTALS.copyShapeToDestination)) {
            return;
        }
        Optional<PortalBlueprint> blueprint = PortalBlueprint.capture(source.level(), source.portalPos());
        if (blueprint.isEmpty()) {
            return;
        }
        Optional<BlockPos> placed = blueprint.get().placeNear(this.level, pos);
        if (placed.isEmpty()) {
            return;
        }
        BlockPos root = placed.get();
        if (!this.level.getBlockState(root).is(Blocks.NETHER_PORTAL)) {
            return;
        }
        cir.setReturnValue(Optional.of(PortalTravel.largestRectangle(this.level, root)));
    }

    @Inject(method = "findClosestPortalPosition", at = @At("RETURN"), cancellable = true)
    private void riley$avoidLinkedPortalHijack(BlockPos exitPos, boolean isNether, WorldBorder worldBorder,
            CallbackInfoReturnable<Optional<BlockPos>> cir) {
        if (!PortalConfig.cfg(PortalConfig.PORTALS.persistentLinks)) {
            return;
        }
        PortalDestinationContext.Source source = PortalDestinationContext.peek();
        Optional<BlockPos> found = cir.getReturnValue();
        if (source == null || found.isEmpty()) {
            return;
        }
        BlockPos targetRoot = CustomPortalShape.findPortalRoot(this.level, found.get());
        Optional<PortalLinkData.PortalRef> owner = PortalLinkData.get(this.level.getServer())
                .getLinkedRef(this.level.dimension(), targetRoot);
        if (owner.isEmpty() || owner.get().matches(source.level().dimension(), source.root())) {
            return;
        }
        // 链接另一端的门已拆掉时视为失效链接，允许原版复用该门并重新配对。
        boolean ownerAlive = owner.get().dimension().equals(source.level().dimension())
                && source.level().getBlockState(owner.get().pos()).is(Blocks.NETHER_PORTAL);
        if (ownerAlive) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
