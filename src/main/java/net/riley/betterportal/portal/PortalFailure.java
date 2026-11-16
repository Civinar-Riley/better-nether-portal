package net.riley.betterportal.portal;

// 传送失败原因，由服务端通过操作栏提示玩家。
public enum PortalFailure {
    NO_DESTINATION("no_destination"),
    OBSTRUCTED("obstructed"),
    BORDER("border"),
    LIMIT("limit"),
    DENIED("denied");

    private final String translationKey;

    PortalFailure(String name) {
        this.translationKey = "message.better_nether_portal." + name;
    }

    public String translationKey() {
        return this.translationKey;
    }
}
