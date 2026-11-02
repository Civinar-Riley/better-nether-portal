package net.riley.betterportal.mixin;

import java.util.Optional;

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
import net.riley.betterportal.portal.PortalLinkData;
import net.riley.betterportal.portal.PortalTravel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// 门方块存活性：原版邻块变化时用矩形 PortalShape.isComplete 校验，
// 任意形状的门会被误判而碎掉。这里先跑泛洪填充复检，通过则保留方块；
// 不通过则放行原版逻辑（原版矩形门行为不变，框破损照旧碎门）。
// Side: BOTH（updateShape 客户端也会跑；getPortalDestination 只在服务端）
@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalBlockMixin {

    @Inject(method = "updateShape", at = @At("HEAD"), cancellable = true)
    private void riley$customPortalValidity(BlockState state, Direction dir, BlockState neighborState,
                                            LevelAccessor level, BlockPos pos, BlockPos neighborPos,
                                            CallbackInfoReturnable<BlockState> cir) {
        Direction.Axis myAxis = state.getValue(NetherPortalBlock.AXIS);
        Direction.Axis neighborAxis = dir.getAxis();
        boolean flag = myAxis != neighborAxis && neighborAxis.isHorizontal();
        // 与原版守卫一致：垂直平面的变化或相邻也是门方块时无需校验
        if (flag || neighborState.is((NetherPortalBlock) (Object) this)) {
            return;
        }
        if (CustomPortalShape.isPortalStillValid(level, pos, myAxis)) {
            cir.setReturnValue(state);
        }
    }

    // 传送前：写入上下文（供 PortalForcer mixin 复制门形），并优先走已链接的目标门。
    @Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
    private void riley$linkedDestination(ServerLevel level, Entity entity, BlockPos pos,
                                         CallbackInfoReturnable<DimensionTransition> cir) {
        BlockPos sourceRoot = CustomPortalShape.findPortalRoot(level, pos);
        PortalDestinationContext.push(new PortalDestinationContext.Source(level, pos.immutable(), sourceRoot));
        if (!PortalConfig.cfg(PortalConfig.PORTALS.persistentLinks)) {
            return;
        }
        ResourceKey<Level> targetKey = level.dimension() == Level.NETHER ? Level.OVERWORLD : Level.NETHER;
        ServerLevel target = level.getServer().getLevel(targetKey);
        if (target == null) {
            return;
        }
        PortalLinkData links = PortalLinkData.get(level.getServer());
        Optional<BlockPos> linked = links.getLinkedPos(level.dimension(), sourceRoot, targetKey);
        if (linked.isEmpty()) {
            return;
        }
        if (!target.getBlockState(linked.get()).is(Blocks.NETHER_PORTAL)) {
            // 目标门已拆：顺手清掉失效链接，存档里的链接表不会只增不减
            links.unlink(level.dimension(), sourceRoot);
            return;
        }
        BlockPos targetRoot = CustomPortalShape.findPortalRoot(target, linked.get());
        DimensionTransition transition = PortalTravel.toLinkedPortal(target, entity, pos, targetRoot);
        if (transition != null) {
            // HEAD 处 setReturnValue 会提前返回，RETURN 注入不再执行，必须在这里清上下文
            PortalDestinationContext.clear();
            cir.setReturnValue(transition);
        }
    }

    // 传送后：把源门与本次实际到达的门配对；无论成功与否都清理 ThreadLocal。
    @Inject(method = "getPortalDestination", at = @At("RETURN"))
    private void riley$recordDestinationLink(ServerLevel level, Entity entity, BlockPos pos,
                                             CallbackInfoReturnable<DimensionTransition> cir) {
        try {
            if (!PortalConfig.cfg(PortalConfig.PORTALS.persistentLinks)) {
                return;
            }
            PortalDestinationContext.Source source = PortalDestinationContext.peek();
            DimensionTransition transition = cir.getReturnValue();
            if (source == null || transition == null) {
                return;
            }
            ServerLevel target = transition.newLevel();
            BlockPos targetRoot = PortalTravel.findPortalRootNear(target, transition.pos());
            if (targetRoot != null) {
                PortalLinkData.get(level.getServer())
                        .link(level.dimension(), source.root(), target.dimension(), targetRoot);
            }
        } finally {
            PortalDestinationContext.clear();
        }
    }
}
