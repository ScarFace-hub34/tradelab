package dev.tradelab;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ClickType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Adım kuyruğu (step queue) tabanlı bot.
 * Her adım true dönerse biter ve ardından insansı gecikme beklenir.
 */
public class BotEngine {
    public interface Step { boolean run(Minecraft mc) throws Exception; }

    public enum Status { STOPPED, RUNNING }

    private final BotConfig cfg;
    private final Deque<Step> queue = new ArrayDeque<>();
    private long nextActionAt = 0;
    private long stepDeadline = 0;
    private boolean loop = false;
    private boolean skipListing = false;

    // Panelde gösterilen bilgiler
    public volatile Status status = Status.STOPPED;
    public volatile String message = "Panelden durduruldu";
    public volatile long lowest = -1, second = -1, suggested = -1;
    public volatile int listedThisSession = 0;
    public volatile long listedValue = 0;
    public volatile int actions = 0;

    public BotEngine(BotConfig cfg) { this.cfg = cfg; }

    // ---------- Kontrol ----------
    public void startLoop() {
        queue.clear();
        loop = true;
        status = Status.RUNNING;
        message = "Döngü çalışıyor";
        queueScan();
        queueSell();
    }

    public void runOnce(Runnable queuer, String msg) {
        queue.clear();
        loop = false;
        status = Status.RUNNING;
        message = msg;
        queuer.run();
    }

