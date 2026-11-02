package net.riley.betterportal.gametest;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.riley.betterportal.BetterNetherPortalMod;
import net.riley.betterportal.config.PortalConfig;
import net.riley.betterportal.portal.CustomPortalShape;
import net.riley.betterportal.portal.PortalBlueprint;
import net.riley.betterportal.portal.PortalDestinationContext;
import net.riley.betterportal.portal.PortalLinkData;
import net.riley.betterportal.portal.PortalTravel;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// 任意形状地狱门测试：
// 1. 最小门（1 格内径 + 上下左右 4 黑曜石）点火即点亮
// 2. 框破损后门方块碎掉
// 3. 原版最小矩形门（内径 2x3）回归——mixin 不得破坏原版行为
@GameTestHolder(BetterNetherPortalMod.MODID)
@PrefixGameTestTemplate(false)
public final class PortalGameTests {
    private PortalGameTests() {
    }

    public static void register(RegisterGameTestsEvent event) {
        event.register(PortalGameTests.class);
    }

    // 十字框：内径 (5,3,5)，框架为门平面内的上/下/左/右四块黑曜石（X 平面竖直门）
    private static void buildPlusFrame(GameTestHelper helper) {
        BlockPos center = new BlockPos(5, 3, 5);
        helper.setBlock(center.above(), Blocks.OBSIDIAN);
        helper.setBlock(center.below(), Blocks.OBSIDIAN);
        helper.setBlock(center.east(), Blocks.OBSIDIAN);
        helper.setBlock(center.west(), Blocks.OBSIDIAN);
    }

    private static BlockPos plusCenter() {
        return new BlockPos(5, 3, 5);
    }

    @GameTest(template = "portal_platform")
    public static void plusShapePortalIgnites(GameTestHelper helper) {
        buildPlusFrame(helper);
        helper.setBlock(plusCenter(), Blocks.FIRE);
        helper.assertBlockPresent(Blocks.NETHER_PORTAL, plusCenter());
        helper.succeed();
    }

    @GameTest(template = "portal_platform")
    public static void plusShapePortalCollapsesWhenFrameBreaks(GameTestHelper helper) {
        buildPlusFrame(helper);
        helper.setBlock(plusCenter(), Blocks.FIRE);
        helper.assertBlockPresent(Blocks.NETHER_PORTAL, plusCenter());
        // 打掉上方框块 → 门方块应变为空气
        helper.setBlock(plusCenter().above(), Blocks.AIR);
        helper.assertBlockPresent(Blocks.AIR, plusCenter());
        helper.succeed();
    }

