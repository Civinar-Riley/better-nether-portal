package net.riley.betterportal.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Optional;

import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.riley.betterportal.config.PortalConfig;
import net.riley.betterportal.portal.CustomPortalShape;
import net.riley.betterportal.portal.PortalDestinationContext;
import net.riley.betterportal.portal.PortalFailure;
import net.riley.betterportal.portal.PortalFeedback;
import net.riley.betterportal.portal.PortalLinkData;
import net.riley.betterportal.portal.PortalTravel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// 门方块按同轴连通形状复检；所有出口统一处理异形门落点与成对链接。
// Side: BOTH（updateShape 客户端也会跑；传送只在服务端）
@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalBlockMixin {

    @Inject(method = "updateShape", at = @At("HEAD"), cancellable = true)
    private void riley$customPortalValidity(BlockState state, Direction dir, BlockState neighborState,
                                            LevelAccessor level, BlockPos pos, BlockPos neighborPos,
                                            CallbackInfoReturnable<BlockState> cir) {
        Direction.Axis myAxis = state.getValue(NetherPortalBlock.AXIS);
        if (dir.getAxis().isHorizontal() && dir.getAxis() != myAxis) {
            return;
        }
        if (neighborState.is(Blocks.NETHER_PORTAL) && neighborState.getValue(NetherPortalBlock.AXIS) == myAxis) {
            return;
        }
        cir.setReturnValue(CustomPortalShape.isPortalStillValid(level, pos, myAxis)
                ? state : Blocks.AIR.defaultBlockState());
    }

    // 整次调用用 finally 收口；已链接的提前返回与嵌套调用都恢复原来的上下文。
    @WrapMethod(method = "getPortalDestination")
    private DimensionTransition riley$linkedDestination(ServerLevel level, Entity entity, BlockPos pos,
                                                         Operation<DimensionTransition> original) {
        BlockPos sourceRoot = CustomPortalShape.findPortalRoot(level, pos);
        PortalDestinationContext.Source previous = PortalDestinationContext.peek();
        PortalDestinationContext.Source source = new PortalDestinationContext.Source(level, pos, sourceRoot);
        PortalDestinationContext.push(source);
        try {
            if (PortalConfig.cfg(PortalConfig.PORTALS.persistentLinks)) {
                ResourceKey<Level> targetKey = level.dimension() == Level.NETHER ? Level.OVERWORLD : Level.NETHER;
                ServerLevel target = level.getServer().getLevel(targetKey);
                PortalLinkData links = PortalLinkData.get(level.getServer());
                Optional<BlockPos> linked = links.getLinkedPos(level.dimension(), sourceRoot, targetKey);
                if (target != null && linked.isPresent()) {
                    BlockPos targetPos = linked.get();
                    if (target.getWorldBorder().isWithinBounds(targetPos)
                            && target.getBlockState(targetPos).is(Blocks.NETHER_PORTAL)) {
                        BlockPos targetRoot = CustomPortalShape.findPortalRoot(target, targetPos);
                        DimensionTransition transition = PortalTravel.toPortal(target, entity, pos, targetRoot,
                                DimensionTransition.PLAY_PORTAL_SOUND.then(e -> e.placePortalTicket(targetRoot))
                                        .then(e -> links.link(level.dimension(), sourceRoot, targetKey, targetRoot)));
                        if (transition == null) {
                            PortalFeedback.showFailure(entity, source.failure());
                        }
                        return transition;
                    }
                    if (!target.getWorldBorder().isWithinBounds(targetPos)) {
                        PortalDestinationContext.fail(PortalFailure.BORDER);
                    }
                    links.unlink(level.dimension(), sourceRoot);
                }
            }
            DimensionTransition transition = original.call(level, entity, pos);
            if (transition == null) {
                PortalFeedback.showFailure(entity, source.failure());
            }
            return transition;
        } finally {
            if (previous == null) {
                PortalDestinationContext.clear();
            } else {
                PortalDestinationContext.push(previous);
            }
        }
    }

    // 在原版已选定出口矩形后记录确切的门；碰撞修正可能把实体移到旁边，不能再从落点猜门。
    @Inject(method = "getDimensionTransitionFromExit", at = @At("HEAD"), cancellable = true)
    private static void riley$customExit(Entity entity, BlockPos pos, BlockUtil.FoundRectangle rectangle,
                                         ServerLevel target, DimensionTransition.PostDimensionTransition postTransition,
                                         CallbackInfoReturnable<DimensionTransition> cir) {
        BlockPos targetRoot = CustomPortalShape.findPortalRoot(target, rectangle.minCorner);
        PortalDestinationContext.Source source = PortalDestinationContext.peek();
        if (source != null && PortalConfig.cfg(PortalConfig.PORTALS.persistentLinks)) {
            postTransition = postTransition.then(e -> PortalLinkData.get(target.getServer())
                    .link(source.level().dimension(), source.root(), target.dimension(), targetRoot));
        }
        cir.setReturnValue(PortalTravel.toPortal(target, entity, pos, targetRoot, postTransition));
    }
}
