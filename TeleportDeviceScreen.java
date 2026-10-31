package com.teleportdevice.client;

import com.teleportdevice.network.ActionPacket;
import com.teleportdevice.network.ActionType;
import com.teleportdevice.network.LocationDto;
import com.teleportdevice.network.NetworkHandler;
import com.teleportdevice.network.SyncPacket;
import com.teleportdevice.registry.ModItems;
import com.teleportdevice.util.Cooldown;
import com.teleportdevice.util.GuiLayout;
import com.teleportdevice.util.NameRules;
import com.teleportdevice.util.ScrollModel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * GUI del dispositivo. Es sólo una VISTA: cada botón envía una petición al servidor, que valida y responde con un
 * SyncPacket. Aquí nunca se decide nada. La geometría (tamaños, posiciones) y el scroll viven en clases puras
 * ({@link GuiLayout}, {@link ScrollModel}) que se adaptan al tamaño de pantalla y están probadas aparte.
 */
public class TeleportDeviceScreen extends Screen {
    private static final String KEY_SAVED = "teleportdevice.msg.saved";
    private static final String KEY_RENAMED = "teleportdevice.msg.renamed";
    private static final int WHEEL_PIXELS = 13;

    private List<LocationDto> locations = new ArrayList<>();
    private int maxLocations;
    private boolean allowCross;
    private String costItem = "";
    private int costCount;
    private long cooldownEnd;
    private Component status = Component.empty();
    private boolean statusOk;

    private @Nullable UUID selected;
    private @Nullable UUID renaming;
    private boolean confirmDelete;
    private boolean draggingScrollbar;
    private boolean focusNameBox;

    private final ScrollModel scroll = new ScrollModel(GuiLayout.ROW_H);
    private GuiLayout lay = GuiLayout.compute(427, 240);

    private EditBox nameBox;
    private Button teleportBtn, renameBtn, deleteBtn, saveBtn, cancelBtn;

    public TeleportDeviceScreen(SyncPacket packet) {
        super(Component.translatable("teleportdevice.gui.title"));
        applySync(packet);
    }

    // ------------------------------------------------------------------ Estado

    public void applySync(SyncPacket p) {
        Set<UUID> before = new HashSet<>();
        for (LocationDto l : locations) before.add(l.id());

        this.locations = new ArrayList<>(p.locations());
        this.maxLocations = p.maxLocations();
        this.allowCross = p.allowCrossDimension();
        this.costItem = p.costItem();
        this.costCount = p.costCount();
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level != null ? mc.level.getGameTime() : 0L;
        this.cooldownEnd = now + p.cooldownTicks();

        String key = p.statusKey();
        if (!key.isEmpty()) {
            this.status = ClientHooks.statusComponent(key, p.statusArg());
            this.statusOk = p.statusOk();
        }

        scroll.configure(lay.listH, locations.size());

        if (p.statusOk() && KEY_SAVED.equals(key)) {
            // Se selecciona y se muestra la ubicación recién creada.
            for (LocationDto l : locations) {
                if (!before.contains(l.id())) selected = l.id();
            }
            clearInput();
            scroll.ensureVisible(indexOf(selected));
        } else if (p.statusOk() && KEY_RENAMED.equals(key)) {
            renaming = null;
            clearInput();
        }
        if (selected != null && indexOf(selected) < 0) selected = null;
        if (renaming != null && indexOf(renaming) < 0) renaming = null;
        confirmDelete = false;
    }

    private int indexOf(@Nullable UUID id) {
        if (id == null) return -1;
        for (int i = 0; i < locations.size(); i++) {
            if (locations.get(i).id().equals(id)) return i;
        }
        return -1;
    }

    @Nullable
    private LocationDto selectedLoc() {
        int i = indexOf(selected);
        return i < 0 ? null : locations.get(i);
    }

    private void clearInput() {
        if (nameBox != null) nameBox.setValue("");
    }

