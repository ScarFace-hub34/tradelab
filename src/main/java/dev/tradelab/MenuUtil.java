package dev.tradelab;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MenuUtil {
    private static final Pattern MONEY = Pattern.compile("\\$\\s*([\\d.,]+)\\s*([KkMmBb]?)");

    private MenuUtil() {}

    public static AbstractContainerScreen<?> screen(Minecraft mc) {
        return mc.gui.screen() instanceof AbstractContainerScreen<?> h ? h : null;
    }

    public static String id(ItemStack s) {
        return BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
    }

    public static List<String> lore(ItemStack s) {
        ItemLore l = s.get(DataComponents.LORE);
        List<String> out = new ArrayList<>();
        if (l != null) for (Component t : l.lines()) out.add(t.getString());
        return out;
    }

    /** "$1.5M" -> 1500000, "$220,000" -> 220000. Bulamazsa -1. */
    public static long parseMoney(String text) {
        Matcher m = MONEY.matcher(text);
        if (!m.find()) return -1;
        try {
            double v = Double.parseDouble(m.group(1).replace(",", ""));
            switch (m.group(2).toUpperCase()) {
                case "K" -> v *= 1_000;
                case "M" -> v *= 1_000_000;
                case "B" -> v *= 1_000_000_000;
                default -> {}
            }
            return Math.round(v);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static long firstPriceInLore(ItemStack s) {
        for (String line : lore(s)) {
            long p = parseMoney(line);
            if (p > 0) return p;
        }
        return -1;
    }

    /** Menünün üst (sunucu) kısmı; oyuncu envanteri (son 36 slot) hariç. */
    public static List<Slot> topSlots(AbstractContainerScreen<?> hs) {
        List<Slot> all = hs.getMenu().slots;
        int n = Math.max(0, all.size() - 36);
        return new ArrayList<>(all.subList(0, n));
    }

    public static List<Slot> playerSlots(AbstractContainerScreen<?> hs) {
        List<Slot> all = hs.getMenu().slots;
        int n = Math.max(0, all.size() - 36);
        return new ArrayList<>(all.subList(n, all.size()));
    }

    public static void click(Minecraft mc, AbstractContainerScreen<?> hs, Slot slot, int button, ClickType type) {
        mc.gameMode.handleInventoryMouseClick(hs.getMenu().containerId, slot.index, button, type, mc.player);
    }

    public static void say(Minecraft mc, String msg) {
        TradeLabClient.LOG.info(msg);
        // 26.2: HUD işlemleri mc.gui.hud üzerinden (aksiyon çubuğunda gösterilir)
        mc.gui.hud.setOverlayMessage(Component.literal(msg), false);
    }

    /** Menü slotlarını sohbete ve log'a döker: kalibrasyon için. */
    public static void dump(Minecraft mc) {
        AbstractContainerScreen<?> hs = screen(mc);
        if (hs == null) { say(mc, "[TradeLab] Açık menü yok."); return; }
        TradeLabClient.LOG.info("=== MENU: {} ===", hs.getTitle().getString());
        say(mc, "[TradeLab] Menü: " + hs.getTitle().getString());
        for (Slot s : topSlots(hs)) {
            ItemStack st = s.getItem();
            if (st.isEmpty()) continue;
            TradeLabClient.LOG.info("slot {} | {} x{} | {} | {}", s.index, id(st), st.getCount(),
                    st.getHoverName().getString(), lore(st));
        }
        say(mc, "[TradeLab] Detaylar latest.log dosyasına yazıldı.");
    }
}
