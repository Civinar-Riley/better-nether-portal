package net.riley.betterportal.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.PortalForcer;
import net.riley.betterportal.config.PortalConfig;
import net.riley.betterportal.portal.CustomPortalShape;
import net.riley.betterportal.portal.PortalBlueprint;
import net.riley.betterportal.portal.PortalDestinationContext;
import net.riley.betterportal.portal.PortalCreation;
import net.riley.betterportal.portal.PortalFailure;
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
        if (source != null && PortalConfig.cfg(PortalConfig.PORTALS.copyShapeToDestination)) {
            Optional<BlockPos> placed = PortalBlueprint.capture(source.level(), source.portalPos())
                    .flatMap(blueprint -> blueprint.placeNear(this.level, pos));
            if (placed.isPresent()) {
                cir.setReturnValue(Optional.of(PortalTravel.largestRectangle(this.level, placed.get())));
                return;
            }
            if (source.denied()) {
                cir.setReturnValue(Optional.empty());
                return;
            }
        }
        // 复制关闭或失败时，原版 2×3 回退也必须满足同一尺寸上限。
        if (PortalConfig.cfg(PortalConfig.PORTALS.maxInteriorCells) < 6
                || PortalConfig.cfg(PortalConfig.PORTALS.maxSpan) < 3) {
            PortalDestinationContext.fail(PortalFailure.LIMIT);
            cir.setReturnValue(Optional.empty());
        }
    }

    @Inject(method = "createPortal", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"),
            cancellable = true)
    private void riley$approvePlatform(BlockPos pos, Direction.Axis axis,
            CallbackInfoReturnable<Optional<BlockUtil.FoundRectangle>> cir,
            @Local(index = 6) BlockPos root, @Share("creationApproved") LocalBooleanRef approved) {
        if (!approved.get()) {
            approved.set(true);
            if (!PortalCreation.allowFallback(this.level, root, axis, true)) {
                cir.setReturnValue(Optional.empty());
            }
        }
    }

    @Inject(method = "createPortal", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
            ordinal = 0), cancellable = true)
    private void riley$approveFrame(BlockPos pos, Direction.Axis axis,
            CallbackInfoReturnable<Optional<BlockUtil.FoundRectangle>> cir,
            @Local(index = 6) BlockPos root, @Share("creationApproved") LocalBooleanRef approved) {
        if (!approved.get()) {
            approved.set(true);
            if (!PortalCreation.allowFallback(this.level, root, axis, false)) {
                cir.setReturnValue(Optional.empty());
            }
        }
    }

    @WrapOperation(method = "findClosestPortalPosition",
            at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;min(Ljava/util/Comparator;)Ljava/util/Optional;"))
    private Optional<BlockPos> riley$avoidLinkedPortalHijack(Stream<BlockPos> candidates, Comparator<BlockPos> order,
                                                            Operation<Optional<BlockPos>> original) {
        PortalDestinationContext.Source source = PortalDestinationContext.peek();
        if (source == null || !PortalConfig.cfg(PortalConfig.PORTALS.persistentLinks)) {
            return original.call(candidates, order);
        }
        PortalLinkData links = PortalLinkData.get(this.level.getServer());
        // 每扇门只查一次归属；同一门的多个 POI 共享结果，但仍按原版距离排序。
        Map<BlockPos, Boolean> available = new HashMap<>();
        return original.call(candidates.filter(candidate -> {
            Boolean cached = available.get(candidate);
            if (cached != null) {
                return cached;
            }
            Set<BlockPos> cells = CustomPortalShape.collectPortalCells(this.level, candidate);
            if (cells.isEmpty()) {
                return false;
            }
            BlockPos root = CustomPortalShape.rootOf(cells);
            Optional<PortalLinkData.PortalRef> owner = links.getLinkedRef(this.level.dimension(), root);
            boolean allowed = owner.isEmpty() || owner.get().matches(source.level().dimension(), source.root());
            if (!allowed) {
                ServerLevel ownerLevel = this.level.getServer().getLevel(owner.get().dimension());
                boolean ownerAlive = ownerLevel != null && ownerLevel.getBlockState(owner.get().pos()).is(Blocks.NETHER_PORTAL);
                allowed = !ownerAlive || (ownerLevel == source.level()
                        && CustomPortalShape.findPortalRoot(ownerLevel, owner.get().pos()).equals(source.root()));
                if (!ownerAlive) {
                    links.unlink(this.level.dimension(), root);
                }
            }
            for (BlockPos cell : cells) {
                available.put(cell, allowed);
            }
            return allowed;
        }), order);
    }
}
