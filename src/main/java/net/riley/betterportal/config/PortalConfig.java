package net.riley.betterportal.config;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.common.ModConfigSpec;

// 独立模组的可调参数：任意形状门扫描上限、对面复制门形、成对链接开关。
// Side: BOTH（配置在客户端与服务端各自加载；实际行为只在服务端生效）
public final class PortalConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final Portals PORTALS = new Portals(BUILDER);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private PortalConfig() {
    }

    public static void register(IEventBus modEventBus) {
        ModLoadingContext.get().getActiveContainer().registerConfig(
                net.neoforged.fml.config.ModConfig.Type.SERVER, SPEC);
    }

    // 安全读取：世界加载阶段配置尚未加载，此时 .get() 会抛 IllegalStateException。
    public static boolean cfg(ModConfigSpec.BooleanValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static int cfg(ModConfigSpec.IntValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static final class Portals {
        public final ModConfigSpec.IntValue maxInteriorCells;
        public final ModConfigSpec.IntValue maxSpan;
        public final ModConfigSpec.BooleanValue copyShapeToDestination;
        public final ModConfigSpec.BooleanValue persistentLinks;

        Portals(ModConfigSpec.Builder b) {
            b.comment("任意形状地狱门（点火扫描、对面复制门形、成对链接）").push("portal");

            maxInteriorCells = b.comment("单扇门内部最大格数；越大扫描越慢，原版矩形上限为 441")
                    .defineInRange("max_interior_cells", 64, 1, 441);
            maxSpan = b.comment("内部包围盒单轴最大跨度（原版为 21）")
                    .defineInRange("max_span", 21, 1, 21);
            copyShapeToDestination = b.comment("对面没有现成门时，尝试把源门形状原样复制过去；失败回退原版矩形门")
                    .define("copy_shape_to_destination", true);
            persistentLinks = b.comment("成对门持久链接，避免附近的门互相抢传送目标")
                    .define("persistent_links", true);

            b.pop();
        }
    }
}
