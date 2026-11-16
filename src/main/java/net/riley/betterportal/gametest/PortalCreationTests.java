package net.riley.betterportal.gametest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.riley.betterportal.BetterNetherPortalMod;
import net.riley.betterportal.api.PortalCreateEvent;
import net.riley.betterportal.config.PortalConfig;
import net.riley.betterportal.portal.PortalDestinationContext;

@GameTestHolder(BetterNetherPortalMod.MODID)
@PrefixGameTestTemplate(false)
public final class PortalCreationTests {
    private PortalCreationTests() {
    }

    public static void register(RegisterGameTestsEvent event) {
        event.register(PortalCreationTests.class);
    }

    @GameTest(template = "portal_platform")
    public static void parentListenerControlsIrregularAndRectangleIgnition(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (boolean rectangle : new boolean[] { false, true }) {
            BlockPos root = helper.absolutePos(new BlockPos(rectangle ? 10 : 4, 3, 5));
            Set<BlockPos> cells = new HashSet<>(Set.of(root, root.east(), root.above()));
            if (rectangle) {
                cells.addAll(Set.of(root.east().above(), root.above(2), root.east().above(2)));
            }
            frame(level, cells, Direction.Axis.X);
            for (boolean cancel : new boolean[] { true, false }) {
                cells.forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 18));
                Map<BlockPos, BlockState> before = new HashMap<>();
                cells.forEach(pos -> before.put(pos, Blocks.AIR.defaultBlockState()));
                // 放火发生在 onPlace 事件之前；拒绝建门仍允许原来的火留在框内。
                before.put(root, Blocks.FIRE.defaultBlockState());
                PortalCreateEvent event = observe(helper, level, PortalCreateEvent.Reason.IGNITION,
                        Direction.Axis.X, cancel, before,
                        () -> level.setBlockAndUpdate(root, Blocks.FIRE.defaultBlockState()));
                helper.assertTrue(event.getPortalCells().equals(cells), "ignition must report the actual interior");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "portal_platform")
    public static void copiedPortalCancellationIsFinalAndAllowedPlanMatches(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = source(helper, Direction.Axis.Z);
        BlockPos target = target(helper);
        boolean oldCopy = PortalConfig.PORTALS.copyShapeToDestination.get();
        try (Fixture fixture = new Fixture(level)) {
            PortalConfig.PORTALS.copyShapeToDestination.set(true);
            fixture.fill(target.offset(-2, -2, -2), target.offset(2, 4, 2), Blocks.AIR.defaultBlockState());
            fixture.fill(target.below(2), target.below(2), Blocks.STONE.defaultBlockState());
            Map<BlockPos, BlockState> before = snapshot(level, target.offset(-2, -2, -2), target.offset(2, 4, 2));
            for (boolean cancel : new boolean[] { true, false }) {
                PortalDestinationContext.Source context = new PortalDestinationContext.Source(level, source, source);
                PortalDestinationContext.push(context);
                PortalCreateEvent event = observe(helper, level, PortalCreateEvent.Reason.COPY,
                        Direction.Axis.Z, cancel, before, () -> {
                            var result = level.getPortalForcer().createPortal(target, Direction.Axis.Z);
                            helper.assertTrue(result.isEmpty() == cancel, "copy cancellation must return empty without fallback");
                            helper.assertTrue(context.denied() == cancel, "copy refusal must reach the destination context");
                        });
                helper.assertTrue(event.getPortalCells().equals(Set.of(target)), "copy must preserve the one-cell source");
            }
        } finally {
            PortalDestinationContext.clear();
            PortalConfig.PORTALS.copyShapeToDestination.set(oldCopy);
        }
        helper.succeed();
    }

    @GameTest(template = "portal_platform")
    public static void ordinaryFallbackChecksBeforeFrameWrites(GameTestHelper helper) {
        fallback(helper, false);
        helper.succeed();
    }