    public void stop(String why) {
        queue.clear();
        loop = false;
        status = Status.STOPPED;
        message = why;
        stepDeadline = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.gui.screen() instanceof AbstractContainerScreen<?>) mc.player.closeContainer();
    }

    // ---------- Tick ----------
    public void tick(Minecraft mc) {
        if (status != Status.RUNNING) return;
        if (mc.player == null || mc.gameMode == null) { stop("Oyundan ayrıldı"); return; }
        if (mc.gui.screen() instanceof ControlScreen) return; // panel açıkken bekle

        long now = System.currentTimeMillis();
        if (now < nextActionAt) return;

        Step s = queue.peek();
        if (s == null) {
            if (loop) {
                nextActionAt = now + cfg.cycleWaitSec * 1000L + Humanizer.delay(cfg);
                queueScan();
                queueSell();
                message = "Sonraki tur bekleniyor";
            } else {
                status = Status.STOPPED;
                message = "Tamamlandı";
            }
            return;
        }
        if (stepDeadline == 0) stepDeadline = now + 15_000;
        try {
            if (s.run(mc)) {
                queue.poll();
                stepDeadline = 0;
                actions++;
                delayNow();
            } else if (now > stepDeadline) {
                stop("Zaman aşımı - menü beklenen gibi değil (Menü Dök ile kontrol et)");
            }
        } catch (Exception ex) {
            TradeLabClient.LOG.warn("Adım hatası", ex);
            stop("Hata: " + ex.getMessage());
        }
    }

    private void delayNow() {
        nextActionAt = System.currentTimeMillis() + Humanizer.delay(cfg);
    }

    // ---------- Akışlar ----------
    public void queueScan() {
        queue.add(cmd("ah " + cfg.searchName));
        queue.add(waitScreen());
        queue.add(waitMs(700));      // eşyaların yüklenmesi için
        queue.add(this::scanAh);
        queue.add(closeScreen());
    }

    public void queueSell() {
        queue.add(this::planListings);
    }

    /** Mevcut order'lara eşya sat (envanterden). */
    public void queueFulfillOrders() {
        queue.add(cmd(cfg.ordersCommand));
        queue.add(waitScreen());
        queue.add(waitMs(700));
        queue.add(this::pickOrder);
        queue.add(waitMs(700));
        queue.add(this::shiftClickInventoryItems);
        queue.add(closeScreen());
    }

    // ---------- Adım fabrikaları ----------
    private Step cmd(String command) {
        return mc -> { mc.player.connection.sendCommand(command); return true; };
    }

    private Step waitScreen() {
        return mc -> MenuUtil.screen(mc) != null;
    }

    private Step waitMs(long ms) {
        long[] until = {0};
        return mc -> {
            long now = System.currentTimeMillis();
            if (until[0] == 0) until[0] = now + ms;
            return now >= until[0];
        };
    }

    private Step closeScreen() {
        return mc -> { if (mc.gui.screen() != null) mc.player.closeContainer(); return true; };
    }

    // ---------- AH tarama ----------
    private boolean scanAh(Minecraft mc) {
        AbstractContainerScreen<?> hs = MenuUtil.screen(mc);
        if (hs == null) return false;
        List<Long> units = new ArrayList<>();
        for (Slot s : MenuUtil.topSlots(hs)) {
            ItemStack st = s.getItem();
            if (st.isEmpty() || !MenuUtil.id(st).equals(cfg.itemId)) continue;
            long price = MenuUtil.firstPriceInLore(st);
            if (price > 0) units.add(Math.max(1, price / st.getCount()));
        }
        Collections.sort(units);
        lowest = units.isEmpty() ? -1 : units.get(0);
        second = units.size() > 1 ? units.get(1) : lowest;
        suggested = lowest > 0 ? lowest - cfg.undercut : -1;
        message = lowest > 0 ? "Piyasa tarandı" : "AH'de eşleşen ilan bulunamadı";
        return true;
    }

    // ---------- İlan planlama ----------
    private boolean planListings(Minecraft mc) {
        skipListing = false;
        long unit;
        if (cfg.priceMode == BotConfig.PriceMode.MANUAL) {
            unit = cfg.manualUnitPrice;
        } else {
            if (lowest <= 0) { message = "Fiyat bulunamadı, tur atlandı"; return true; }
            unit = lowest - cfg.undercut;
        }
        if (unit <= 0) { message = "Geçersiz fiyat"; return true; }
        if (cfg.profitGuard && cfg.minUnitPrice > 0 && unit < cfg.minUnitPrice) {
            message = "Kâr koruması: " + unit + " < " + cfg.minUnitPrice;
            return true;
        }
        for (int i = 0; i < cfg.listingCount; i++) {
            queue.add(this::holdItem);
            queue.add(sellHeld(unit));
            queue.add(waitMs(600));
            queue.add(confirmIfOpen());
        }
        message = "İlanlar veriliyor (adet başı " + unit + ")";
        return true;
    }

    private boolean holdItem(Minecraft mc) {
        if (skipListing) return true;
        Inventory inv = mc.player.getInventory();
        int found = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty() || !MenuUtil.id(st).equals(cfg.itemId)) continue;
            if (cfg.listMode == BotConfig.ListMode.SINGLE || st.getCount() == st.getMaxStackSize()) { found = i; break; }
        }
        if (found < 0) { skipListing = true; message = "Envanterde satılacak eşya kalmadı"; return true; }

        int hotbar = found;
        if (found >= 9) {
            // Ana envanterden seçili hotbar slotuna takas et (PlayerScreenHandler: 9-35 envanter, 36-44 hotbar)
            hotbar = inv.getSelectedSlot();
            mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, found, hotbar, ClickType.SWAP, mc.player);
        } else {
            inv.setSelectedSlot(found);
        }

        if (cfg.listMode == BotConfig.ListMode.SINGLE && inv.getItem(hotbar).getCount() > 1) {
            splitOne(mc, hotbar);
        }
        return true;
    }

    /** Hotbar'daki stack'ten 1 adet ayırıp boş bir hotbar slotuna koyar ve onu seçer. */
    private void splitOne(Minecraft mc, int srcHotbar) {
        Inventory inv = mc.player.getInventory();
        int empty = -1;
        for (int i = 0; i < 9; i++) if (inv.getItem(i).isEmpty()) { empty = i; break; }
        if (empty < 0) { skipListing = true; message = "SINGLE için boş hotbar slotu gerekli"; return; }
        int sync = mc.player.inventoryMenu.containerId;
        mc.gameMode.handleInventoryMouseClick(sync, 36 + srcHotbar, 0, ClickType.PICKUP, mc.player); // al
        mc.gameMode.handleInventoryMouseClick(sync, 36 + empty, 1, ClickType.PICKUP, mc.player);     // 1 adet bırak
        mc.gameMode.handleInventoryMouseClick(sync, 36 + srcHotbar, 0, ClickType.PICKUP, mc.player); // kalanı geri koy
        inv.setSelectedSlot(empty);
    }

    private Step sellHeld(long unit) {
        return mc -> {
            if (skipListing) return true;
            ItemStack h = mc.player.getMainHandItem();
            if (h.isEmpty() || !MenuUtil.id(h).equals(cfg.itemId)) { skipListing = true; return true; }
            long total = unit * h.getCount();
            mc.player.connection.sendCommand(cfg.sellCommand.replace("{price}", Long.toString(total)));
            listedThisSession++;
            listedValue += total;
            return true;
        };
    }

    private Step confirmIfOpen() {
        return mc -> {
            AbstractContainerScreen<?> hs = MenuUtil.screen(mc);
            if (hs == null) return true;
            for (Slot s : MenuUtil.topSlots(hs)) {
                if (!s.getItem().isEmpty() && MenuUtil.id(s.getItem()).equals(cfg.confirmItemId)) {
                    MenuUtil.click(mc, hs, s, 0, ClickType.PICKUP);
                    break;
                }
            }
            mc.player.closeContainer();
            return true;
        };
    }

    // ---------- Order'a satış ----------
    private boolean pickOrder(Minecraft mc) {
        AbstractContainerScreen<?> hs = MenuUtil.screen(mc);
        if (hs == null) return false;
        for (Slot s : MenuUtil.topSlots(hs)) {
            ItemStack st = s.getItem();
            if (st.isEmpty() || !MenuUtil.id(st).equals(cfg.itemId)) continue;
            long price = MenuUtil.firstPriceInLore(st); // KALİBRE: order lore'unda adet başı fiyat varsayıldı
            if (price > 0 && price < cfg.minOrderUnitPrice) continue;
            MenuUtil.click(mc, hs, s, 0, ClickType.PICKUP);
            message = "Order seçildi";
            return true;
        }
        message = "Uygun order bulunamadı";
        queue.clear(); // kalan adımları iptal et
        queue.add(closeScreen());
        return true;
    }

    private boolean shiftClickInventoryItems(Minecraft mc) {
        AbstractContainerScreen<?> hs = MenuUtil.screen(mc);
        if (hs == null) return true;
        for (Slot s : MenuUtil.playerSlots(hs)) {
            ItemStack st = s.getItem();
            if (!st.isEmpty() && MenuUtil.id(st).equals(cfg.itemId)) {
                MenuUtil.click(mc, hs, s, 0, ClickType.QUICK_MOVE);
                delayNow();
                return false; // her tıklama arası gecikme
            }
        }
        return true;
    }
}
