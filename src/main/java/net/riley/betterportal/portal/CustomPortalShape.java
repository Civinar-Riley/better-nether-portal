package net.riley.betterportal.portal;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.riley.betterportal.config.PortalConfig;
import net.riley.betterportal.spec.ModTags;

// 任意形状地狱门：从点火点在竖直平面内泛洪填充内部空间，
// 要求每个内部格的平面内四邻都是传送门框架（原版 isPortalFrame ∪ 本模组 portal_frames 标签）。
// 最小门 = 1 格内径 + 上下左右 4 块框架方块；上限见 PortalConfig.PORTALS（默认 64 格 / 包围盒 21）。
// Side: BOTH（点火判定只在服务端生效，客户端不需要调用）
public final class CustomPortalShape {
    private CustomPortalShape() {
    }

    // 扫描结果：门所在平面轴 + 全部内部格
    public record Found(Direction.Axis axis, Set<BlockPos> interior) {
        // 填充门方块（flag 18 与原版 createPortalBlocks 一致：同步客户端 + 不触发邻块更新）
        public void fillPortalBlocks(LevelAccessor level) {
            BlockState portal = Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, axis);
            for (BlockPos pos : interior) {
                level.setBlock(pos, portal, 18);
            }
        }
    }

    // 点火生门：先扫 X 平面再扫 Z 平面，任一成立即可
    public static Optional<Found> findEmptyPortalShape(LevelAccessor level, BlockPos ignitionPos) {
        Optional<Found> x = scan(level, ignitionPos, Direction.Axis.X, false);
        if (x.isPresent()) {
            return x;
        }
        return scan(level, ignitionPos, Direction.Axis.Z, false);
    }

    // 邻块变化后的存活性复检：pos 处的门方块所在形状仍完整（对应原版 isComplete 语义）
    public static boolean isPortalStillValid(LevelAccessor level, BlockPos pos, Direction.Axis axis) {
        return scan(level, pos, axis, true).isPresent();
    }

    // 框架方块 = 原版 PortalShape 的扩展点（默认黑曜石，模组可覆盖）∪ 本模组数据包标签。
    // 标签让整合包无需写代码就能追加哭泣黑曜石/模组方块。
    public static boolean isFrame(BlockGetter level, BlockPos pos, BlockState state) {
        return state.is(ModTags.PORTAL_FRAMES) || state.isPortalFrame(level, pos);
    }

    // 同平面四邻：门平面的水平方向 + 上下
    static Direction[] planeDirs(Direction.Axis axis) {
        return axis == Direction.Axis.X
                ? new Direction[] { Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN }
                : new Direction[] { Direction.SOUTH, Direction.NORTH, Direction.UP, Direction.DOWN };
    }

    // 找到同一扇门里字典序最小的门方块（链接表与门形蓝图的稳定锚点）。
    // 任意形状门没有固定“左下角”，用全连通块的最小坐标作根，重建/比较都稳定。
    public static BlockPos findPortalRoot(BlockGetter level, BlockPos start) {
        if (!level.getBlockState(start).is(Blocks.NETHER_PORTAL)) {
            return start.immutable();
        }
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        BlockPos root = start.immutable();
        visited.add(root);
        queue.add(root);
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            if (comparePos(current, root) < 0) {
                root = current;
            }
            for (Direction dir : Direction.values()) {
                BlockPos next = current.relative(dir);
                if (!visited.contains(next) && level.getBlockState(next).is(Blocks.NETHER_PORTAL)) {
                    visited.add(next.immutable());
                    queue.add(next.immutable());
                }
            }
        }
        return root.immutable();
    }

    private static int comparePos(BlockPos a, BlockPos b) {
        if (a.getX() != b.getX()) {
            return Integer.compare(a.getX(), b.getX());
        }
        if (a.getY() != b.getY()) {
            return Integer.compare(a.getY(), b.getY());
        }
        return Integer.compare(a.getZ(), b.getZ());
    }

    // requireFilled=false：点火扫描，内部格允许空气/火/门方块（原版 isEmpty 口径）
    // requireFilled=true ：存活性复检，所有内部格必须已经是门方块
    private static Optional<Found> scan(LevelAccessor level, BlockPos start, Direction.Axis axis, boolean requireFilled) {
        if (axis != Direction.Axis.X && axis != Direction.Axis.Z) {
            return Optional.empty();
        }
        if (!isInteriorBlock(level.getBlockState(start), requireFilled)) {
            return Optional.empty();
        }

        int maxCells = PortalConfig.cfg(PortalConfig.PORTALS.maxInteriorCells);
        int maxSpan = PortalConfig.cfg(PortalConfig.PORTALS.maxSpan);
        Direction[] planeDirs = planeDirs(axis);
        Set<BlockPos> interior = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        interior.add(start);
        int minX = start.getX(), maxX = start.getX();
        int minY = start.getY(), maxY = start.getY();
        int minZ = start.getZ(), maxZ = start.getZ();

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (Direction dir : planeDirs) {
                BlockPos neighbor = current.relative(dir);
                if (interior.contains(neighbor)) {
                    continue;
                }
                BlockState neighborState = level.getBlockState(neighbor);
                if (isFrame(level, neighbor, neighborState)) {
                    continue;
                }
                if (!isInteriorBlock(neighborState, requireFilled)) {
                    return Optional.empty(); // 框上有缺口，形状不闭合
                }
                interior.add(neighbor);
                if (interior.size() > maxCells
                        || span(minX, neighbor.getX(), maxX) > maxSpan
                        || span(minY, neighbor.getY(), maxY) > maxSpan
                        || span(minZ, neighbor.getZ(), maxZ) > maxSpan) {
                    return Optional.empty(); // 超限：内径过大或框架泄漏
                }
                minX = Math.min(minX, neighbor.getX());
                maxX = Math.max(maxX, neighbor.getX());
                minY = Math.min(minY, neighbor.getY());
                maxY = Math.max(maxY, neighbor.getY());
                minZ = Math.min(minZ, neighbor.getZ());
                maxZ = Math.max(maxZ, neighbor.getZ());
                queue.add(neighbor);
            }
        }
        return Optional.of(new Found(axis, Set.copyOf(interior)));
    }

    private static boolean isInteriorBlock(BlockState state, boolean requireFilled) {
        if (requireFilled) {
            return state.is(Blocks.NETHER_PORTAL);
        }
        return state.isAir() || state.is(BlockTags.FIRE) || state.is(Blocks.NETHER_PORTAL);
    }

    private static int span(int a, int v, int b) {
        return Math.max(b, v) - Math.min(a, v) + 1;
    }

    // mixin 侧复用：仅主世界/下界允许生门（与原版 inPortalDimension 一致）
    public static boolean inPortalDimension(Level level) {
        return level.dimension() == Level.OVERWORLD || level.dimension() == Level.NETHER;
    }
}
