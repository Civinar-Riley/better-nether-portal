package net.riley.betterportal.api;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalShape;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * 服务端每次建门在首次写方块前发送一次，取消后本模组不会执行本次创建的任何方块写入。
 * <p>保护模组可在 NeoForge.EVENT_BUS 订阅本事件或 {@link BlockEvent.PortalSpawnEvent}，
 * 检查 {@link #getPlannedBlocks()} 后用 {@link #setCanceled(boolean)} 拒绝创建。
 * 计划包含全部写入（门框、门格、空气清理和支撑），是只读快照，不支持通过事件变更计划。
 * {@link #getPortalSize()} 使用真实门格描述形状；监听器不应调用其 createPortalBlocks 提前写入。
 */
public final class PortalCreateEvent extends BlockEvent.PortalSpawnEvent {
    /** 创建原因：点火、复制源门、回退原版建门。 */
    public enum Reason {
        IGNITION, COPY, FALLBACK
    }

    private final Map<BlockPos, BlockState> plannedBlocks;
    private final Set<BlockPos> portalCells;
    private final Direction.Axis axis;
    private final Reason reason;

    public PortalCreateEvent(LevelAccessor level, BlockPos origin, Direction.Axis axis, Set<BlockPos> portalCells,
                             Map<BlockPos, BlockState> plannedBlocks, Reason reason) {
        this(level, origin.immutable(), new PlannedPortalShape(level, origin, axis, portalCells), plannedBlocks, reason);
    }

    private PortalCreateEvent(LevelAccessor level, BlockPos origin, PlannedPortalShape shape,
                              Map<BlockPos, BlockState> plannedBlocks, Reason reason) {
        super(level, origin, level.getBlockState(origin), shape);
        Map<BlockPos, BlockState> snapshot = new HashMap<>();
        plannedBlocks.forEach((pos, state) -> snapshot.put(pos.immutable(), state));
        this.plannedBlocks = Map.copyOf(snapshot);
        this.portalCells = shape.cells;
        this.axis = shape.axis;
        this.reason = reason;
    }

    /** 全部拟写入位置及目标状态，包含空气写入；坐标和集合均不可变。 */
    public Map<BlockPos, BlockState> getPlannedBlocks() {
        return plannedBlocks;
    }

    /** 实际门方块的位置，不包含门框、支撑或矩形包围盒内的其他位置。 */
    public Set<BlockPos> getPortalCells() {
        return portalCells;
    }

    /** 门平面的水平轴（X 或 Z）。 */
    public Direction.Axis getAxis() {
        return axis;
    }

    public Reason getReason() {
        return reason;
    }

    // 为已有 PortalSpawnEvent 监听器提供真实形状，三个操作均使用实际门格。
    private static final class PlannedPortalShape extends PortalShape {
        private final LevelAccessor level;
        private final Direction.Axis axis;
        private final Set<BlockPos> cells;

        private PlannedPortalShape(LevelAccessor level, BlockPos origin, Direction.Axis axis, Set<BlockPos> cells) {
            super(level, origin, axis);
            this.level = level;
            this.axis = axis;
            Set<BlockPos> snapshot = new HashSet<>();
            cells.forEach(pos -> snapshot.add(pos.immutable()));
            this.cells = Set.copyOf(snapshot);
        }

        @Override
        public boolean isValid() {
            return !cells.isEmpty();
        }

        @Override
        public void createPortalBlocks() {
            BlockState portal = Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, axis);
            for (BlockPos pos : cells) {
                level.setBlock(pos, portal, 18);
            }
        }

        @Override
        public boolean isComplete() {
            if (!isValid()) {
                return false;
            }
            for (BlockPos pos : cells) {
                BlockState state = level.getBlockState(pos);
                if (!state.is(Blocks.NETHER_PORTAL) || state.getValue(NetherPortalBlock.AXIS) != axis) {
                    return false;
                }
            }
            return true;
        }
    }
}
