package net.riley.betterportal.portal;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

// 成对地狱门链接表：键 = 维度 + 门根坐标；双向存储。
// 目的：附近有两扇门时，原版“最近门”规则会互相抢目标；这里让成对的门优先走彼此。
// 目标门被拆/重建后，链接失效会自动回退原版搜索并重新配对。
// Side: SERVER
public class PortalLinkData extends SavedData {
    private static final String DATA_NAME = "better_nether_portal_links";
    private static final SavedData.Factory<PortalLinkData> FACTORY =
            new SavedData.Factory<>(PortalLinkData::new, PortalLinkData::load, DataFixTypes.LEVEL);

    private final Map<String, String> links = new HashMap<>();

    public static PortalLinkData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static PortalLinkData load(CompoundTag tag, HolderLookup.Provider provider) {
        PortalLinkData data = new PortalLinkData();
        ListTag list = tag.getList("links", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            data.links.put(entry.getString("from"), entry.getString("to"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Map.Entry<String, String> entry : links.entrySet()) {
            CompoundTag compound = new CompoundTag();
            compound.putString("from", entry.getKey());
            compound.putString("to", entry.getValue());
            list.add(compound);
        }
        tag.put("links", list);
        return tag;
    }

    // 从 fromDim/fromRoot 出发，若链接指向 toDim 则返回目标门根；目标维度不匹配视为无链接。
    public Optional<BlockPos> getLinkedPos(ResourceKey<Level> fromDim, BlockPos fromRoot, ResourceKey<Level> toDim) {
        String target = links.get(key(fromDim, fromRoot));
        if (target == null) {
            return Optional.empty();
        }
        Parsed parsed = parse(target);
        if (parsed == null || !parsed.dimension.equals(toDim)) {
            return Optional.empty();
        }
        return Optional.of(parsed.pos);
    }

    // 查某扇门当前被谁链接（用于阻止附近门互相抢人）。
    public Optional<PortalRef> getLinkedRef(ResourceKey<Level> dim, BlockPos root) {
        String target = links.get(key(dim, root));
        if (target == null) {
            return Optional.empty();
        }
        Parsed parsed = parse(target);
        return parsed == null ? Optional.empty() : Optional.of(new PortalRef(parsed.dimension, parsed.pos));
    }

    // 双向配对；两端原有的链接先解除，保证一扇门只属于一对。
    public void link(ResourceKey<Level> aDim, BlockPos aRoot, ResourceKey<Level> bDim, BlockPos bRoot) {
        String a = key(aDim, aRoot);
        String b = key(bDim, bRoot);
        if (a.equals(b)) {
            return;
        }
        if (b.equals(links.get(a)) && a.equals(links.get(b))) {
            return; // 已经是这一对，避免每次传送都标脏
        }
        unlinkKey(a);
        unlinkKey(b);
        links.put(a, b);
        links.put(b, a);
        setDirty();
    }

    public void unlink(ResourceKey<Level> dim, BlockPos root) {
        if (unlinkKey(key(dim, root))) {
            setDirty();
        }
    }

    private boolean unlinkKey(String key) {
        String other = links.remove(key);
        if (other == null) {
            return false;
        }
        links.remove(other);
        return true;
    }

    public static String key(ResourceKey<Level> dimension, BlockPos pos) {
        return dimension.location() + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static Parsed parse(String key) {
        int split = key.indexOf('|');
        if (split < 0) {
            return null;
        }
        ResourceLocation dimId = ResourceLocation.tryParse(key.substring(0, split));
        if (dimId == null) {
            return null;
        }
        String[] parts = key.substring(split + 1).split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            BlockPos pos = new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            return new Parsed(ResourceKey.create(Registries.DIMENSION, dimId), pos);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public record PortalRef(ResourceKey<Level> dimension, BlockPos pos) {
        public boolean matches(ResourceKey<Level> dim, BlockPos root) {
            return dimension.equals(dim) && pos.equals(root);
        }
    }

    private record Parsed(ResourceKey<Level> dimension, BlockPos pos) {
    }
}