    private String currentDimension() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? "" : mc.level.dimension().location().toString();
    }

    private boolean isDimensionLocked(LocationDto l) {
        return !allowCross && !l.dimension().equals(currentDimension());
    }

    // ------------------------------------------------------------------ Widgets

    @Override
    protected void init() {
        lay = GuiLayout.compute(this.width, this.height);

        String previous = nameBox != null ? nameBox.getValue() : "";
        boolean hadFocus = nameBox != null && nameBox.isFocused();

        nameBox = new EditBox(this.font, lay.inputX, lay.inputY, lay.inputW, GuiLayout.BTN_H,
                Component.translatable("teleportdevice.gui.name_hint"));
        nameBox.setMaxLength(NameRules.MAX_LENGTH);
        nameBox.setValue(previous);
        addRenderableWidget(nameBox);

        teleportBtn = addRenderableWidget(Button.builder(Component.translatable("teleportdevice.gui.teleport"),
                b -> teleportSelected()).bounds(lay.panelX, lay.teleportY, lay.panelW, GuiLayout.BTN_H).build());
        renameBtn = addRenderableWidget(Button.builder(Component.translatable("teleportdevice.gui.rename"),
                b -> startRename()).bounds(lay.panelX, lay.renameDeleteY, lay.halfButtonW(), GuiLayout.BTN_H).build());
        deleteBtn = addRenderableWidget(Button.builder(Component.translatable("teleportdevice.gui.delete"),
                b -> onDelete()).bounds(lay.panelX + lay.halfButtonW() + 4, lay.renameDeleteY, lay.halfButtonW(),
                GuiLayout.BTN_H).build());
        cancelBtn = addRenderableWidget(Button.builder(Component.translatable("teleportdevice.gui.cancel"),
                b -> cancelRename()).bounds(lay.panelX, lay.cancelY, lay.panelW, GuiLayout.BTN_H).build());
        saveBtn = addRenderableWidget(Button.builder(Component.translatable("teleportdevice.gui.save_here"),
                b -> trySave()).bounds(lay.panelX, lay.saveY, lay.panelW, GuiLayout.BTN_H).build());

        scroll.configure(lay.listH, locations.size());
        if (hadFocus || renaming != null) setFocused(nameBox);
        updateButtons();
    }

    private void updateButtons() {
        if (teleportBtn == null) return;
        LocationDto sel = selectedLoc();
        boolean has = sel != null && renaming == null;
        teleportBtn.active = has && !isDimensionLocked(sel);
        renameBtn.active = has;
        deleteBtn.active = has;
        deleteBtn.setMessage(Component.translatable(confirmDelete
                ? "teleportdevice.gui.delete_confirm" : "teleportdevice.gui.delete"));
        cancelBtn.visible = renaming != null;
        boolean hasText = !nameBox.getValue().trim().isEmpty();
        saveBtn.active = hasText && (renaming != null || locations.size() < maxLocations);
        saveBtn.setMessage(Component.translatable(renaming != null
                ? "teleportdevice.gui.apply_name" : "teleportdevice.gui.save_here"));
    }

    private void send(ActionPacket packet) {
        NetworkHandler.CHANNEL.sendToServer(packet);
    }

    private void teleportSelected() {
        if (selected == null) return;
        send(ActionPacket.of(ActionType.TELEPORT, selected));
    }

    private void trySave() {
        String text = NameRules.sanitize(nameBox.getValue());
        if (text == null) {
            status = Component.translatable("teleportdevice.msg.name_invalid");
            statusOk = false;
            return;
        }
        if (renaming != null) {
            send(new ActionPacket(ActionType.RENAME, renaming, text));
        } else {
            send(new ActionPacket(ActionType.SAVE, new UUID(0L, 0L), text));
        }
    }

    private void startRename() {
        LocationDto sel = selectedLoc();
        if (sel == null) return;
        renaming = sel.id();
        nameBox.setValue(sel.name());
        // El clic en el botón le quita el foco a la caja al terminar; se devuelve después (mouseClicked / tick).
        focusNameBox = true;
    }

    private void cancelRename() {
        renaming = null;
        clearInput();
    }

    private void onDelete() {
        if (selected == null) return;
        if (!confirmDelete) {
            confirmDelete = true;
        } else {
            confirmDelete = false;
            send(ActionPacket.of(ActionType.DELETE, selected));
        }
    }

    private void select(UUID id) {
        if (id.equals(selected)) return;
        selected = id;
        confirmDelete = false;
        status = Component.empty();
        if (renaming != null) cancelRename();
    }

    private void moveSelection(int delta) {
        if (locations.isEmpty()) return;
        int idx = indexOf(selected);
        if (idx < 0) idx = delta > 0 ? -1 : locations.size();
        int next = Mth.clamp(idx + delta, 0, locations.size() - 1);
        select(locations.get(next).id());
        scroll.ensureVisible(next);
    }

    // ------------------------------------------------------------------ Entrada

    private boolean inList(double mx, double my) {
        return mx >= lay.listX && mx < lay.listX + lay.listW && my >= lay.listY && my < lay.listY + lay.listH;
    }

    private boolean inScrollbar(double mx, double my) {
        return mx >= lay.scrollX - 2 && mx <= lay.scrollX + 6 && my >= lay.listY && my <= lay.listY + lay.listH;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        boolean boxFocused = nameBox != null && nameBox.isFocused();
        if (boxFocused && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            trySave();
            return true;
        }
        if (!boxFocused) {
            if (key == GLFW.GLFW_KEY_UP) { moveSelection(-1); return true; }
            if (key == GLFW.GLFW_KEY_DOWN) { moveSelection(1); return true; }
            if (key == GLFW.GLFW_KEY_PAGE_UP) { moveSelection(-scroll.rowsPerPage()); return true; }
            if (key == GLFW.GLFW_KEY_PAGE_DOWN) { moveSelection(scroll.rowsPerPage()); return true; }
            if (key == GLFW.GLFW_KEY_HOME) { moveSelection(-locations.size()); return true; }
            if (key == GLFW.GLFW_KEY_END) { moveSelection(locations.size()); return true; }
            if (Minecraft.getInstance().options.keyInventory.matches(key, scan)) {
                onClose(); // la tecla de inventario también cierra el HUD (cuando no se está escribiendo)
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (scroll.scrollable() && inScrollbar(mx, my)) {
                draggingScrollbar = true;
                scroll.dragTo(my, lay.listY, lay.listH);
                return true;
            }
            if (inList(mx, my)) {
                int idx = scroll.indexAt(my, lay.listY);
                if (idx >= 0 && idx < locations.size()) {
                    select(locations.get(idx).id());
                    Minecraft.getInstance().getSoundManager()
                            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
                setFocused(null);
                return true;
            }
        }
        boolean handled = super.mouseClicked(mx, my, button);
        if (focusNameBox) {
            focusNameBox = false;
            setFocused(nameBox);
        }
        return handled;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingScrollbar) {
            scroll.dragTo(my, lay.listY, lay.listH);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll.wheel(delta, WHEEL_PIXELS);
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Item device = ModItems.TELEPORT_DEVICE.get();
        if (!mc.player.getMainHandItem().is(device) && !mc.player.getOffhandItem().is(device)) {
            onClose();
            return;
        }
        if (nameBox != null) nameBox.tick();
        if (focusNameBox) {
            focusNameBox = false;
            setFocused(nameBox);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ Dibujo

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        updateButtons();
        this.renderBackground(g);
        GuiLayout L = lay;

        // Marco y fondo
        g.fill(L.left - 2, L.top - 2, L.left + L.width + 2, L.top + L.height + 2, 0xFF05030C);
        g.fillGradient(L.left, L.top, L.left + L.width, L.top + L.height, 0xFF271C44, 0xFF140E26);
        g.fill(L.left, L.top, L.left + L.width, L.top + 2, 0xFF8A66E6);
        g.fill(L.left, L.top + GuiLayout.HEADER_H, L.left + L.width, L.top + GuiLayout.HEADER_H + 1, 0xFF3A2C5C);

        // Cabecera
        g.renderItem(new ItemStack(ModItems.TELEPORT_DEVICE.get()), L.left + 12, L.top + 8);
        g.drawString(this.font, this.title, L.left + 34, L.top + 12, 0xFFEBDDFF, true);
        String counter = locations.size() + " / " + maxLocations;
        boolean full = maxLocations > 0 && locations.size() >= maxLocations;
        g.drawString(this.font, counter, L.left + L.width - 12 - this.font.width(counter), L.top + 12,
                full ? 0xFFFF9C7B : 0xFF9C93B8, false);

        renderList(g, mouseX, mouseY);
        renderDetails(g);

        super.render(g, mouseX, mouseY, partialTick); // botones y caja de texto

        if (nameBox.getValue().isEmpty() && !nameBox.isFocused()) {
            g.drawString(this.font, Component.translatable("teleportdevice.gui.name_hint"),
                    nameBox.getX() + 4, nameBox.getY() + 6, 0xFF707070, false);
        }

        renderStatus(g);
        renderFooter(g);
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        GuiLayout L = lay;
        int lx = L.listX, ly = L.listY, lw = L.listW, lh = L.listH;
        g.fill(lx - 1, ly - 1, lx + lw + 1, ly + lh + 1, 0xFF3A2C5C);
        g.fill(lx, ly, lx + lw, ly + lh, 0xFF0E0A1A);

        if (locations.isEmpty()) {
            g.drawCenteredString(this.font, ellipsize(I18n.get("teleportdevice.gui.empty_1"), lw - 8),
                    lx + lw / 2, ly + lh / 2 - 10, 0xFFB8AEDB);
            g.drawCenteredString(this.font, ellipsize(I18n.get("teleportdevice.gui.empty_2"), lw - 8),
                    lx + lw / 2, ly + lh / 2 + 2, 0xFF7E7599);
            return;
        }

        int hovered = inList(mouseX, mouseY) ? scroll.indexAt(mouseY, ly) : -1;
        g.enableScissor(lx, ly, lx + lw, ly + lh);
        int first = scroll.firstVisible();
        int last = scroll.lastVisible();
        for (int i = first; i <= last; i++) {
            int ry = ly + i * GuiLayout.ROW_H - scroll.offset();
            LocationDto loc = locations.get(i);
            boolean sel = loc.id().equals(selected);
            int bg = sel ? 0xFF4A2F8F : (i == hovered ? 0xFF2B2050 : 0xFF181030);
            g.fill(lx + 1, ry + 1, lx + lw - 1, ry + GuiLayout.ROW_H - 1, bg);
            g.fill(lx + 1, ry + 1, lx + 4, ry + GuiLayout.ROW_H - 1, dimensionColor(loc.dimension()));
            g.drawString(this.font, ellipsize(loc.name(), lw - 18), lx + 9, ry + 4, 0xFFFFFFFF, false);
            String sub = dimensionName(loc.dimension()) + "  " + coords(loc);
            g.drawString(this.font, ellipsize(sub, lw - 18), lx + 9, ry + 14, 0xFF9C93B8, false);
        }
        g.disableScissor();

        if (scroll.scrollable()) {
            int thumb = scroll.thumbHeight(lh);
            int ty = ly + scroll.thumbOffset(lh);
            g.fill(L.scrollX, ly, L.scrollX + 4, ly + lh, 0xFF0E0A1A);
            g.fill(L.scrollX, ty, L.scrollX + 4, ty + thumb, draggingScrollbar ? 0xFFB79BFF : 0xFF7C5CD6);
        }
    }

    private void renderDetails(GuiGraphics g) {
        GuiLayout L = lay;
        int lines = L.maxDetailLines();
        if (lines <= 0) return; // pantalla muy baja: la lista ya muestra nombre, dimensión y coordenadas
        int px = L.panelX;
        int y = L.detailsY;
        LocationDto sel = selectedLoc();
        if (sel == null) {
            for (FormattedCharSequence line : this.font.split(
                    Component.translatable("teleportdevice.gui.select_hint"), L.panelW)) {
                if (lines-- <= 0) break;
                g.drawString(this.font, line, px, y, 0xFF9C93B8, false);
                y += 10;
            }
            return;
        }
        g.drawString(this.font, ellipsize(sel.name(), L.panelW), px, y, 0xFFFFFFFF, true);
        y += 10;
        lines--;
        if (lines > 0) {
            g.drawString(this.font, ellipsize(dimensionName(sel.dimension()), L.panelW), px, y,
                    dimensionColor(sel.dimension()), false);
            y += 10;
            lines--;
        }
        if (lines >= 3) {
            g.drawString(this.font, "X  " + Mth.floor(sel.x()), px, y, 0xFFD8D0F0, false);
            g.drawString(this.font, "Y  " + Mth.floor(sel.y()), px, y + 10, 0xFFD8D0F0, false);
            g.drawString(this.font, "Z  " + Mth.floor(sel.z()), px, y + 20, 0xFFD8D0F0, false);
        } else if (lines >= 1) {
            g.drawString(this.font, ellipsize(coords(sel), L.panelW), px, y, 0xFFD8D0F0, false);
        }
    }

    private void renderStatus(GuiGraphics g) {
        GuiLayout L = lay;
        String text = status.getString();
        int color = statusOk ? 0xFF7CFC9A : 0xFFFF7B7B;
        LocationDto sel = selectedLoc();
        if (text.isEmpty() && sel != null && isDimensionLocked(sel)) {
            text = I18n.get("teleportdevice.gui.dimension_locked");
            color = 0xFFFF7B7B;
        }
        if (!text.isEmpty()) {
            g.drawCenteredString(this.font, ellipsize(text, L.width - 24), L.left + L.width / 2, L.statusY, color);
        }
    }

    private void renderFooter(GuiGraphics g) {
        GuiLayout L = lay;
        Component costText = (costCount > 0 && !costItem.isEmpty())
                ? Component.translatable("teleportdevice.gui.cost", costCount, ClientHooks.itemName(costItem))
                : Component.translatable("teleportdevice.gui.free");
        g.drawString(this.font, costText, L.left + 12, L.infoY, 0xFF9C93B8, false);

        Minecraft mc = Minecraft.getInstance();
        long remaining = cooldownEnd - (mc.level != null ? mc.level.getGameTime() : 0L);
        Component cd = remaining > 0
                ? Component.translatable("teleportdevice.gui.cooldown_wait", Cooldown.secondsCeil(remaining))
                : Component.translatable("teleportdevice.gui.cooldown_ready");
        g.drawString(this.font, cd, L.left + L.width - 12 - this.font.width(cd), L.infoY,
                remaining > 0 ? 0xFFFFC857 : 0xFF7CFC9A, false);
    }

    // ------------------------------------------------------------------ Helpers

    /** Recorta con "..." si el texto no cabe en maxWidth píxeles. */
    private String ellipsize(String s, int maxWidth) {
        if (this.font.width(s) <= maxWidth) return s;
        int dots = this.font.width("...");
        return this.font.plainSubstrByWidth(s, Math.max(0, maxWidth - dots)) + "...";
    }

    private static String coords(LocationDto l) {
        return Mth.floor(l.x()) + ", " + Mth.floor(l.y()) + ", " + Mth.floor(l.z());
    }

    private static String dimensionName(String dim) {
        String key = "teleportdevice.dim." + dim.replace(':', '.');
        return I18n.exists(key) ? I18n.get(key) : dim;
    }

    private static int dimensionColor(String dim) {
        return switch (dim) {
            case "minecraft:overworld" -> 0xFF5FD35F;
            case "minecraft:the_nether" -> 0xFFE05A3A;
            case "minecraft:the_end" -> 0xFFB58AF0;
            default -> 0xFF9AA0A6;
        };
    }
}
