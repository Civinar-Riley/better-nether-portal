package net.riley.betterportal.portal;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.riley.betterportal.config.PortalConfig;
import net.riley.betterportal.api.PortalCreateEvent;

// 门形蓝图：把源门的内部格与框架偏移记下来，在对面寻找安全落点原样重建。
// 只在服务端传送路径使用；找不到合适落点时返回 empty，由调用方回退原版矩形门。
// 落点策略（由保守到激进）：地表支撑 -> 空中可替换空间 -> 有限开凿（保护基岩/容器/黑曜石建筑）。
// Side: SERVER
public final class PortalBlueprint {
    private static final int SEARCH_RADIUS = 16;

    private final Direction.Axis axis;
    private final Set<BlockPos> interiorOffsets;
    private final Set<BlockPos> frameOffsets;
    // 以下在构造时算好：落点搜索每个候选点都要遍历，避免反复分配集合
    private final BlockPos[] allOffsets;
    private final BlockPos[] supportOffsets;
    private final BlockPos[] clearanceOffsets;
    private final int minRelX, maxRelX, minRelY, maxRelY, minRelZ, maxRelZ;

    private PortalBlueprint(Direction.Axis axis, Set<BlockPos> interiorOffsets, Set<BlockPos> frameOffsets) {
        this.axis = axis;
        this.interiorOffsets = Set.copyOf(interiorOffsets);
        this.frameOffsets = Set.copyOf(frameOffsets);
        Set<BlockPos> all = new HashSet<>(interiorOffsets);
        all.addAll(frameOffsets);
        this.allOffsets = all.toArray(BlockPos[]::new);
        int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE;
        int y0 = Integer.MAX_VALUE, y1 = Integer.MIN_VALUE;
        int z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
        for (BlockPos offset : this.allOffsets) {
            x0 = Math.min(x0, offset.getX());
            x1 = Math.max(x1, offset.getX());
            y0 = Math.min(y0, offset.getY());
            y1 = Math.max(y1, offset.getY());
            z0 = Math.min(z0, offset.getZ());
            z1 = Math.max(z1, offset.getZ());
        }
        this.minRelX = x0;
        this.maxRelX = x1;
        this.minRelY = y0;
        this.maxRelY = y1;
        this.minRelZ = z0;
        this.maxRelZ = z1;
        int bottomY = y0;
        this.supportOffsets = all.stream().filter(o -> o.getY() == bottomY).map(BlockPos::below).toArray(BlockPos[]::new);
        Direction cross = axis == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
        this.clearanceOffsets = new BlockPos[interiorOffsets.size() * 2];
        int index = 0;
        for (BlockPos offset : this.interiorOffsets) {
            this.clearanceOffsets[index++] = offset.relative(cross);
            this.clearanceOffsets[index++] = offset.relative(cross.getOpposite());
        }
    }

    public static Optional<PortalBlueprint> capture(BlockGetter level, BlockPos portalPos) {
        BlockState origin = level.getBlockState(portalPos);
        if (!origin.is(Blocks.NETHER_PORTAL)) {
            return Optional.empty();
        }
        Direction.Axis axis = origin.getValue(NetherPortalBlock.AXIS);
        int maxCells = PortalConfig.cfg(PortalConfig.PORTALS.maxInteriorCells);
        int maxSpan = PortalConfig.cfg(PortalConfig.PORTALS.maxSpan);

        Set<BlockPos> interior = CustomPortalShape.collectPortalCells(level, portalPos);
        if (interior.size() > maxCells) {
            return Optional.empty();
        }
        BlockPos root = CustomPortalShape.rootOf(interior);
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : interior) {
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        if (maxX - minX + 1 > maxSpan || maxY - minY + 1 > maxSpan || maxZ - minZ + 1 > maxSpan) {
            return Optional.empty();
        }

        Set<BlockPos> frame = new HashSet<>();
        for (BlockPos pos : interior) {
            for (Direction dir : CustomPortalShape.planeDirs(axis)) {
                BlockPos neighbor = pos.relative(dir);
                if (interior.contains(neighbor)) {
                    continue;
                }
                BlockState state = level.getBlockState(neighbor);
                if (!CustomPortalShape.isFrame(level, neighbor, state)) {
                    return Optional.empty();
                }
                frame.add(neighbor.subtract(root));
            }
        }
        if (frame.isEmpty()) {
            return Optional.empty();
        }

        Set<BlockPos> interiorOffsets = new HashSet<>();
        for (BlockPos pos : interior) {
            interiorOffsets.add(pos.subtract(root));
        }
        return Optional.of(new PortalBlueprint(axis, interiorOffsets, frame));
    }

