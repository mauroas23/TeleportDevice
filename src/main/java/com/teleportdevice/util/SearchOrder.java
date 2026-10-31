package com.teleportdevice.util;

import java.util.ArrayList;
import java.util.List;

/** Orden en que se prueban las posiciones alrededor de un destino. Sin dependencias de Minecraft. */
public final class SearchOrder {
    private SearchOrder() {}

    /**
     * Desplazamientos {dx, dy, dz}. Primero (0,0,0) (la posición exacta guardada); después anillos concéntricos de
     * radio 0..radius y, en cada columna, dy = 0, +1, -1, +2, -2... hasta ±vertical. Sin repeticiones.
     */
    public static List<int[]> offsets(int radius, int vertical) {
        List<int[]> out = new ArrayList<>();
        out.add(new int[] {0, 0, 0});
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int i = 0; i <= vertical * 2; i++) {
                        int dy = (i % 2 == 1) ? (i + 1) / 2 : -(i / 2);
                        if (r == 0 && dy == 0) continue; // ya probado como posición exacta
                        out.add(new int[] {dx, dy, dz});
                    }
                }
            }
        }
        return out;
    }
}
