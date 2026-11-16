package net.riley.betterportal.gametest;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.riley.betterportal.BetterNetherPortalMod;
import net.riley.betterportal.portal.PortalFailure;
import net.riley.betterportal.portal.PortalFeedback;

@GameTestHolder(BetterNetherPortalMod.MODID)
@PrefixGameTestTemplate(false)
public final class PortalFeedbackTests {
    private PortalFeedbackTests() {
    }

    public static void register(RegisterGameTestsEvent event) {
        event.register(PortalFeedbackTests.class);
    }

    @GameTest(template = "portal_platform")
    public static void failureMessagesUseActionBarAndCooldown(GameTestHelper helper) {
        List<Component> messages = new ArrayList<>();
        Player player = new Player(helper.getLevel(), BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "portal-feedback-test")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }

            @Override
            public void displayClientMessage(Component message, boolean actionBar) {
                helper.assertTrue(actionBar, "portal failure should use the action bar");
                messages.add(message);
            }
        };
        PortalFeedback.showFailure(player, PortalFailure.OBSTRUCTED);
        player.tickCount = 99;
        PortalFeedback.showFailure(player, PortalFailure.OBSTRUCTED);
        helper.assertTrue(messages.size() == 1, "standing in a blocked portal must not spam messages");
        player.tickCount = 100;
        PortalFeedback.showFailure(player, PortalFailure.BORDER);
        helper.assertTrue(messages.size() == 2, "failure notice should be available again after five seconds");
        helper.assertTrue(messages.get(0).getContents() instanceof TranslatableContents first
                && first.getKey().equals(PortalFailure.OBSTRUCTED.translationKey()), "blocked exit should report its reason");
        helper.assertTrue(messages.get(1).getContents() instanceof TranslatableContents second
                && second.getKey().equals(PortalFailure.BORDER.translationKey()), "later notice should use the current reason");
        helper.succeed();
    }
}