    // 在 approximatePos 附近找落点并粘贴；返回新门根（与源门根同相对位置）。
    public Optional<BlockPos> placeNear(ServerLevel level, BlockPos approximatePos) {
        WorldBorder border = level.getWorldBorder();
        BlockPos clamped = border.clampToBounds(approximatePos.getX(), approximatePos.getY(), approximatePos.getZ());
        int minBuild = level.getMinBuildHeight() + 1;
        int maxBuild = level.dimensionType().hasCeiling()
                ? level.getMinBuildHeight() + level.dimensionType().logicalHeight() - 8
                : level.getMaxBuildHeight() - 2;

        boolean carve = false;
        Optional<BlockPos> spot = findGroundedSpot(level, clamped, border, minBuild, maxBuild);
        if (spot.isEmpty()) {
            spot = scanAirOrCarve(level, clamped, border, minBuild, maxBuild, false);
        }
        if (spot.isEmpty()) {
            carve = true;
            spot = scanAirOrCarve(level, clamped, border, minBuild, maxBuild, true);
        }
        if (spot.isEmpty()) {
            return Optional.empty();
        }
        BlockPos root = spot.get();
        Map<BlockPos, BlockState> blocks = placementPlan(level, root, carve);
        Set<BlockPos> cells = new HashSet<>();
        this.interiorOffsets.forEach(offset -> cells.add(root.offset(offset)));
        if (!PortalCreation.allow(level, root, this.axis, cells, blocks, PortalCreateEvent.Reason.COPY)) {
            return Optional.empty();
        }
        // 计划顺序为清理、支撑、框架、门格；门格最后填，避免半扇门触发邻块复检。
        blocks.forEach((pos, state) -> level.setBlock(pos, state, state.is(Blocks.NETHER_PORTAL) ? 18 : 3));
        return spot;
    }

