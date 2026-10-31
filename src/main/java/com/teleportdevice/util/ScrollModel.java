package com.teleportdevice.util;

/** Modelo de desplazamiento de una lista de filas de altura fija. Sin dependencias de Minecraft. */
public final class ScrollModel {
    private static final int MIN_THUMB = 14;

    private final int rowHeight;
    private int viewHeight = 1;
    private int itemCount;
    private int offset;

    public ScrollModel(int rowHeight) {
        this.rowHeight = Math.max(1, rowHeight);
    }

    /** Debe llamarse cada vez que cambia el tamaño visible o la cantidad de filas. */
    public void configure(int viewHeight, int itemCount) {
        this.viewHeight = Math.max(1, viewHeight);
        this.itemCount = Math.max(0, itemCount);
        this.offset = clamp(offset);
    }

    public int offset() { return offset; }

    public int maxScroll() { return Math.max(0, itemCount * rowHeight - viewHeight); }

    public boolean scrollable() { return maxScroll() > 0; }

    private int clamp(int value) { return Math.max(0, Math.min(value, maxScroll())); }

    public void setOffset(int value) { offset = clamp(value); }

    /** Rueda del ratón: delta > 0 sube, delta < 0 baja. */
    public void wheel(double delta, int pixelsPerNotch) {
        offset = clamp(offset - (int) Math.round(delta * pixelsPerNotch));
    }

    public int thumbHeight(int trackHeight) {
        if (!scrollable()) return trackHeight;
        int content = itemCount * rowHeight;
        int thumb = (int) ((long) trackHeight * viewHeight / content);
        return Math.max(Math.min(MIN_THUMB, trackHeight), Math.min(thumb, trackHeight));
    }

    public int thumbOffset(int trackHeight) {
        if (!scrollable()) return 0;
        int free = trackHeight - thumbHeight(trackHeight);
        return (int) Math.round(free * (offset / (double) maxScroll()));
    }

    /** Arrastre de la barra: centra el pulgar bajo el cursor. */
    public void dragTo(double mouseY, int trackTop, int trackHeight) {
        if (!scrollable()) return;
        int thumb = thumbHeight(trackHeight);
        double t = (mouseY - trackTop - thumb / 2.0) / Math.max(1, trackHeight - thumb);
        offset = clamp((int) Math.round(t * maxScroll()));
    }

    /** Índice de la fila bajo el cursor, o -1 si no hay ninguna. */
    public int indexAt(double mouseY, int listTop) {
        int i = (int) Math.floor((mouseY - listTop + offset) / rowHeight);
        return (i < 0 || i >= itemCount) ? -1 : i;
    }

    public void ensureVisible(int index) {
        if (index < 0 || index >= itemCount) return;
        int top = index * rowHeight;
        int bottom = top + rowHeight;
        if (top < offset) offset = top;
        else if (bottom > offset + viewHeight) offset = bottom - viewHeight;
        offset = clamp(offset);
    }

    public int firstVisible() { return Math.min(itemCount, offset / rowHeight); }

    public int lastVisible() { return Math.min(itemCount - 1, (offset + viewHeight - 1) / rowHeight); }

    public int rowsPerPage() { return Math.max(1, viewHeight / rowHeight); }
}
