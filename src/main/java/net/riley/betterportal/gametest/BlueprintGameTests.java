package net.riley.betterportal.gametest;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.riley.betterportal.BetterNetherPortalMod;
import net.riley.betterportal.portal.PortalBlueprint;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// 通过真实 capture/placeNear 检查落点选择与方块保留；高空场地相互隔离并在 finally 清理。
@GameTestHolder(BetterNetherPortalMod.MODID)
@PrefixGameTestTemplate(false)
public final class BlueprintGameTests {
    private BlueprintGameTests() {
    }

    public static void register(RegisterGameTestsEvent event) {
        event.register(BlueprintGameTests.class);
    }

    private static PortalBlueprint captureSource(GameTestHelper helper) {
        BlockPos bottom = new BlockPos(5, 3, 5);
        helper.setBlock(bottom.below(), Blocks.OBSIDIAN);
        helper.setBlock(bottom.above(2), Blocks.OBSIDIAN);
        for (BlockPos cell : new BlockPos[] { bottom, bottom.above() }) {
            helper.setBlock(cell.east(), Blocks.OBSIDIAN);
            helper.setBlock(cell.west(), Blocks.OBSIDIAN);
        }
        BlockState portal = Blocks.NETHER_PORTAL.defaultBlockState()
                .setValue(NetherPortalBlock.AXIS, Direction.Axis.X);
        helper.getLevel().setBlock(helper.absolutePos(bottom), portal, 18);
        helper.getLevel().setBlock(helper.absolutePos(bottom.above()), portal, 18);
        return PortalBlueprint.capture(helper.getLevel(), helper.absolutePos(bottom)).orElseThrow();
    }

    private static BlockPos target(GameTestHelper helper, int site) {
        BlockPos origin = helper.absolutePos(new BlockPos(5, 0, 5));
        return new BlockPos(origin.getX() + 256 * site, helper.getLevel().getMaxBuildHeight() - 20,
                origin.getZ());
    }

    private static BlockPos place(GameTestHelper helper, PortalBlueprint blueprint, BlockPos target) {
        Optional<BlockPos> placed = blueprint.placeNear(helper.getLevel(), target);
        helper.assertTrue(placed.isPresent(), "blueprint should find a safe alternative");
        BlockPos root = placed.orElseThrow();
        helper.assertTrue(helper.getLevel().getBlockState(root).is(Blocks.NETHER_PORTAL), "portal at selected root");
        helper.assertTrue(helper.getLevel().getBlockState(root.above()).is(Blocks.NETHER_PORTAL), "two-block portal");
        return root;
    }

