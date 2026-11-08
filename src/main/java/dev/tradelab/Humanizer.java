package dev.tradelab;

import java.util.Random;

/** Tıklamalar arası insansı gecikme: çan eğrisi + ara sıra uzun duraksama. */
public final class Humanizer {
    private static final Random R = new Random();

    private Humanizer() {}

    public static long delay(BotConfig c) {
        int min = Math.min(c.delayMinMs, c.delayMaxMs);
        int max = Math.max(c.delayMinMs, c.delayMaxMs);
        double mean = (min + max) / 2.0;
        double sd = Math.max(1, (max - min) / 5.0);
        long d = Math.round(mean + R.nextGaussian() * sd);
        d = Math.max(min, Math.min(max, d));
        if (R.nextInt(25) == 0) d += 500 + R.nextInt(1500); // arada "düşünme" payı
        return d;
    }
}
