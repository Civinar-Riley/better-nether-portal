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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 异形门出口：保留原版相对位置/朝向公式，按实体尺寸选内接矩形并验证实际落点。
// Side: SERVER
public final class PortalTravel {
    private PortalTravel() {
    }

    public static DimensionTransition toLinkedPortal(ServerLevel target, Entity entity, BlockPos sourcePos, BlockPos targetRoot) {
        return toPortal(target, entity, sourcePos, targetRoot,
                DimensionTransition.PLAY_PORTAL_SOUND.then(e -> e.placePortalTicket(targetRoot)));
    }

    // 不存在或无法安全容纳实体的出口返回 null，实体留在源维度。
    public static DimensionTransition toPortal(ServerLevel target, Entity entity, BlockPos sourcePos, BlockPos targetRoot,
                                               DimensionTransition.PostDimensionTransition postTransition) {
        BlockState targetState = target.getBlockState(targetRoot);
        if (!targetState.is(Blocks.NETHER_PORTAL) || !target.getWorldBorder().isWithinBounds(targetRoot)) {
            return null;
        }
        EntityDimensions dimensions = entity.getDimensions(entity.getPose());
        Set<BlockPos> cells = CustomPortalShape.collectPortalCells(target, targetRoot);
        Direction.Axis exitAxis = targetState.getValue(NetherPortalBlock.AXIS);
        BlockUtil.FoundRectangle targetRect = largestRectangle(cells, exitAxis, dimensions.width(), dimensions.height());
        BlockState sourceState = entity.level().getBlockState(sourcePos);
        Direction.Axis sourceAxis = sourceState.getOptionalValue(NetherPortalBlock.AXIS).orElse(Direction.Axis.X);
        Vec3 offset = new Vec3(0.5, 0.0, 0.0);
        if (sourceState.is(Blocks.NETHER_PORTAL)) {
            BlockUtil.FoundRectangle sourceRect = BlockUtil.getLargestRectangleAround(
                    sourcePos, sourceAxis, 21, Direction.Axis.Y, 21,
                    pos -> entity.level().getBlockState(pos) == sourceState);
            offset = entity.getRelativePortalPosition(sourceAxis, sourceRect);
        }

        BlockPos min = targetRect.minCorner;
        int rotation = sourceAxis == exitAxis ? 0 : 90;
        Vec3 speed = sourceAxis == exitAxis
                ? entity.getDeltaMovement()
                : new Vec3(entity.getDeltaMovement().z, entity.getDeltaMovement().y, -entity.getDeltaMovement().x);
        double offsetRight = dimensions.width() / 2.0 + (targetRect.axis1Size - dimensions.width()) * offset.x();
        double offsetUp = (targetRect.axis2Size - dimensions.height()) * offset.y();
        double offsetForward = 0.5 + offset.z();
        boolean xAligned = exitAxis == Direction.Axis.X;
        Vec3 targetPos = new Vec3(
                min.getX() + (xAligned ? offsetRight : offsetForward),
                min.getY() + offsetUp,
                min.getZ() + (xAligned ? offsetForward : offsetRight));
        Vec3 collisionFree = PortalShape.findCollisionFreePosition(targetPos, target, entity, dimensions);
        if (!isClear(target, entity, dimensions, collisionFree)) {
            collisionFree = findSideExit(target, entity, dimensions, cells, exitAxis, targetPos);
            if (collisionFree == null) {
                return null;
            }
        }
        return new DimensionTransition(target, collisionFree, speed, entity.getYRot() + rotation, entity.getXRot(), postTransition);
    }

    private static boolean isClear(ServerLevel level, Entity entity, EntityDimensions dimensions, Vec3 pos) {
        AABB bounds = dimensions.makeBoundingBox(pos);
        return bounds.minY >= level.getMinBuildHeight() && bounds.maxY <= level.getMaxBuildHeight()
                && level.getWorldBorder().isWithinBounds(bounds)
                && level.noCollision(entity, bounds) && !level.containsAnyLiquid(bounds);
    }

