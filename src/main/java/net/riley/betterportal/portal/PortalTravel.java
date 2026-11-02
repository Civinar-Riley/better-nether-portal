package net.riley.betterportal.portal;

import java.util.Set;

import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.Vec3;

// 已链接门的落点计算：复刻原版 getDimensionTransitionFromExit 的定位公式，
// 但门形可以是非矩形（用最大内接矩形计算实体在门内的相对位置）。
// Side: SERVER
public final class PortalTravel {
    private PortalTravel() {
    }

    // 构造“回到已链接门”的过渡；目标门不存在/不是门方块时返回 null，由调用方回退原版。
    public static DimensionTransition toLinkedPortal(ServerLevel target, Entity entity, BlockPos sourcePos, BlockPos targetRoot) {
        BlockState targetState = target.getBlockState(targetRoot);
        if (!targetState.is(Blocks.NETHER_PORTAL)) {
            return null;
        }
        BlockUtil.FoundRectangle targetRect = largestRectangle(target, targetRoot);

        BlockState sourceState = entity.level().getBlockState(sourcePos);
        Direction.Axis sourceAxis = sourceState.hasProperty(NetherPortalBlock.AXIS)
                ? sourceState.getValue(NetherPortalBlock.AXIS)
                : Direction.Axis.X;
        BlockUtil.FoundRectangle sourceRect = BlockUtil.getLargestRectangleAround(
                sourcePos, sourceAxis, 21, Direction.Axis.Y, 21,
                pos -> entity.level().getBlockState(pos) == sourceState);
        Vec3 offset = entity.getRelativePortalPosition(sourceAxis, sourceRect);

        BlockPos min = targetRect.minCorner;
        BlockState exitState = target.getBlockState(min);
        Direction.Axis exitAxis = exitState.getOptionalValue(NetherPortalBlock.AXIS).orElse(Direction.Axis.X);
        double width = targetRect.axis1Size;
        double height = targetRect.axis2Size;
        EntityDimensions dimensions = entity.getDimensions(entity.getPose());
        int rotation = sourceAxis == exitAxis ? 0 : 90;
        Vec3 speed = sourceAxis == exitAxis
                ? entity.getDeltaMovement()
                : new Vec3(entity.getDeltaMovement().z, entity.getDeltaMovement().y, -entity.getDeltaMovement().x);
        double offsetRight = dimensions.width() / 2.0 + (width - dimensions.width()) * offset.x();
        double offsetUp = (height - dimensions.height()) * offset.y();
        double offsetForward = 0.5 + offset.z();
        boolean xAligned = exitAxis == Direction.Axis.X;
        Vec3 targetPos = new Vec3(
                min.getX() + (xAligned ? offsetRight : offsetForward),
                min.getY() + offsetUp,
                min.getZ() + (xAligned ? offsetForward : offsetRight));
        Vec3 collisionFree = PortalShape.findCollisionFreePosition(targetPos, target, entity, dimensions);
        return new DimensionTransition(
                target, collisionFree, speed, entity.getYRot() + rotation, entity.getXRot(),
                DimensionTransition.PLAY_PORTAL_SOUND.then(e -> e.placePortalTicket(targetRoot)));
    }

    // 传送落点通常在门内/门口；在 7x7x7 范围找最近的门方块，再取整扇门的根。
    public static BlockPos findPortalRootNear(ServerLevel level, Vec3 pos) {
        BlockPos center = BlockPos.containing(pos);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos candidate = center.offset(dx, dy, dz);
                    if (!level.getBlockState(candidate).is(Blocks.NETHER_PORTAL)) {
                        continue;
                    }
                    double distance = candidate.distSqr(center);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = candidate;
                    }
                }
            }
        }
        return best == null ? null : CustomPortalShape.findPortalRoot(level, best);
    }

    // 门内的最大内接矩形：从门形质心附近的格子展开，而不是从根（字典序最小格，
    // 对圆环/爱心多半在边角，展开出来只有 1×1，实体会被塞进门角）。
    public static BlockUtil.FoundRectangle largestRectangle(BlockGetter level, BlockPos portalPos) {
        Direction.Axis axis = level.getBlockState(portalPos).getValue(NetherPortalBlock.AXIS);
        Set<BlockPos> cells = CustomPortalShape.collectPortalCells(level, portalPos);
        BlockPos center = cells.isEmpty() ? portalPos : CustomPortalShape.centerOf(cells);
        return BlockUtil.getLargestRectangleAround(
                center, axis, 21, Direction.Axis.Y, 21,
                pos -> isPortal(level.getBlockState(pos), axis));
    }

    private static boolean isPortal(BlockState state, Direction.Axis axis) {
        return state.is(Blocks.NETHER_PORTAL) && state.getValue(NetherPortalBlock.AXIS) == axis;
    }
}