    // 原版最小矩形门：内径 2x3 @ x9..10,y2..4,z=5，框为黑曜石
    @GameTest(template = "portal_platform")
    public static void vanillaRectanglePortalStillWorks(GameTestHelper helper) {
        BlockPos interiorMin = new BlockPos(9, 2, 5);
        for (int dx = -1; dx <= 2; dx++) {
            helper.setBlock(interiorMin.offset(dx, -1, 0), Blocks.OBSIDIAN); // 底
            helper.setBlock(interiorMin.offset(dx, 3, 0), Blocks.OBSIDIAN);  // 顶
        }
        for (int dy = 0; dy <= 2; dy++) {
            helper.setBlock(interiorMin.offset(-1, dy, 0), Blocks.OBSIDIAN); // 左列
            helper.setBlock(interiorMin.offset(2, dy, 0), Blocks.OBSIDIAN);  // 右列
        }
        helper.setBlock(interiorMin, Blocks.FIRE);
        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                helper.assertBlockPresent(Blocks.NETHER_PORTAL, interiorMin.offset(dx, dy, 0));
            }
        }
        helper.succeed();
    }

    // 任意形状：同一竖直平面内的 L 形内径（3 格）也应点亮
    @GameTest(template = "portal_platform")
    public static void lShapePortalIgnites(GameTestHelper helper) {
        int z = 5;
        BlockPos a = new BlockPos(4, 3, z); // 内径 L 底左
        BlockPos b = new BlockPos(5, 3, z); // 内径 L 底右
        BlockPos c = new BlockPos(4, 4, z); // 内径 L 上（a 正上方）
        // 外圈沿 L 形边界铺框（共 7 块，含凹角 (5,4)）
        helper.setBlock(a.below(), Blocks.OBSIDIAN);
        helper.setBlock(b.below(), Blocks.OBSIDIAN);
        helper.setBlock(a.west(), Blocks.OBSIDIAN);
        helper.setBlock(c.west(), Blocks.OBSIDIAN);
        helper.setBlock(b.east(), Blocks.OBSIDIAN);
        helper.setBlock(b.above(), Blocks.OBSIDIAN); // 凹角
        helper.setBlock(c.above(), Blocks.OBSIDIAN);
        helper.setBlock(b, Blocks.FIRE);
        helper.assertBlockPresent(Blocks.NETHER_PORTAL, a);
        helper.assertBlockPresent(Blocks.NETHER_PORTAL, b);
        helper.assertBlockPresent(Blocks.NETHER_PORTAL, c);
        helper.succeed();
    }

    // Z 平面门：L 形内径 (x=5,y=3,z=5) + (5,3,6) + (5,4,5)。
    // 用非矩形形状，确保不是靠原版矩形判定蒙混过关；x 两侧放石头让先跑的 X 平面扫描失败。
    @GameTest(template = "portal_platform")
    public static void zPlanePortalIgnites(GameTestHelper helper) {
        BlockPos a = new BlockPos(5, 3, 5);
        BlockPos b = new BlockPos(5, 3, 6);
        BlockPos c = new BlockPos(5, 4, 5);
        helper.setBlock(a.below(), Blocks.OBSIDIAN);
        helper.setBlock(b.below(), Blocks.OBSIDIAN);
        helper.setBlock(a.north(), Blocks.OBSIDIAN);
        helper.setBlock(c.north(), Blocks.OBSIDIAN);
        helper.setBlock(b.south(), Blocks.OBSIDIAN);
        helper.setBlock(b.above(), Blocks.OBSIDIAN); // 凹角
        helper.setBlock(c.above(), Blocks.OBSIDIAN);
        helper.setBlock(a.east(), Blocks.STONE);
        helper.setBlock(a.west(), Blocks.STONE);
        helper.setBlock(a, Blocks.FIRE);
        helper.assertBlockPresent(Blocks.NETHER_PORTAL, a);
        helper.assertBlockPresent(Blocks.NETHER_PORTAL, b);
        helper.assertBlockPresent(Blocks.NETHER_PORTAL, c);
        helper.assertBlockProperty(a, NetherPortalBlock.AXIS, Direction.Axis.Z);
        helper.succeed();
    }

    // 哭泣黑曜石在原版不是门框；本模组 portal_frames 标签应让它可用。
    @GameTest(template = "portal_platform")
    public static void cryingObsidianFrameIgnites(GameTestHelper helper) {
        BlockPos center = plusCenter();
        helper.setBlock(center.above(), Blocks.CRYING_OBSIDIAN);
        helper.setBlock(center.below(), Blocks.CRYING_OBSIDIAN);
        helper.setBlock(center.east(), Blocks.CRYING_OBSIDIAN);
        helper.setBlock(center.west(), Blocks.CRYING_OBSIDIAN);
        helper.setBlock(center, Blocks.FIRE);
        helper.assertBlockPresent(Blocks.NETHER_PORTAL, center);
        helper.succeed();
    }

    // 非框架方块不能组成门；不能让原版/自定义扫描误判。
    @GameTest(template = "portal_platform")
    public static void nonFrameDoesNotIgnite(GameTestHelper helper) {
        BlockPos center = plusCenter();
        helper.setBlock(center.above(), Blocks.DIRT);
        helper.setBlock(center.below(), Blocks.DIRT);
        helper.setBlock(center.east(), Blocks.DIRT);
        helper.setBlock(center.west(), Blocks.DIRT);
        helper.setBlock(center, Blocks.FIRE);
        helper.assertBlockNotPresent(Blocks.NETHER_PORTAL, center);
        helper.succeed();
    }

    // 挖掉任意一个内部格，整扇门应碎裂（与原版 isComplete 语义一致）。
    @GameTest(template = "portal_platform")
    public static void interiorBlockBreakCollapses(GameTestHelper helper) {
        int z = 5;
        BlockPos a = new BlockPos(4, 3, z);
        BlockPos b = new BlockPos(5, 3, z);
        BlockPos c = new BlockPos(4, 4, z);
        helper.setBlock(a.below(), Blocks.OBSIDIAN);
        helper.setBlock(b.below(), Blocks.OBSIDIAN);
        helper.setBlock(a.west(), Blocks.OBSIDIAN);
        helper.setBlock(c.west(), Blocks.OBSIDIAN);
        helper.setBlock(b.east(), Blocks.OBSIDIAN);
        helper.setBlock(b.above(), Blocks.OBSIDIAN);
        helper.setBlock(c.above(), Blocks.OBSIDIAN);
        helper.setBlock(b, Blocks.FIRE);
        helper.assertBlockPresent(Blocks.NETHER_PORTAL, a);
        helper.assertBlockPresent(Blocks.NETHER_PORTAL, b);
        helper.assertBlockPresent(Blocks.NETHER_PORTAL, c);
        helper.setBlock(a, Blocks.AIR);
        helper.assertBlockPresent(Blocks.AIR, b);
        helper.assertBlockPresent(Blocks.AIR, c);
        helper.succeed();
    }

    // 蓝图捕获 + 粘贴：十字门（1 格内径）复制到目标点，框架与门块都应出现。
    // 直接访问 level 的 API 必须用 absolutePos，GameTestHelper 的 setBlock/assert 才会自动换算。
    @GameTest(template = "portal_platform")
    public static void blueprintCaptureAndPaste(GameTestHelper helper) {
        buildPlusFrame(helper);
        helper.setBlock(plusCenter(), Blocks.FIRE);
        BlockPos source = helper.absolutePos(plusCenter());
        BlockState sourceState = helper.getLevel().getBlockState(source);
        helper.assertTrue(sourceState.is(Blocks.NETHER_PORTAL), "source should be portal: " + source + " -> " + sourceState);
        Optional<PortalBlueprint> blueprint = PortalBlueprint.capture(helper.getLevel(), source);
        helper.assertTrue(blueprint.isPresent(),
                "capture failed: axis=" + sourceState.getValue(NetherPortalBlock.AXIS)
                        + " above=" + helper.getLevel().getBlockState(source.above())
                        + " below=" + helper.getLevel().getBlockState(source.below())
                        + " east=" + helper.getLevel().getBlockState(source.east())
                        + " west=" + helper.getLevel().getBlockState(source.west())
                        + " north=" + helper.getLevel().getBlockState(source.north())
                        + " south=" + helper.getLevel().getBlockState(source.south())
                        + " maxCells=" + PortalConfig.cfg(PortalConfig.PORTALS.maxInteriorCells)
                        + " maxSpan=" + PortalConfig.cfg(PortalConfig.PORTALS.maxSpan));
        Optional<BlockPos> placed = blueprint.get().placeNear(helper.getLevel(), helper.absolutePos(new BlockPos(10, 3, 5)));
        helper.assertTrue(placed.isPresent(), "blueprint should find a place near target");
        BlockPos root = placed.get();
        helper.assertTrue(helper.getLevel().getBlockState(root).is(Blocks.NETHER_PORTAL), "portal at pasted root");
        helper.assertTrue(helper.getLevel().getBlockState(root.above()).is(Blocks.OBSIDIAN), "frame above");
        helper.assertTrue(helper.getLevel().getBlockState(root.below()).is(Blocks.OBSIDIAN), "frame below");
        helper.assertTrue(helper.getLevel().getBlockState(root.east()).is(Blocks.OBSIDIAN), "frame east");
        helper.assertTrue(helper.getLevel().getBlockState(root.west()).is(Blocks.OBSIDIAN), "frame west");
        helper.succeed();
    }

    // 真正走 PortalForcer.createPortal + mixin 上下文：应复制十字门，而不是原版 2x3。
    @GameTest(template = "portal_platform")
    public static void blueprintCreatePortalMixin(GameTestHelper helper) {
        buildPlusFrame(helper);
        helper.setBlock(plusCenter(), Blocks.FIRE);
        var level = helper.getLevel();
        BlockPos source = helper.absolutePos(plusCenter());
        try {
            PortalDestinationContext.push(new PortalDestinationContext.Source(level, source, source));
            Optional<BlockUtil.FoundRectangle> rectangle = level.getPortalForcer()
                    .createPortal(helper.absolutePos(new BlockPos(10, 3, 5)), Direction.Axis.X);
            helper.assertTrue(rectangle.isPresent(), "createPortal should return a rectangle");
            BlockPos root = rectangle.get().minCorner;
            helper.assertTrue(level.getBlockState(root).is(Blocks.NETHER_PORTAL), "portal at mixin root");
            helper.assertTrue(level.getBlockState(root.above()).is(Blocks.OBSIDIAN), "mixin frame above");
            helper.assertTrue(level.getBlockState(root.below()).is(Blocks.OBSIDIAN), "mixin frame below");
        } finally {
            PortalDestinationContext.clear();
        }
        helper.succeed();
    }

    // 按内部格集合自动铺框（门平面内四邻中不属于内部的格子放黑曜石），X 平面。
    private static void buildFrameAround(GameTestHelper helper, Set<BlockPos> interior) {
        for (BlockPos pos : interior) {
            for (Direction dir : new Direction[] { Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN }) {
                BlockPos neighbor = pos.relative(dir);
                if (!interior.contains(neighbor)) {
                    helper.setBlock(neighbor, Blocks.OBSIDIAN);
                }
            }
        }
    }

    // 前后贴着的两扇十字门（z=5 与 z=6）不能被当成同一扇门：各自的根应是各自的门格。
    @GameTest(template = "portal_platform")
    public static void adjacentPortalsHaveSeparateRoots(GameTestHelper helper) {
        BlockPos front = plusCenter();
        BlockPos back = front.south();
        for (BlockPos center : new BlockPos[] { front, back }) {
            buildFrameAround(helper, Set.of(center));
            helper.setBlock(center, Blocks.FIRE);
            helper.assertBlockPresent(Blocks.NETHER_PORTAL, center);
        }
        var level = helper.getLevel();
        helper.assertTrue(CustomPortalShape.findPortalRoot(level, helper.absolutePos(back)).equals(helper.absolutePos(back)),
                "back portal root must not leak into front portal");
        helper.assertTrue(CustomPortalShape.collectPortalCells(level, helper.absolutePos(front)).size() == 1,
                "front portal should contain exactly one cell");
        helper.succeed();
    }

    // 3×3 方块 + 左侧单格凸起：根落在凸起上，但落点矩形应从门心展开，拿到完整 3×3。
    @GameTest(template = "portal_platform")
    public static void largestRectangleStartsFromCenter(GameTestHelper helper) {
        Set<BlockPos> interior = new HashSet<>();
        for (int x = 5; x <= 7; x++) {
            for (int y = 3; y <= 5; y++) {
                interior.add(new BlockPos(x, y, 5));
            }
        }
        interior.add(new BlockPos(4, 4, 5));
        buildFrameAround(helper, interior);
        helper.setBlock(new BlockPos(6, 4, 5), Blocks.FIRE);
        for (BlockPos pos : interior) {
            helper.assertBlockPresent(Blocks.NETHER_PORTAL, pos);
        }
        var level = helper.getLevel();
        BlockPos root = CustomPortalShape.findPortalRoot(level, helper.absolutePos(new BlockPos(6, 4, 5)));
        helper.assertTrue(root.equals(helper.absolutePos(new BlockPos(4, 4, 5))), "root should be the stub cell: " + root);
        BlockUtil.FoundRectangle rect = PortalTravel.largestRectangle(level, root);
        helper.assertTrue(rect.axis1Size == 3 && rect.axis2Size == 3,
                "expected 3x3 rectangle, got " + rect.axis1Size + "x" + rect.axis2Size);
        helper.succeed();
    }

    // 链接表双向读写 + 解除链接。
    @GameTest(template = "portal_platform")
    public static void linkDataRoundTrip(GameTestHelper helper) {
        PortalLinkData data = PortalLinkData.get(helper.getLevel().getServer());
        BlockPos overworld = new BlockPos(1, 2, 3);
        BlockPos nether = new BlockPos(4, 5, 6);
        data.link(Level.OVERWORLD, overworld, Level.NETHER, nether);
        helper.assertTrue(data.getLinkedPos(Level.OVERWORLD, overworld, Level.NETHER)
                .filter(nether::equals).isPresent(), "overworld -> nether link");
        helper.assertTrue(data.getLinkedPos(Level.NETHER, nether, Level.OVERWORLD)
                .filter(overworld::equals).isPresent(), "nether -> overworld link");
        data.unlink(Level.OVERWORLD, overworld);
        helper.assertTrue(data.getLinkedPos(Level.OVERWORLD, overworld, Level.NETHER).isEmpty(), "unlink");
        helper.succeed();
    }
}
