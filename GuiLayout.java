package com.teleportdevice.util;

/**
 * Geometría del HUD calculada a partir del tamaño de pantalla ya escalado (el de {@code Screen#width/height}).
 * Sin dependencias de Minecraft: se prueba con muchas resoluciones. Mide el panel dentro de [MIN, MAX] y reparte
 * el espacio vertical sobrante en la lista, de modo que ningún control queda fuera de pantalla ni superpuesto.
 */
public final class GuiLayout {
    public static final int MAX_W = 340, MAX_H = 236;
    public static final int MIN_W = 264, MIN_H = 176;
    public static final int ROW_H = 26;
    public static final int BTN_H = 20;
    public static final int HEADER_H = 30;

    public final int left, top, width, height;
    public final int listX, listY, listW, listH;
    public final int scrollX;                 // x de la barra de scroll (4 px de ancho)
    public final int panelX, panelW;          // columna derecha
    public final int detailsY, detailsH;      // zona de texto de detalles (puede medir 0)
    public final int teleportY, renameDeleteY, cancelY, saveY; // filas de botones (alto BTN_H)
    public final int inputX, inputY, inputW;  // caja de texto
    public final int statusY, infoY;          // mensaje de estado y pie (coste / cooldown)

    private GuiLayout(int screenW, int screenH) {
        this.width = clamp(screenW - 8, MIN_W, MAX_W);
        this.height = clamp(screenH - 8, MIN_H, MAX_H);
        this.left = (screenW - width) / 2;
        this.top = (screenH - height) / 2;

        this.infoY = top + height - 20;
        this.statusY = infoY - 14;
        this.inputY = statusY - 26;

        this.panelW = clamp(width * 3 / 8, 92, 124);
        this.panelX = left + width - 12 - panelW;

        this.listX = left + 12;
        this.listW = width - 12 - 16 - panelW - 12;
        this.listY = top + 38;
        this.listH = inputY - 8 - listY;
        this.scrollX = listX + listW + 3;

        this.inputX = listX;
        this.inputW = listW;

        this.saveY = inputY;
        this.cancelY = saveY - 24;
        this.renameDeleteY = cancelY - 24;
        this.teleportY = renameDeleteY - 24;

        this.detailsY = listY + 2;
        this.detailsH = Math.max(0, teleportY - 4 - detailsY);
    }

    public static GuiLayout compute(int screenW, int screenH) {
        return new GuiLayout(screenW, screenH);
    }

    /** Ancho de cada uno de los dos botones de la fila Renombrar | Eliminar. */
    public int halfButtonW() { return (panelW - 4) / 2; }

    public int maxDetailLines() { return detailsH / 10; }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
