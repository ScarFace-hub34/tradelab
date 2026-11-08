package dev.tradelab;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class BotConfig {
    public enum ListMode { STACK, SINGLE }
    public enum PriceMode { AUTO_UNDERCUT, MANUAL }

    // Hedef item
    public String itemId = "minecraft:glowstone";
    public String searchName = "glowstone";        // /ah <searchName> ile aranır

    // Order tarafı (kendi seçtiğin fiyatlar - ADET BAŞINA)
    public int orderQty = 1152;
    public long minOrderUnitPrice = 0;             // bundan düşük ödeyen order'a satma

    // AH tarafı
    public ListMode listMode = ListMode.STACK;     // STACK = 64'lük, SINGLE = tek tek
    public PriceMode priceMode = PriceMode.AUTO_UNDERCUT;
    public long undercut = 1;                      // en düşük fiyatın ne kadar altı
    public long manualUnitPrice = 0;               // MANUAL modda adet başı fiyat
    public long minUnitPrice = 0;                  // kâr koruması tabanı
    public boolean profitGuard = true;
    public int listingCount = 18;                  // döngü başına ilan sayısı

    // İnsansı gecikme
    public int delayMinMs = 250;
    public int delayMaxMs = 700;
    public int cycleWaitSec = 30;

    // Sunucuya özel (KALİBRE EDİLECEK - "Menü Dök" ile bak)
    public String sellCommand = "ah sell {price}";
    public String ordersCommand = "orders";
    public String confirmItemId = "minecraft:lime_stained_glass_pane";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("tradelab.json");
    }

    public static BotConfig load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                try (Reader r = Files.newBufferedReader(p)) {
                    BotConfig c = GSON.fromJson(r, BotConfig.class);
                    if (c != null) return c;
                }
            }
        } catch (Exception e) {
            TradeLabClient.LOG.warn("Config okunamadı", e);
        }
        return new BotConfig();
    }

    public void save() {
        try (Writer w = Files.newBufferedWriter(path())) {
            GSON.toJson(this, w);
        } catch (Exception e) {
            TradeLabClient.LOG.warn("Config yazılamadı", e);
        }
    }
}