    // 1 格高的门无法容纳站立玩家；只在门前后找有支撑的空位，不破坏框架或周围建筑。
    private static Vec3 findSideExit(ServerLevel level, Entity entity, EntityDimensions dimensions,
                                     Set<BlockPos> cells, Direction.Axis axis, Vec3 preferred) {
        Vec3 best = null;
        double bestDistance = Double.MAX_VALUE;
        double sideOffset = 0.5 + dimensions.width() / 2.0 + 0.01;
        for (BlockPos cell : cells) {
            for (int side : new int[] { -1, 1 }) {
                for (int dy = -1; dy <= 1; dy++) {
                    Vec3 candidate = new Vec3(cell.getX() + 0.5 + (axis == Direction.Axis.Z ? side * sideOffset : 0),
                            cell.getY() + dy, cell.getZ() + 0.5 + (axis == Direction.Axis.X ? side * sideOffset : 0));
                    if (!isClear(level, entity, dimensions, candidate)) {
                        continue;
                    }
                    AABB feet = dimensions.makeBoundingBox(candidate).move(0, -0.05, 0);
                    BlockState floor = level.getBlockState(BlockPos.containing(candidate.add(0, -0.05, 0)));
                    if (level.noCollision(entity, feet) || floor.is(Blocks.MAGMA_BLOCK)
                            || floor.is(Blocks.CAMPFIRE) || floor.is(Blocks.SOUL_CAMPFIRE)) {
                        continue;
                    }
                    double distance = candidate.distanceToSqr(preferred);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = candidate;
                    }
                }
            }
        }
        return best;
    }

    public static BlockUtil.FoundRectangle largestRectangle(BlockGetter level, BlockPos portalPos) {
        Direction.Axis axis = level.getBlockState(portalPos).getValue(NetherPortalBlock.AXIS);
        return largestRectangle(CustomPortalShape.collectPortalCells(level, portalPos), axis, 0, 0);
    }

    // 按行带枚举矩形；最多 21×21 格，无需从每一格重复查询世界方块。
    private static BlockUtil.FoundRectangle largestRectangle(Set<BlockPos> cells, Direction.Axis axis, float width, float height) {
        BlockPos center = CustomPortalShape.centerOf(cells);
        int minH = Integer.MAX_VALUE, maxH = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (BlockPos cell : cells) {
            int horizontal = axis == Direction.Axis.X ? cell.getX() : cell.getZ();
            minH = Math.min(minH, horizontal);
            maxH = Math.max(maxH, horizontal);
            minY = Math.min(minY, cell.getY());
            maxY = Math.max(maxY, cell.getY());
        }
        BlockUtil.FoundRectangle best = null;
        boolean bestFits = false;
        for (int bottom = minY; bottom <= maxY; bottom++) {
            boolean[] columns = new boolean[maxH - minH + 1];
            java.util.Arrays.fill(columns, true);
            for (int top = bottom; top <= maxY; top++) {
                int start = minH;
                for (int horizontal = minH; horizontal <= maxH + 1; horizontal++) {
                    if (horizontal <= maxH) {
                        BlockPos pos = axis == Direction.Axis.X
                                ? new BlockPos(horizontal, top, center.getZ()) : new BlockPos(center.getX(), top, horizontal);
                        columns[horizontal - minH] &= cells.contains(pos);
                        if (columns[horizontal - minH]) {
                            continue;
                        }
                    }
                    if (horizontal > start) {
                        BlockPos min = axis == Direction.Axis.X
                                ? new BlockPos(start, bottom, center.getZ()) : new BlockPos(center.getX(), bottom, start);
                        BlockUtil.FoundRectangle rectangle = new BlockUtil.FoundRectangle(min, horizontal - start, top - bottom + 1);
                        boolean fits = rectangle.axis1Size >= width && rectangle.axis2Size >= height;
                        if (best == null || (fits && !bestFits) || (fits == bestFits && betterRectangle(rectangle, best, center))) {
                            best = rectangle;
                            bestFits = fits;
                        }
                    }
                    start = horizontal + 1;
                }
            }
        }
        return best;
    }

    private static boolean betterRectangle(BlockUtil.FoundRectangle candidate, BlockUtil.FoundRectangle best, BlockPos center) {
        int area = candidate.axis1Size * candidate.axis2Size;
        int bestArea = best.axis1Size * best.axis2Size;
        if (area != bestArea) {
            return area > bestArea;
        }
        double distance = candidate.minCorner.distSqr(center);
        double bestDistance = best.minCorner.distSqr(center);
        return distance < bestDistance || (distance == bestDistance && candidate.minCorner.compareTo(best.minCorner) < 0);
    }
}
