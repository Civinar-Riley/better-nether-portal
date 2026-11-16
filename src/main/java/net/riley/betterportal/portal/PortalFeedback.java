package net.riley.betterportal.portal;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

// 同一玩家最多每 5 秒提示一次；弱引用不会让离线玩家长期留在服务端内存里。
public final class PortalFeedback {
    private static final int INTERVAL_TICKS = 100;
    private static final Map<Player, Integer> LAST_NOTICE = new WeakHashMap<>();

    private PortalFeedback() {
    }

    public static void showFailure(Entity entity, PortalFailure failure) {
        if (!(entity instanceof Player player)) {
            return;
        }
        Integer last = LAST_NOTICE.get(player);
        if (last != null && player.tickCount - last < INTERVAL_TICKS) {
            return;
        }
        LAST_NOTICE.put(player, player.tickCount);
        player.displayClientMessage(Component.translatable(failure.translationKey()), true);
    }
}