    private static void fill(ServerLevel level, BlockPos min, BlockPos max, BlockState state) {
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            level.setBlock(pos, state, 18);
        }
    }

    private static void clearTarget(ServerLevel level, BlockPos target) {
        fill(level, target.offset(-18, -50, -18),
                new BlockPos(target.getX() + 18, level.getMaxBuildHeight() - 1, target.getZ() + 18),
                Blocks.AIR.defaultBlockState());
    }

    @GameTest(template = "portal_platform")
    public static void waterloggedPlayerBlocksSurvivePlacement(GameTestHelper helper) {
        PortalBlueprint blueprint = captureSource(helper);
        ServerLevel level = helper.getLevel();
        BlockState[] obstacles = {
                Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true),
                Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true),
                Blocks.OAK_SIGN.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true)
        };
        for (int i = 0; i < obstacles.length; i++) {
            BlockPos ground = target(helper, i + 1);
            // 唯一地面上方两格是首选门根；站立牌放在其下一格，让牌子有实际支撑。
            BlockPos obstacle = obstacles[i].is(Blocks.OAK_SIGN) ? ground.above() : ground.above(2);
            try {
                level.setBlock(ground, Blocks.STONE.defaultBlockState(), 18);
                level.setBlock(obstacle, obstacles[i], 18);
                if (level.getBlockEntity(obstacle) instanceof ChestBlockEntity chest) {
                    chest.setItem(0, new ItemStack(Items.DIAMOND, 7));
                }
                place(helper, blueprint, ground);
                helper.assertTrue(level.getBlockState(obstacle).equals(obstacles[i]),
                        "waterlogged player block must survive: " + obstacles[i]);
                if (obstacles[i].is(Blocks.CHEST)) {
                    helper.assertTrue(level.getBlockEntity(obstacle) instanceof ChestBlockEntity,
                            "waterlogged chest block entity must survive");
                    ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(obstacle);
                    helper.assertTrue(chest.getItem(0).is(Items.DIAMOND) && chest.getItem(0).getCount() == 7,
                            "waterlogged chest inventory must survive");
                }
            } finally {
                clearTarget(level, ground);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "portal_platform")
    public static void supportPreservesNonCollisionPlayerBlocks(GameTestHelper helper) {
        PortalBlueprint blueprint = captureSource(helper);
        ServerLevel level = helper.getLevel();
        BlockState[] obstacles = {
                Blocks.OAK_SIGN.defaultBlockState(), Blocks.TORCH.defaultBlockState(), Blocks.RAIL.defaultBlockState()
        };
        for (int i = 0; i < obstacles.length; i++) {
            BlockPos target = target(helper, i + 4);
            BlockPos obstacle = target.below();
            try {
                // 地面候选被牌/火把/轨道挡住；首个空中候选的补支撑位置恰好是玩家方块。
                level.setBlock(target.below(2), Blocks.STONE.defaultBlockState(), 18);
                level.setBlock(obstacle, obstacles[i], 18);
                place(helper, blueprint, target);
                helper.assertTrue(level.getBlockState(obstacle).equals(obstacles[i]),
                        "support must not overwrite player block: " + obstacles[i]);
            } finally {
                clearTarget(level, target);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "portal_platform")
    public static void waterloggedExitRequiresCollisionClearance(GameTestHelper helper) {
        PortalBlueprint blueprint = captureSource(helper);
        ServerLevel level = helper.getLevel();
        BlockPos ground = target(helper, 7);
        BlockPos blockedRoot = ground.above(2);
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
        try {
            level.setBlock(ground, Blocks.STONE.defaultBlockState(), 18);
            level.setBlock(blockedRoot.north(), stairs, 18);
            level.setBlock(blockedRoot.south(), stairs, 18);
            BlockPos placed = place(helper, blueprint, ground);
            helper.assertTrue(!placed.equals(blockedRoot), "waterlogged stairs must block the preferred exits");
            assertClearExits(helper, placed);
            helper.assertTrue(level.getBlockState(blockedRoot.north()).equals(stairs)
                    && level.getBlockState(blockedRoot.south()).equals(stairs), "exit stairs must survive");
        } finally {
            clearTarget(level, ground);
        }
        helper.succeed();
    }

    @GameTest(template = "portal_platform")
    public static void carvingRejectsProtectedExitFrames(GameTestHelper helper) {
        PortalBlueprint blueprint = captureSource(helper);
        ServerLevel level = helper.getLevel();
        BlockPos target = target(helper, 8);
        BlockPos blockedRoot = target.above();
        try {
            // 石体覆盖整个搜索范围并一直延伸到建筑高度上限，前两阶段无落点，必须开凿。
            fill(level, target.offset(-17, -50, -17),
                    new BlockPos(target.getX() + 17, level.getMaxBuildHeight() - 1, target.getZ() + 17),
                    Blocks.STONE.defaultBlockState());
            for (BlockPos cell : new BlockPos[] { blockedRoot, blockedRoot.above() }) {
                level.setBlock(cell.north(), Blocks.OBSIDIAN.defaultBlockState(), 18);
                level.setBlock(cell.south(), Blocks.OBSIDIAN.defaultBlockState(), 18);
            }
            BlockPos placed = place(helper, blueprint, target);
            helper.assertTrue(!placed.equals(blockedRoot), "protected exit frames must reject the first carve candidate");
            assertClearExits(helper, placed);
            for (BlockPos cell : new BlockPos[] { blockedRoot, blockedRoot.above() }) {
                helper.assertTrue(level.getBlockState(cell.north()).is(Blocks.OBSIDIAN)
                        && level.getBlockState(cell.south()).is(Blocks.OBSIDIAN), "protected exit frames must survive");
            }
        } finally {
            clearTarget(level, target);
        }
        helper.succeed();
    }

    private static void assertClearExits(GameTestHelper helper, BlockPos root) {
        ServerLevel level = helper.getLevel();
        for (BlockPos cell : new BlockPos[] { root, root.above() }) {
            for (BlockPos exit : new BlockPos[] { cell.north(), cell.south() }) {
                helper.assertTrue(level.getBlockState(exit).getCollisionShape(level, exit).isEmpty(),
                        "portal exit must be clear at " + exit);
            }
        }
    }
}
