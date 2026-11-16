package net.riley.betterportal.portal;

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
import net.neoforged.neoforge.common.NeoForge;
import net.riley.betterportal.api.PortalCreateEvent;

// 所有建门路径在首次写方块前统一发事件；取消是本次创建的最终结果。
public final class PortalCreation {
    private PortalCreation() {
    }

    public static boolean allow(LevelAccessor level, BlockPos origin, Direction.Axis axis, Set<BlockPos> cells,
                                Map<BlockPos, BlockState> blocks, PortalCreateEvent.Reason reason) {
        boolean allowed = !NeoForge.EVENT_BUS.post(new PortalCreateEvent(level, origin, axis, cells, blocks, reason)).isCanceled();
        if (!allowed) {
            PortalDestinationContext.fail(PortalFailure.DENIED);
        }
        return allowed;
    }

    public static boolean ignite(LevelAccessor level, BlockPos origin, CustomPortalShape.Found shape) {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        BlockState portal = Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, shape.axis());
        shape.interior().forEach(pos -> blocks.put(pos, portal));
        return allow(level, origin, shape.axis(), shape.interior(), blocks, PortalCreateEvent.Reason.IGNITION);
    }

    public static boolean allowFallback(LevelAccessor level, BlockPos root, Direction.Axis axis, boolean platform) {
        Direction along = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        Direction across = along.getClockWise();
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        Set<BlockPos> cells = new HashSet<>();
        BlockState frame = Blocks.OBSIDIAN.defaultBlockState();
        if (platform) {
            for (int side = -1; side <= 1; side++) {
                for (int column = 0; column < 2; column++) {
                    for (int y = -1; y < 3; y++) {
                        blocks.put(root.relative(along, column).relative(across, side).above(y),
                                y < 0 ? frame : Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        for (int column = -1; column < 3; column++) {
            for (int y = -1; y < 4; y++) {
                if (column == -1 || column == 2 || y == -1 || y == 3) {
                    blocks.put(root.relative(along, column).above(y), frame);
                }
            }
        }
        BlockState portal = Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, axis);
        for (int column = 0; column < 2; column++) {
            for (int y = 0; y < 3; y++) {
                BlockPos pos = root.relative(along, column).above(y);
                cells.add(pos);
                blocks.put(pos, portal);
            }
        }
        return allow(level, root, axis, cells, blocks, PortalCreateEvent.Reason.FALLBACK);
    }
}
