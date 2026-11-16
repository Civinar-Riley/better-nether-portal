package net.riley.betterportal.mixin;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.riley.betterportal.portal.CustomPortalShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 点火生门：原版只认矩形框（且 PortalSpawnEvent 仅在已找到矩形时触发），
// 这里在原版逻辑之前先做任意形状扫描，命中即填门并跳过原版判定。
// 打火石/火绒盒/发射器/火焰蔓延最终都走 BaseFireBlock#onPlace，全部覆盖。
@Mixin(BaseFireBlock.class)
public abstract class BaseFireBlockMixin {

    @Inject(method = "onPlace", at = @At("HEAD"), cancellable = true)
    private void riley$trySpawnCustomPortal(BlockState state, Level level, BlockPos pos,
                                            BlockState oldState, boolean isMoving, CallbackInfo ci) {
        // 与原版守卫一致：火→火（蔓延续燃）不触发生门判定
        if (oldState.is(state.getBlock())) {
            return;
        }
        if (!CustomPortalShape.inPortalDimension(level)) {
            return;
        }
        if (level.isClientSide()) {
            ci.cancel();
            return;
        }
        Optional<CustomPortalShape.Found> found = CustomPortalShape.findEmptyPortalShape(level, pos);
        if (found.isPresent()) {
            found.get().fillPortalBlocks(level);
        } else if (!state.canSurvive(level, pos)) {
            level.removeBlock(pos, false);
        }
        // 自定义扫描失败也不能继续走原版矩形判定，否则会绕过配置上限。
        ci.cancel();
    }
}