    private Optional<BlockPos> findGroundedSpot(ServerLevel level, BlockPos center, WorldBorder border, int minBuild, int maxBuild) {
        BlockPos.MutableBlockPos surface = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos root = new BlockPos.MutableBlockPos();
        int startY = Math.min(maxBuild, Math.max(minBuild, center.getY() + 16));
        int endY = Math.max(minBuild, center.getY() - 48);
        for (int radius = 0; radius <= SEARCH_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    int x = center.getX() + dx;
                    int z = center.getZ() + dz;
                    for (int surfaceY = startY; surfaceY >= endY; surfaceY--) {
                        surface.set(x, surfaceY, z);
                        BlockState state = level.getBlockState(surface);
                        if (!isSolidGround(level, surface, state) || state.is(Blocks.BEDROCK) || isUnsafe(state)) {
                            continue;
                        }
                        root.set(x, surfaceY + 1 - this.minRelY, z);
                        if (!canPlace(level, root, border, minBuild, maxBuild, false)) {
                            continue;
                        }
                        if (!hasSolidSupport(level, root) || !hasPlayerClearance(level, root, border, false)) {
                            continue;
                        }
                        if (!isEnvironmentSafe(level, root, minBuild, maxBuild)) {
                            continue;
                        }
                        return Optional.of(root.immutable());
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Optional<BlockPos> scanAirOrCarve(ServerLevel level, BlockPos center, WorldBorder border, int minBuild, int maxBuild, boolean carve) {
        int preferredY = Math.min(maxBuild, Math.max(minBuild, center.getY()));
        BlockPos.MutableBlockPos root = new BlockPos.MutableBlockPos();
        for (int radius = 0; radius <= SEARCH_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    for (int dy = 0; dy <= 24; dy++) {
                        for (int side = 0; side < (dy == 0 ? 1 : 2); side++) {
                            int y = preferredY + (side == 0 ? dy : -dy);
                            if (y < minBuild || y > maxBuild) {
                                continue;
                            }
                            root.set(center.getX() + dx, y - this.minRelY, center.getZ() + dz);
                            if (!canPlace(level, root, border, minBuild, maxBuild, carve)) {
                                continue;
                            }
                            if (!canEnsureSupport(level, root) || !hasPlayerClearance(level, root, border, carve)) {
                                continue;
                            }
                            if (!isEnvironmentSafe(level, root, minBuild, maxBuild)) {
                                continue;
                            }
                            return Optional.of(root.immutable());
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private boolean canPlace(ServerLevel level, BlockPos root, WorldBorder border, int minBuild, int maxBuild, boolean carve) {
        // 先用包围盒做高度/边界的整体判定，再逐格查方块
        if (root.getY() + this.minRelY < minBuild || root.getY() + this.maxRelY > maxBuild) {
            return false;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (BlockPos offset : this.allOffsets) {
            pos.setWithOffset(root, offset);
            if (!border.isWithinBounds(pos)) {
                return false;
            }
            if (!canReplace(level.getBlockState(pos), carve)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasSolidSupport(ServerLevel level, BlockPos root) {
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
        for (BlockPos offset : this.supportOffsets) {
            below.setWithOffset(root, offset);
            BlockState state = level.getBlockState(below);
            if (!isSolidGround(level, below, state) || isUnsafe(state)) {
                return false;
            }
        }
        return true;
    }

    // 空中/开凿落点需要补支撑时，也不能覆盖玩家方块。
    private boolean canEnsureSupport(ServerLevel level, BlockPos root) {
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
        for (BlockPos offset : this.supportOffsets) {
            below.setWithOffset(root, offset);
            BlockState state = level.getBlockState(below);
            if (!isSolidGround(level, below, state) && !canReplace(state, false)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasPlayerClearance(ServerLevel level, BlockPos root, WorldBorder border, boolean carve) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (BlockPos offset : this.clearanceOffsets) {
            pos.setWithOffset(root, offset);
            if (!border.isWithinBounds(pos)) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            if (!isOpenForPlayer(level, pos, state) && (!carve || !canReplace(state, true))) {
                return false;
            }
        }
        return true;
    }

    private boolean isEnvironmentSafe(ServerLevel level, BlockPos root, int minBuild, int maxBuild) {
        int minX = root.getX() + this.minRelX, maxX = root.getX() + this.maxRelX;
        int minY = root.getY() + this.minRelY, maxY = root.getY() + this.maxRelY;
        int minZ = root.getZ() + this.minRelZ, maxZ = root.getZ() + this.maxRelZ;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int z = minZ - 1; z <= maxZ + 1; z++) {
                for (int y = Math.max(minBuild, minY - 1); y <= Math.min(maxBuild, maxY + 2); y++) {
                    if (isUnsafe(level.getBlockState(pos.set(x, y, z)))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private Map<BlockPos, BlockState> placementPlan(ServerLevel level, BlockPos root, boolean carve) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        if (carve) {
            for (BlockPos offset : this.clearanceOffsets) {
                BlockPos pos = root.offset(offset);
                if (canReplace(level.getBlockState(pos), true)) {
                    blocks.put(pos, Blocks.AIR.defaultBlockState());
                }
            }
        }
        BlockState filler = level.dimension() == Level.NETHER
                ? Blocks.NETHERRACK.defaultBlockState() : Blocks.STONE.defaultBlockState();
        for (BlockPos offset : this.supportOffsets) {
            BlockPos pos = root.offset(offset);
            BlockState state = level.getBlockState(pos);
            if (!state.is(Blocks.BEDROCK) && !state.is(Blocks.NETHER_PORTAL)
                    && !CustomPortalShape.isFrame(level, pos, state)
                    && !isSolidGround(level, pos, state) && canReplace(state, false)) {
                blocks.put(pos, filler);
            }
        }
        for (BlockPos offset : this.frameOffsets) {
            blocks.put(root.offset(offset), Blocks.OBSIDIAN.defaultBlockState());
        }
        BlockState portal = Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, this.axis);
        for (BlockPos offset : this.interiorOffsets) {
            blocks.put(root.offset(offset), portal);
        }
        return blocks;
    }

    private static boolean isSolidGround(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty() || isUnsafe(state)) {
            return false;
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        return !shape.isEmpty();
    }

    private static boolean isOpenForPlayer(ServerLevel level, BlockPos pos, BlockState state) {
        return state.getCollisionShape(level, pos).isEmpty();
    }

    private static boolean canReplace(BlockState state, boolean carve) {
        if (isProtected(state)) {
            return false;
        }
        if (carve) {
            // 开凿只动自然地形/空气/水，绝不拆玩家建筑、矿石或容器。
            return isNaturalTerrain(state) || canReplace(state, false);
        }
        if (state.isAir() || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
            return true;
        }
        // 含水方块仍按方块本身判断；水浸箱子、告示牌和楼梯都不是水格。
        return !isUnsafe(state) && (state.is(Blocks.WATER) || state.canBeReplaced());
    }

    // 允许开凿的自然方块白名单；模组/建筑方块不在内，宁可回退原版也不拆家。
    private static boolean isNaturalTerrain(BlockState state) {
        return state.is(Blocks.NETHERRACK)
                || state.is(Blocks.SOUL_SAND)
                || state.is(Blocks.SOUL_SOIL)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.BASALT)
                || state.is(Blocks.SMOOTH_BASALT)
                || state.is(Blocks.BLACKSTONE)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.MUD)
                || state.is(Blocks.CLAY)
                || state.is(Blocks.MOSS_BLOCK)
                || state.is(Blocks.STONE)
                || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.TUFF)
                || state.is(Blocks.ANDESITE)
                || state.is(Blocks.DIORITE)
                || state.is(Blocks.GRANITE)
                || state.is(Blocks.CALCITE)
                || state.is(Blocks.DRIPSTONE_BLOCK)
                || state.is(Blocks.SAND)
                || state.is(Blocks.SANDSTONE)
                || state.is(Blocks.RED_SAND)
                || state.is(Blocks.RED_SANDSTONE)
                || state.is(Blocks.TERRACOTTA);
    }

    // 开凿黑名单：不可破坏方块、容器/方块实体、已有门与黑曜石建筑。
    private static boolean isProtected(BlockState state) {
        if (state.hasBlockEntity()) {
            return true;
        }
        return state.is(Blocks.BEDROCK)
                || state.is(Blocks.END_PORTAL)
                || state.is(Blocks.END_PORTAL_FRAME)
                || state.is(Blocks.END_GATEWAY)
                || state.is(Blocks.COMMAND_BLOCK)
                || state.is(Blocks.CHAIN_COMMAND_BLOCK)
                || state.is(Blocks.REPEATING_COMMAND_BLOCK)
                || state.is(Blocks.STRUCTURE_BLOCK)
                || state.is(Blocks.JIGSAW)
                || state.is(Blocks.BARRIER)
                || state.is(Blocks.LIGHT)
                || state.is(Blocks.NETHER_PORTAL)
                || state.is(Blocks.OBSIDIAN)
                || state.is(Blocks.CRYING_OBSIDIAN)
                || state.is(Blocks.ANCIENT_DEBRIS)
                || state.is(Blocks.NETHER_QUARTZ_ORE)
                || state.is(Blocks.NETHER_GOLD_ORE)
                || state.is(BlockTags.COAL_ORES)
                || state.is(BlockTags.IRON_ORES)
                || state.is(BlockTags.COPPER_ORES)
                || state.is(BlockTags.GOLD_ORES)
                || state.is(BlockTags.REDSTONE_ORES)
                || state.is(BlockTags.LAPIS_ORES)
                || state.is(BlockTags.DIAMOND_ORES)
                || state.is(BlockTags.EMERALD_ORES);
    }

    private static boolean isUnsafe(BlockState state) {
        return state.is(Blocks.LAVA)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE)
                || state.getFluidState().is(Fluids.LAVA)
                || state.getFluidState().is(Fluids.FLOWING_LAVA);
    }
}
