package net.riley.betterportal;

import net.riley.betterportal.config.PortalConfig;
import net.riley.betterportal.gametest.MixinGameTests;
import net.riley.betterportal.gametest.BlueprintGameTests;
import net.riley.betterportal.gametest.PortalGameTests;
import net.riley.betterportal.gametest.PortalRegressionTests;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

// 更好的地狱门：任意形状地狱门 + 对面复制门形 + 成对持久链接。
// Side: BOTH（功能主体在服务端，客户端不需要额外代码）
@Mod(BetterNetherPortalMod.MODID)
public class BetterNetherPortalMod {
    public static final String MODID = "better_nether_portal";

    public BetterNetherPortalMod(IEventBus modEventBus) {
        PortalConfig.register(modEventBus);
        modEventBus.addListener(PortalGameTests::register);
        modEventBus.addListener(PortalRegressionTests::register);
        modEventBus.addListener(BlueprintGameTests::register);
        modEventBus.addListener(MixinGameTests::register);
    }
}
