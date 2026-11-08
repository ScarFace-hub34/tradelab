package dev.tradelab;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Panel: Genel / Sipariş Tasarla / Ayarlar.
 * 26.x'te çizim API'si (render -> extractRenderState) değiştiği için özel çizim yok;
 * sadece hazır bileşenler kullanılır. Renkli tema, ilk başarılı derlemeden sonra eklenebilir.
 */
public class ControlScreen extends Screen {
    private static final int ACCENT = 0xB48CFF, PINK = 0xFF7AB8, GREEN = 0x5CFFA8, RED = 0xFF5A5A, TEXT = 0xE8E0FF, DIM = 0x9A90B8;

    private record Dyn(StringWidget widget, Supplier<String> text, int color) {}

    private final BotConfig cfg = TradeLabClient.CONFIG;
    private final BotEngine bot = TradeLabClient.ENGINE;
    private final List<Dyn> dyn = new ArrayList<>();
    private int tab = 0; // 0 genel, 1 sipariş, 2 ayarlar
    private int x0, w;

    public ControlScreen() { super(Component.literal("TradeLab")); }

    private static Component colored(String s, int rgb) {
        return Component.literal(s).withStyle(st -> st.withColor(rgb));
    }

    private void label(int x, int y, int width, Supplier<String> text, int color) {
        StringWidget sw = new StringWidget(colored(text.get(), color), this.font);
        sw.setPosition(x, y);
        sw.setWidth(width);
        addRenderableWidget(sw);
        dyn.add(new Dyn(sw, text, color));
    }

    private void label(int x, int y, int width, String text, int color) {
        label(x, y, width, () -> text, color);
    }

    @Override
    protected void init() {
        dyn.clear();
        w = Math.min(this.width - 20, 440);
        x0 = (this.width - w) / 2;

        label(x0, 6, w, "✦ TradeLab ✦", PINK);

        String[] names = {"GENEL", "SİPARİŞ TASARLA", "AYARLAR"};
        int tx = x0;
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            int bw = this.font.width(names[i]) + 24;
            addRenderableWidget(Button.builder(Component.literal((tab == i ? "✦ " : "") + names[i]),
                    b -> { tab = idx; rebuildWidgets(); }).bounds(tx, 20, bw, 18).build());
            tx += bw + 4;
        }

        label(x0, 46, w, "BOT DURUMU", DIM);
        label(x0, 58, w, () -> (bot.status == BotEngine.Status.RUNNING ? "● ÇALIŞIYOR  " : "● DURDURULDU  ") + bot.message, ACCENT);

        if (tab == 0) initGeneral();
        if (tab == 1) initOrderTab();
        if (tab == 2) initSettingsTab();

