package net.riley.betterportal.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

// 传送上下文：getPortalDestination 调用期间，把源门信息传给 PortalForcer mixin。
// 只在服务端主线程使用；ThreadLocal 防止嵌套/跨维度调用串线。
// Side: SERVER
public final class PortalDestinationContext {
    public static final class Source {
        private final ServerLevel level;
        private final BlockPos portalPos;
        private final BlockPos root;
        private PortalFailure failure;

        public Source(ServerLevel level, BlockPos portalPos, BlockPos root) {
            this.level = level;
            this.portalPos = portalPos.immutable();
            this.root = root.immutable();
        }

        public ServerLevel level() {
            return this.level;
        }

        public BlockPos portalPos() {
            return this.portalPos;
        }

        public BlockPos root() {
            return this.root;
        }

        public PortalFailure failure() {
            return this.failure == null ? PortalFailure.NO_DESTINATION : this.failure;
        }

        public boolean denied() {
            return this.failure == PortalFailure.DENIED;
        }
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

    public static void fail(PortalFailure failure) {
        Source source = CURRENT.get();
        if (source != null) {
            source.failure = failure;
        }
    }

    public static void clear() {
        CURRENT.remove();
    }
}
