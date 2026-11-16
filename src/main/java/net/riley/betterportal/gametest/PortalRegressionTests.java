package net.riley.betterportal.gametest;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.riley.betterportal.BetterNetherPortalMod;
import net.riley.betterportal.config.PortalConfig;
import net.riley.betterportal.portal.CustomPortalShape;
import net.riley.betterportal.portal.PortalDestinationContext;
import net.riley.betterportal.portal.PortalLinkData;
import net.riley.betterportal.portal.PortalTravel;

@GameTestHolder(BetterNetherPortalMod.MODID)
@PrefixGameTestTemplate(false)
public final class PortalRegressionTests {
    private PortalRegressionTests() {
    }

    public static void register(RegisterGameTestsEvent event) {
        event.register(PortalRegressionTests.class);
    }

    private static void buildPortal(ServerLevel level, Set<BlockPos> cells, Direction.Axis axis) {
        Direction horizontal = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        for (BlockPos cell : cells) {
            for (Direction direction : new Direction[] { horizontal, horizontal.getOpposite(), Direction.UP, Direction.DOWN }) {
                if (!cells.contains(cell.relative(direction))) {
                    level.setBlock(cell.relative(direction), Blocks.OBSIDIAN.defaultBlockState(), 18);
                }
            }
        }
        BlockState portal = Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, axis);
        cells.forEach(pos -> level.setBlock(pos, portal, 18));
    }

    private static Set<BlockPos> rectangle(BlockPos root, int width, int height) {
        Set<BlockPos> cells = new HashSet<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                cells.add(root.offset(x, y, 0));
            }
        }
        return cells;
    }

    @GameTest(template = "portal_platform")
    public static void configuredLimitsAlsoApplyToObsidian(GameTestHelper helper) {
        int oldCells = PortalConfig.PORTALS.maxInteriorCells.get();
        int oldSpan = PortalConfig.PORTALS.maxSpan.get();
        try {
            PortalConfig.PORTALS.maxInteriorCells.set(1);
            PortalConfig.PORTALS.maxSpan.set(1);
            BlockPos small = helper.absolutePos(new BlockPos(3, 3, 5));
            buildPortal(helper.getLevel(), Set.of(small), Direction.Axis.X);
            helper.getLevel().setBlock(small, Blocks.AIR.defaultBlockState(), 18);
            helper.getLevel().setBlockAndUpdate(small, Blocks.FIRE.defaultBlockState());
            helper.assertTrue(helper.getLevel().getBlockState(small).is(Blocks.NETHER_PORTAL), "one cell must fit limit");
            BlockPos root = helper.absolutePos(new BlockPos(8, 3, 5));
            Set<BlockPos> cells = rectangle(root, 2, 3);
            buildPortal(helper.getLevel(), cells, Direction.Axis.X);
            cells.forEach(pos -> helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 18));
            helper.getLevel().setBlockAndUpdate(root, Blocks.FIRE.defaultBlockState());
            helper.assertTrue(!helper.getLevel().getBlockState(root).is(Blocks.NETHER_PORTAL),
                    "vanilla rectangle must not bypass configured cell/span limits");
            helper.assertTrue(helper.getLevel().getPortalForcer().createPortal(root.east(30), Direction.Axis.X).isEmpty(),
                    "destination fallback must obey the same limits");
        } finally {
            PortalConfig.PORTALS.maxInteriorCells.set(oldCells);
            PortalConfig.PORTALS.maxSpan.set(oldSpan);
        }
        helper.succeed();
    }

    @GameTest(template = "portal_platform")
    public static void crossingPortalDoesNotOverwriteAxis(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos root = helper.absolutePos(new BlockPos(5, 3, 5));
        Set<BlockPos> xCells = rectangle(root, 2, 3);
        Set<BlockPos> zCells = new HashSet<>();
        for (int z = -1; z <= 1; z++) {
            for (int y = 0; y < 3; y++) {
                zCells.add(root.offset(1, y, z));
            }
        }
        buildPortal(level, zCells, Direction.Axis.Z);
        zCells.forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 18));
        buildPortal(level, xCells, Direction.Axis.X);
        level.setBlockAndUpdate(root.offset(1, 0, 1), Blocks.FIRE.defaultBlockState());
        for (BlockPos pos : xCells) {
            helper.assertTrue(level.getBlockState(pos).is(Blocks.NETHER_PORTAL)
                    && level.getBlockState(pos).getValue(NetherPortalBlock.AXIS) == Direction.Axis.X,
                    "crossing ignition must preserve the original portal axis");
        }
        helper.succeed();
    }

    @GameTest(template = "portal_platform")
    public static void largestRectangleAvoidsNarrowBridge(GameTestHelper helper) {
        BlockPos root = helper.absolutePos(new BlockPos(2, 3, 5));
        Set<BlockPos> cells = rectangle(root, 3, 3);
        cells.addAll(rectangle(root.east(8), 3, 3));
        for (int x = 3; x < 8; x++) {
            cells.add(root.offset(x, 1, 0));
        }
        buildPortal(helper.getLevel(), cells, Direction.Axis.X);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(root.getX() + 1.5, root.getY(), root.getZ() + 0.5);
        DimensionTransition transition = PortalTravel.toLinkedPortal(helper.getLevel(), player, root, root);
        helper.assertTrue(transition != null, "wide rooms must provide a player exit");
        helper.assertTrue(helper.getLevel().noCollision(player, player.getDimensions(player.getPose()).makeBoundingBox(transition.pos())),
                "narrow bridge must not be chosen over a room that fits the player");
        helper.succeed();
    }

    @GameTest(template = "portal_platform")
    public static void smallPortalHasCollisionFreePlayerExit(GameTestHelper helper) {
        BlockPos root = helper.absolutePos(new BlockPos(5, 3, 5));
        buildPortal(helper.getLevel(), Set.of(root), Direction.Axis.X);
        helper.getLevel().setBlockAndUpdate(root.north().below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(root.south().below(), Blocks.STONE.defaultBlockState());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atBottomCenterOf(root));
        DimensionTransition transition = PortalTravel.toLinkedPortal(helper.getLevel(), player, root, root);
        helper.assertTrue(transition != null, "small portal should use the supported side exit");
        helper.assertTrue(helper.getLevel().noCollision(player, player.getDimensions(player.getPose()).makeBoundingBox(transition.pos())),
                "standing player must not intersect the top frame");
        helper.succeed();
    }

    @GameTest(template = "portal_platform")
    public static void blockedSmallPortalCancelsTravel(GameTestHelper helper) {
        BlockPos root = helper.absolutePos(new BlockPos(5, 3, 5));
        buildPortal(helper.getLevel(), Set.of(root), Direction.Axis.X);
        for (BlockPos pos : BlockPos.betweenClosed(root.offset(-2, -2, -2), root.offset(2, 3, 2))) {
            if (!pos.equals(root)) {
                helper.getLevel().setBlock(pos, Blocks.OBSIDIAN.defaultBlockState(), 18);
            }
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atBottomCenterOf(root));
        helper.assertTrue(PortalTravel.toLinkedPortal(helper.getLevel(), player, root, root) == null,
                "no collision-free exit must cancel travel");
        helper.succeed();
    }

    @GameTest(template = "portal_platform")
    public static void occupiedClosestPortalDoesNotHideFreePortal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos occupied = helper.absolutePos(new BlockPos(4, 3, 5));
        BlockPos free = helper.absolutePos(new BlockPos(9, 3, 5));
        BlockPos owner = helper.absolutePos(new BlockPos(13, 3, 5));
        BlockPos source = helper.absolutePos(new BlockPos(7, 3, 10));
        for (BlockPos root : new BlockPos[] { occupied, free, owner, source }) {
            buildPortal(level, Set.of(root), Direction.Axis.X);
        }
        PortalLinkData links = PortalLinkData.get(level.getServer());
        links.link(level.dimension(), occupied, level.dimension(), owner);
        // POI 更新在当前 tick 结束时处理。
        helper.runAfterDelay(2, () -> {
            try {
                PortalDestinationContext.push(new PortalDestinationContext.Source(level, source, source));
                var result = level.getPortalForcer().findClosestPortalPosition(occupied, true, level.getWorldBorder());
                helper.assertTrue(result.isPresent() && CustomPortalShape.findPortalRoot(level, result.get()).equals(free),
                        "second nearest free portal must be reused: " + result);
            } finally {
                PortalDestinationContext.clear();
                links.unlink(level.dimension(), occupied);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "portal_platform")
    public static void linkedExitRespectsWorldBorder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos root = helper.absolutePos(new BlockPos(5, 3, 5));
        buildPortal(level, rectangle(root, 2, 3), Direction.Axis.X);
        WorldBorder border = level.getWorldBorder();
        double oldX = border.getCenterX(), oldZ = border.getCenterZ(), oldSize = border.getSize();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atBottomCenterOf(root));
        try {
            border.setCenter(root.getX() + 20, root.getZ());
            border.setSize(8);
            helper.assertTrue(PortalTravel.toLinkedPortal(level, player, root, root) == null,
                    "linked portal outside the new border must be rejected");
        } finally {
            border.setCenter(oldX, oldZ);
            border.setSize(oldSize);
        }
        helper.succeed();
    }

    @GameTest(template = "portal_platform", timeoutTicks = 200)
    public static void itemTravelLinksSelectedPortalAfterArrival(GameTestHelper helper) {
        ServerLevel sourceLevel = helper.getLevel();
        ServerLevel target = sourceLevel.getServer().getLevel(Level.NETHER);
        BlockPos anchor = helper.absolutePos(new BlockPos(5, 3, 5));
        BlockPos selected = new BlockPos(Math.floorDiv(anchor.getX(), 8) + 64, 200, Math.floorDiv(anchor.getZ(), 8) + 64);
        BlockPos adjacent = selected.north();
        BlockPos source = new BlockPos(selected.getX() * 8, 200, selected.getZ() * 8 + 1);
        buildPortal(sourceLevel, rectangle(source, 2, 3), Direction.Axis.X);
        buildPortal(target, rectangle(selected, 2, 3), Direction.Axis.X);
        buildPortal(target, rectangle(adjacent, 2, 3), Direction.Axis.X);
        ChunkPos destinationChunk = new ChunkPos(selected);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                target.getChunk(destinationChunk.x + dx, destinationChunk.z + dz);
            }
        }
        PortalLinkData links = PortalLinkData.get(sourceLevel.getServer());
        links.unlink(sourceLevel.dimension(), source);
        links.unlink(target.dimension(), selected);
        links.unlink(target.dimension(), adjacent);
        helper.runAfterDelay(2, () -> {
            ItemEntity item = new ItemEntity(sourceLevel, source.getX() + 0.5, source.getY() + 0.25,
                    source.getZ() - 0.05, new ItemStack(Items.STONE));
            try {
                DimensionTransition transition = ((NetherPortalBlock) Blocks.NETHER_PORTAL).getPortalDestination(sourceLevel, item, source);
                helper.assertTrue(transition != null, "existing target portal should produce a transition");
                helper.assertTrue(PortalDestinationContext.peek() == null, "destination context must be cleared");
                helper.assertTrue(links.getLinkedRef(sourceLevel.dimension(), source).isEmpty(),
                        "merely calculating a destination must not reserve its portal");
                var arrived = item.changeDimension(transition);
                target.getChunkSource().removeRegionTicket(TicketType.PORTAL, new ChunkPos(selected), 3, selected);
                helper.assertTrue(arrived != null, "item should arrive in the nether");
                helper.assertTrue(links.getLinkedPos(sourceLevel.dimension(), source, target.dimension()).filter(selected::equals).isPresent(),
                        "link must use selected portal despite item offset towards the adjacent portal");
                helper.assertTrue(links.getLinkedRef(target.dimension(), adjacent).isEmpty(), "adjacent portal must stay unlinked");
                arrived.discard();
                DimensionTransition linked = ((NetherPortalBlock) Blocks.NETHER_PORTAL).getPortalDestination(sourceLevel,
                        new ItemEntity(sourceLevel, source.getX() + 0.5, source.getY() + 0.25, source.getZ() + 0.5, new ItemStack(Items.STONE)), source);
                helper.assertTrue(linked != null && PortalDestinationContext.peek() == null, "linked return must clear its context");
            } finally {
                links.unlink(sourceLevel.dimension(), source);
                item.discard();
                for (BlockPos root : new BlockPos[] { selected, adjacent }) {
                    for (BlockPos pos : BlockPos.betweenClosed(root.offset(-1, -1, 0), root.offset(2, 3, 0))) {
                        target.setBlock(pos, Blocks.AIR.defaultBlockState(), 18);
                    }
                }
                for (BlockPos pos : BlockPos.betweenClosed(source.offset(-1, -1, 0), source.offset(2, 3, 0))) {
                    sourceLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), 18);
                }
            }
            // 成功传送会提交区块票；让目标维度完成区块加载后再结束服务器。
            helper.runAfterDelay(40, helper::succeed);
        });
    }

    @GameTest(template = "portal_platform")
    public static void expandedPortalCanReuseItsPartner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(4, 3, 10));
        BlockPos oldRoot = source.east();
        BlockPos partner = helper.absolutePos(new BlockPos(9, 3, 5));
        buildPortal(level, rectangle(source, 2, 3), Direction.Axis.X);
        buildPortal(level, rectangle(partner, 2, 3), Direction.Axis.X);
        PortalLinkData links = PortalLinkData.get(level.getServer());
        links.link(level.dimension(), oldRoot, level.dimension(), partner);
        helper.runAfterDelay(2, () -> {
            try {
                PortalDestinationContext.push(new PortalDestinationContext.Source(level, source, source));
                var result = level.getPortalForcer().findClosestPortalPosition(partner, true, level.getWorldBorder());
                helper.assertTrue(result.isPresent() && CustomPortalShape.findPortalRoot(level, result.get()).equals(partner),
                        "expanding a portal must not make its old partner look occupied");
            } finally {
                PortalDestinationContext.clear();
                links.unlink(level.dimension(), oldRoot);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "portal_platform")
    public static void linkDataPersistsAndReassignsBothDirections(GameTestHelper helper) {
        PortalLinkData data = new PortalLinkData();
        BlockPos a = new BlockPos(1, 2, 3), b = new BlockPos(4, 5, 6), c = new BlockPos(7, 8, 9);
        data.link(Level.OVERWORLD, a, Level.NETHER, b);
        data = PortalLinkData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(data.getLinkedPos(Level.OVERWORLD, a, Level.NETHER).filter(b::equals).isPresent(), "saved forward link");
        helper.assertTrue(data.getLinkedPos(Level.NETHER, b, Level.OVERWORLD).filter(a::equals).isPresent(), "saved reverse link");
        data.link(Level.OVERWORLD, c, Level.NETHER, b);
        helper.assertTrue(data.getLinkedRef(Level.OVERWORLD, a).isEmpty(), "old partner must be released");
        data.unlink(Level.NETHER, b);
        helper.assertTrue(data.getLinkedRef(Level.OVERWORLD, c).isEmpty() && data.getLinkedRef(Level.NETHER, b).isEmpty(),
                "unlink must remove both directions");
        helper.succeed();
    }
}