        // Alt buton şeridi
        int by = this.height - 26, bw = (w - 16) / 5;
        addRenderableWidget(Button.builder(Component.literal("PİYASA TARA"),
                b -> bot.runOnce(bot::queueScan, "Piyasa taranıyor")).bounds(x0, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("ORDER ÇEK"),
                b -> bot.runOnce(bot::queueFulfillOrders, "Order'lara satılıyor")).bounds(x0 + (bw + 4), by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("ENVANTERİ SAT"),
                b -> bot.runOnce(() -> { bot.queueScan(); bot.queueSell(); }, "Envanter satılıyor")).bounds(x0 + 2 * (bw + 4), by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("BOTU BAŞLAT"),
                b -> { cfg.save(); bot.startLoop(); onClose(); }).bounds(x0 + 3 * (bw + 4), by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("BOTU DURDUR"),
                b -> bot.stop("Panelden durduruldu")).bounds(x0 + 4 * (bw + 4), by, bw, 20).build());
    }

    private void initGeneral() {
        int y = 82;
        label(x0, y, w, () -> "Listelenen değer: " + money(bot.listedValue) + "    Oturum ilanı: " + bot.listedThisSession, GREEN);
        label(x0, y + 12, w, () -> "Toplam işlem: " + bot.actions + "    Envanter: x" + countInv(), GREEN);

        y += 34;
        label(x0, y, w, "AKTİF SİPARİŞ PLANI", PINK);
        label(x0, y + 14, w, () -> cfg.itemId, TEXT);
        label(x0, y + 26, w, () -> "Order: " + cfg.orderQty + " adet   İlan adedi: " + cfg.listingCount, DIM);
        label(x0, y + 38, w, () -> "İlan biçimi: " + (cfg.listMode == BotConfig.ListMode.STACK ? "64 stack" : "tekli")
                + "   Fiyat: " + (cfg.priceMode == BotConfig.PriceMode.AUTO_UNDERCUT ? "AH altı" : "manuel"), DIM);

        y += 62;
        label(x0, y, w, "PİYASA NABZI", ACCENT);
        label(x0, y + 14, w, () -> "En düşük: " + money(bot.lowest) + "    İkinci: " + money(bot.second), TEXT);
        label(x0, y + 26, w, () -> "Önerilen: " + money(bot.suggested), TEXT);
        label(x0, y + 38, w, () -> "Kâr koruması " + (cfg.profitGuard ? "AÇIK ✦" : "KAPALI"), cfg.profitGuard ? GREEN : RED);
    }

    private void initOrderTab() {
        int y = 82, fw = (w - 8) / 2;
        field(x0, y, fw, "Item ID (minecraft:glowstone)", cfg.itemId, s -> cfg.itemId = s.trim());
        field(x0 + fw + 8, y, fw, "Arama adı (/ah <ad>)", cfg.searchName, s -> cfg.searchName = s.trim());
        y += 36;
        field(x0, y, fw, "Order adedi", String.valueOf(cfg.orderQty), s -> cfg.orderQty = (int) num(s, cfg.orderQty));
        field(x0 + fw + 8, y, fw, "İlan adedi (tur başına)", String.valueOf(cfg.listingCount), s -> cfg.listingCount = (int) num(s, cfg.listingCount));
        y += 36;
        field(x0, y, fw, "Min order fiyatı (adet başı)", String.valueOf(cfg.minOrderUnitPrice), s -> cfg.minOrderUnitPrice = num(s, cfg.minOrderUnitPrice));
        field(x0 + fw + 8, y, fw, "Manuel AH fiyatı (adet başı)", String.valueOf(cfg.manualUnitPrice), s -> cfg.manualUnitPrice = num(s, cfg.manualUnitPrice));
        y += 40;
        addRenderableWidget(Button.builder(Component.literal("İlan biçimi: " + (cfg.listMode == BotConfig.ListMode.STACK ? "64 stack" : "Tekli")),
                b -> { cfg.listMode = cfg.listMode == BotConfig.ListMode.STACK ? BotConfig.ListMode.SINGLE : BotConfig.ListMode.STACK; cfg.save(); rebuildWidgets(); })
                .bounds(x0, y, fw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Fiyat: " + (cfg.priceMode == BotConfig.PriceMode.AUTO_UNDERCUT ? "AH altı (otomatik)" : "Manuel")),
                b -> { cfg.priceMode = cfg.priceMode == BotConfig.PriceMode.AUTO_UNDERCUT ? BotConfig.PriceMode.MANUAL : BotConfig.PriceMode.AUTO_UNDERCUT; cfg.save(); rebuildWidgets(); })
                .bounds(x0 + fw + 8, y, fw, 20).build());
    }

    private void initSettingsTab() {
        int y = 82, fw = (w - 8) / 2;
        field(x0, y, fw, "Gecikme min (ms)", String.valueOf(cfg.delayMinMs), s -> cfg.delayMinMs = (int) num(s, cfg.delayMinMs));
        field(x0 + fw + 8, y, fw, "Gecikme max (ms)", String.valueOf(cfg.delayMaxMs), s -> cfg.delayMaxMs = (int) num(s, cfg.delayMaxMs));
        y += 36;
        field(x0, y, fw, "Tur arası bekleme (sn)", String.valueOf(cfg.cycleWaitSec), s -> cfg.cycleWaitSec = (int) num(s, cfg.cycleWaitSec));
        field(x0 + fw + 8, y, fw, "Alt fiyat (kâr koruması)", String.valueOf(cfg.minUnitPrice), s -> cfg.minUnitPrice = num(s, cfg.minUnitPrice));
        y += 36;
        field(x0, y, fw, "AH'nin kaç altı", String.valueOf(cfg.undercut), s -> cfg.undercut = num(s, cfg.undercut));
        field(x0 + fw + 8, y, fw, "Satış komutu ({price})", cfg.sellCommand, s -> cfg.sellCommand = s.trim());
        y += 40;
        addRenderableWidget(Button.builder(Component.literal("Kâr koruması: " + (cfg.profitGuard ? "AÇIK" : "KAPALI")),
                b -> { cfg.profitGuard = !cfg.profitGuard; cfg.save(); rebuildWidgets(); }).bounds(x0, y, fw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Sonraki menüyü dök"),
                b -> { TradeLabClient.armDump(); onClose(); }).bounds(x0 + fw + 8, y, fw, 20).build());
    }

    private void field(int x, int y, int fw, String labelText, String value, Consumer<String> onChange) {
        label(x, y, fw, labelText, DIM);
        EditBox f = new EditBox(this.font, x, y + 11, fw, 18, Component.literal(labelText));
        f.setMaxLength(64);
        f.setValue(value);
        f.setResponder(s -> { onChange.accept(s); cfg.save(); });
        addRenderableWidget(f);
    }

    private static long num(String s, long fallback) {
        try { return Long.parseLong(s.trim().replace(",", "").replace("_", "")); }
        catch (Exception e) { return fallback; }
    }

    private static String money(long v) { return v > 0 ? "$" + String.format("%,d", v) : "-"; }

    private int countInv() {
        if (this.minecraft == null || this.minecraft.player == null) return 0;
        int n = 0;
        for (int i = 0; i < 36; i++) {
            var st = this.minecraft.player.getInventory().getItem(i);
            if (!st.isEmpty() && MenuUtil.id(st).equals(cfg.itemId)) n += st.getCount();
        }
        return n;
    }

    @Override
    public void tick() {
        super.tick();
        for (Dyn d : dyn) d.widget().setMessage(colored(d.text().get(), d.color()));
    }

    @Override
    public void onClose() {
        cfg.save();
        super.onClose();
    }
}