    @GameTest(template = "portal_platform")
    public static void forcedFallbackChecksBeforePlatformWrites(GameTestHelper helper) {
        fallback(helper, true);
        helper.succeed();
    }

    private static void fallback(GameTestHelper helper, boolean platform) {
        ServerLevel level = helper.getLevel();
        BlockPos source = source(helper, Direction.Axis.X);
        BlockPos target = target(helper);
        WorldBorder border = level.getWorldBorder();
        double oldX = border.getCenterX(), oldZ = border.getCenterZ(), oldSize = border.getSize();
        boolean oldCopy = PortalConfig.PORTALS.copyShapeToDestination.get();
        try (Fixture fixture = new Fixture(level)) {
            fixture.fill(target.offset(-1, -1, -1), target.offset(2, 3, 1), Blocks.AIR.defaultBlockState());
            if (platform) {
                // 边界仅容两列门格；四根实心柱封住所有允许搜索的列，强制走平台分支。
                border.setCenter(target.getX() + 1, target.getZ() + 1);
                border.setSize(2);
                fixture.fill(new BlockPos(target.getX(), level.getMinBuildHeight(), target.getZ()),
                        new BlockPos(target.getX() + 1, level.getMaxBuildHeight() - 1, target.getZ() + 1),
                        Blocks.STONE.defaultBlockState());
            } else {
                // 高空静态平地令目标点距离为零，不依赖随机生成的地形。
                fixture.fill(target.offset(-1, -1, -1), target.offset(2, -1, 1), Blocks.STONE.defaultBlockState());
            }
            Map<BlockPos, BlockState> before = snapshot(level, target.offset(-1, -1, -1), target.offset(2, 3, 1));
            for (int attempt = 0; attempt < 3; attempt++) {
                boolean cancel = attempt < 2;
                PortalConfig.PORTALS.copyShapeToDestination.set(attempt != 0);
                if (attempt == 0) {
                    PortalDestinationContext.push(new PortalDestinationContext.Source(level, source, source));
                } else {
                    PortalDestinationContext.clear();
                }
                PortalCreateEvent event = observe(helper, level, PortalCreateEvent.Reason.FALLBACK,
                        Direction.Axis.X, cancel, before, () -> {
                            var result = level.getPortalForcer().createPortal(target, Direction.Axis.X);
                            helper.assertTrue(result.isEmpty() == cancel, "fallback cancellation must return empty");
                            if (!cancel) {
                                var rectangle = result.orElseThrow();
                                helper.assertTrue(rectangle.minCorner.equals(target)
                                        && rectangle.axis1Size == 2 && rectangle.axis2Size == 3,
                                        "fallback fixture must select the expected 2x3 portal");
                            }
                        });
                helper.assertTrue(event.getPortalCells().size() == 6, "fallback must report six portal cells");
                helper.assertTrue(event.getPlannedBlocks().values().stream().anyMatch(BlockState::isAir) == platform,
                        "fixture must exercise the requested ordinary/platform branch");
                helper.assertTrue(event.getPlannedBlocks().size() == (platform ? 36 : 20),
                        "fallback plan must include the entire frame and any platform/clearance");
            }
        } finally {
            PortalDestinationContext.clear();
            PortalConfig.PORTALS.copyShapeToDestination.set(oldCopy);
            border.setCenter(oldX, oldZ);
            border.setSize(oldSize);
        }
    }

