package com.teleportdevice.util;

/** Cálculo del cooldown a partir de tiempos de juego (ticks). Sin dependencias de Minecraft. */
public final class Cooldown {
    private Cooldown() {}

    /**
     * @param lastTeleport  tiempo de juego del último teletransporte, o un valor negativo si nunca hubo uno
     * @param cooldownSeconds duración configurada (0 = sin cooldown)
     * @param now           tiempo de juego actual
     * @return ticks restantes, siempre entre 0 y la duración total (así un reloj retrasado no bloquea para siempre)
     */
    public static int remainingTicks(long lastTeleport, int cooldownSeconds, long now) {
        long total = cooldownSeconds * 20L;
        if (total <= 0 || lastTeleport < 0) return 0;
        long remaining = lastTeleport + total - now;
        return (int) Math.max(0L, Math.min(remaining, total));
    }

    public static long secondsCeil(long ticks) {
        return (ticks + 19) / 20;
    }
}
