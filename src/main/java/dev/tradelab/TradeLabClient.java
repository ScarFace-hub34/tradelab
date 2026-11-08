package dev.tradelab;

import com.mojang.brigadier.Command;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradeLabClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("tradelab");
    public static BotConfig CONFIG;
    public static BotEngine ENGINE;

    private static boolean openPanel = false;
    private static boolean dumpArmed = false;
    private static long armedAt = 0;

    public static void armDump() { dumpArmed = true; armedAt = 0; }

    @Override
    public void onInitializeClient() {
        CONFIG = BotConfig.load();
        ENGINE = new BotEngine(CONFIG);

        // Tuş atamasına bağımlılık yok: /tradelab ile panel, /tradelab dump ile menü dökümü.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("tradelab")
                        .executes(ctx -> { openPanel = true; return Command.SINGLE_SUCCESS; })
                        .then(ClientCommandManager.literal("dump").executes(ctx -> {
                            armDump();
                            ctx.getSource().sendFeedback(net.minecraft.network.chat.Component.literal(
                                    "[TradeLab] Şimdi dökülecek menüyü aç (örn. /ah glowstone); 1 sn sonra dökülür."));
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(ClientCommandManager.literal("stop").executes(ctx -> {
                            ENGINE.stop("Komutla durduruldu");
                            return Command.SINGLE_SUCCESS;
                        }))));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (openPanel && mc.gui.screen() == null) {
                openPanel = false;
                mc.gui.setScreen(new ControlScreen());
            }
            if (dumpArmed && mc.player != null && MenuUtil.screen(mc) != null) {
                if (armedAt == 0) armedAt = System.currentTimeMillis();
                if (System.currentTimeMillis() - armedAt > 1000) { dumpArmed = false; MenuUtil.dump(mc); }
            }
            ENGINE.tick(mc);
        });
    }
}
