package net.riley.betterportal.spec;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

// 任意形状地狱门的可用框架方块。默认黑曜石 + 哭泣黑曜石；
// 整合包/数据包可追加模组方块（CustomPortalShape 会与本标签并集原版 isPortalFrame）。
public final class ModTags {
    private ModTags() {
    }

    public static final TagKey<Block> PORTAL_FRAMES = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("better_nether_portal", "portal_frames"));
}
