package net.riley.betterportal.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.riley.betterportal.BetterNetherPortalMod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Mixin 回归测试：强制类加载所有 mixin 目标，让 transformer 当场应用。
// 与 better_nether_portal.mixins.json 的 mixins 列表一一对应。
// Side: SERVER
@GameTestHolder(BetterNetherPortalMod.MODID)
@PrefixGameTestTemplate(false)
public final class MixinGameTests {
    private MixinGameTests() {
    }

    public static void register(RegisterGameTestsEvent event) {
        event.register(MixinGameTests.class);
    }

    @GameTest(template = "portal_platform")
    public static void mixinTargetsApply(GameTestHelper helper) throws Exception {
        for (String target : new String[] {
                "net.minecraft.world.level.block.BaseFireBlock",
                "net.minecraft.world.level.block.NetherPortalBlock",
                "net.minecraft.world.level.portal.PortalForcer"
        }) {
            Class.forName(target, true, MixinGameTests.class.getClassLoader());
        }
        helper.succeed();
    }
}
