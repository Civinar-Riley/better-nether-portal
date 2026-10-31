package net.riley.betterportal.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

// 传送上下文：getPortalDestination 调用期间，把源门信息传给 PortalForcer mixin。
// 只在服务端主线程使用；ThreadLocal 防止嵌套/跨维度调用串线。
// Side: SERVER
public final class PortalDestinationContext {
    public record Source(ServerLevel level, BlockPos portalPos, BlockPos root) {
    }

    private static final ThreadLocal<Source> CURRENT = new ThreadLocal<>();

    private PortalDestinationContext() {
    }

    public static void push(Source source) {
        CURRENT.set(source);
    }

    public static Source peek() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