    private static PortalCreateEvent observe(GameTestHelper helper, ServerLevel level, PortalCreateEvent.Reason reason,
            Direction.Axis axis, boolean cancel, Map<BlockPos, BlockState> before, Runnable create) {
        List<PortalCreateEvent> events = new ArrayList<>();
        Consumer<BlockEvent.PortalSpawnEvent> listener = parent -> {
            if (parent.getLevel() != level) {
                return;
            }
            helper.assertTrue(parent instanceof PortalCreateEvent, "parent listener must receive the extended creation event");
            PortalCreateEvent event = (PortalCreateEvent) parent;
            events.add(event);
            helper.assertTrue(event.getReason() == reason, "creation must not retry or fall back after refusal");
            helper.assertTrue(event.getAxis() == axis && event.getPortalSize().isValid(), "event must expose a valid actual shape");
            helper.assertTrue(!event.getPortalCells().isEmpty(), "creation must include portal cells");
            for (BlockPos pos : event.getPlannedBlocks().keySet()) {
                helper.assertTrue(before.containsKey(pos), "planned position escaped the fixture: " + pos);
                helper.assertTrue(level.getBlockState(pos).equals(before.get(pos)), "event must precede every write: " + pos);
            }
            for (BlockPos cell : event.getPortalCells()) {
                helper.assertTrue(Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, axis)
                        .equals(event.getPlannedBlocks().get(cell)), "portal cell must have its planned axis");
            }
            readOnly(helper, () -> event.getPlannedBlocks().put(event.getPos(), Blocks.AIR.defaultBlockState()));
            readOnly(helper, () -> event.getPlannedBlocks().entrySet().iterator().next().setValue(Blocks.AIR.defaultBlockState()));
            readOnly(helper, () -> event.getPortalCells().clear());
            if (cancel) {
                parent.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            create.run();
            helper.assertTrue(events.size() == 1, "one public creation call must emit exactly one event, got " + events.size());
            PortalCreateEvent event = events.getFirst();
            event.getPlannedBlocks().forEach((pos, state) -> helper.assertTrue(
                    level.getBlockState(pos).equals(cancel ? before.get(pos) : state),
                    (cancel ? "canceled creation changed " : "accepted plan disagrees at ") + pos));
            helper.assertTrue(event.getPortalSize().isComplete() != cancel, "event shape must reflect the final portal");
            return event;
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
    }

    private static void readOnly(GameTestHelper helper, Runnable mutate) {
        try {
            mutate.run();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        helper.assertTrue(false, "creation plan and portal cells must be immutable");
    }

    private static BlockPos source(GameTestHelper helper, Direction.Axis axis) {
        BlockPos root = helper.absolutePos(new BlockPos(5, 3, 5));
        frame(helper.getLevel(), Set.of(root), axis);
        helper.getLevel().setBlock(root, Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, axis), 18);
        return root;
    }

    private static void frame(ServerLevel level, Set<BlockPos> cells, Direction.Axis axis) {
        Direction along = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        for (BlockPos cell : cells) {
            for (Direction direction : new Direction[] { along, along.getOpposite(), Direction.UP, Direction.DOWN }) {
                BlockPos neighbor = cell.relative(direction);
                if (!cells.contains(neighbor)) {
                    level.setBlock(neighbor, Blocks.OBSIDIAN.defaultBlockState(), 18);
                }
            }
        }
    }

    private static BlockPos target(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(10, 0, 10));
        return new BlockPos(origin.getX(), helper.getLevel().getMaxBuildHeight() - 20, origin.getZ());
    }

    private static Map<BlockPos, BlockState> snapshot(ServerLevel level, BlockPos min, BlockPos max) {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        BlockPos.betweenClosed(min, max).forEach(pos -> blocks.put(pos.immutable(), level.getBlockState(pos)));
        return blocks;
    }

    private static final class Fixture implements AutoCloseable {
        private final ServerLevel level;
        private final Map<BlockPos, BlockState> original = new HashMap<>();

        private Fixture(ServerLevel level) {
            this.level = level;
        }

        private void fill(BlockPos min, BlockPos max, BlockState state) {
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                original.putIfAbsent(pos.immutable(), level.getBlockState(pos));
                level.setBlock(pos, state, 18);
            }
        }

        @Override
        public void close() {
            original.forEach((pos, state) -> level.setBlock(pos, state, 18));
        }
    }
}
