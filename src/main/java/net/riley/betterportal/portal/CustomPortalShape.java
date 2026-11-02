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
    // 一扇门的门方块连通块硬上限：配置允许的最大门（21×21）
    private static final int MAX_PORTAL_CELLS = 21 * 21;

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

    // 收集与 start 同一扇门的全部门方块：只走门平面内四邻、只认同轴门方块，
    // 前后贴着或 X/Z 垂直相接的两扇门不会串成一扇。超过 MAX_PORTAL_CELLS 即停（防 /fill 大片门方块）。
    public static Set<BlockPos> collectPortalCells(BlockGetter level, BlockPos start) {
        BlockState origin = level.getBlockState(start);
        if (!origin.is(Blocks.NETHER_PORTAL)) {
            return Set.of();
        }
        Direction.Axis axis = origin.getValue(NetherPortalBlock.AXIS);
        Direction[] dirs = planeDirs(axis);
        Set<BlockPos> cells = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        BlockPos first = start.immutable();
        cells.add(first);
        queue.add(first);
        while (!queue.isEmpty() && cells.size() <= MAX_PORTAL_CELLS) {
            BlockPos current = queue.poll();
            for (Direction dir : dirs) {
                BlockPos next = current.relative(dir);
                if (!cells.contains(next) && isPortal(level.getBlockState(next), axis)) {
                    cells.add(next);
                    queue.add(next);
                }
            }
        }
        return cells;
    }

    // 找到同一扇门里字典序最小的门方块（链接表与门形蓝图的稳定锚点）。
    // 任意形状门没有固定“左下角”，用全连通块的最小坐标作根，重建/比较都稳定。
    public static BlockPos findPortalRoot(BlockGetter level, BlockPos start) {
        Set<BlockPos> cells = collectPortalCells(level, start);
        return cells.isEmpty() ? start.immutable() : rootOf(cells);
    }

    public static BlockPos rootOf(Set<BlockPos> cells) {
        BlockPos root = null;
        for (BlockPos pos : cells) {
            if (root == null || comparePos(pos, root) < 0) {
                root = pos;
            }
        }
        return root;
    }

    // 离门形质心最近的门格（并列取字典序最小，保证确定性）；异形门找内接矩形的起点。
    public static BlockPos centerOf(Set<BlockPos> cells) {
        double cx = 0, cy = 0, cz = 0;
        for (BlockPos pos : cells) {
            cx += pos.getX();
            cy += pos.getY();
            cz += pos.getZ();
        }
        cx /= cells.size();
        cy /= cells.size();
        cz /= cells.size();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : cells) {
            double dx = pos.getX() - cx, dy = pos.getY() - cy, dz = pos.getZ() - cz;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance || (distance == bestDistance && comparePos(pos, best) < 0)) {
                bestDistance = distance;
                best = pos;
            }
        }
        return best;
    }

    private static boolean isPortal(BlockState state, Direction.Axis axis) {
        return state.is(Blocks.NETHER_PORTAL) && state.getValue(NetherPortalBlock.AXIS) == axis;
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
        // 廉价预检：起点沿平面四向直线走，maxSpan 格内必须撞到框架；
        // 否则泛洪必然泄漏/超限。开阔地放火（火焰蔓延）在这里就被挡掉，不跑整轮泛洪。
        for (Direction dir : planeDirs) {
            if (!rayHitsFrame(level, start, dir, maxSpan, requireFilled)) {
                return Optional.empty();
            }
        }
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

    private static boolean rayHitsFrame(LevelAccessor level, BlockPos start, Direction dir, int maxSpan, boolean requireFilled) {
        BlockPos.MutableBlockPos cursor = start.mutable();
        for (int step = 1; step <= maxSpan; step++) {
            cursor.move(dir);
            BlockState state = level.getBlockState(cursor);
            if (isFrame(level, cursor, state)) {
                return true;
            }
            if (!isInteriorBlock(state, requireFilled)) {
                return false;
            }
        }
        return false;
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
