package net.squxso.lumina.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-game screenshot gallery — a scrollable thumbnail grid of the screenshots folder,
 * click a shot to view it large. Thumbnails are decoded + downscaled on the fly (capped,
 * throttled) and all GPU textures are released when the screen closes.
 */
public final class LuminaScreenshotGallery extends Screen {

    private static final int MAX_FILES = 60;
    private static final int THUMB_W = 512;           // downscaled texture width (VRAM-friendly)
    private static final int COLS = 3, GAP = 10;

    private final List<File> files = new ArrayList<>();
    private final Map<File, Identifier> tex = new HashMap<>();
    private final Map<File, int[]> dims = new HashMap<>();
    private final Set<File> failed = new HashSet<>();
    private int idCounter = 0;
    private int scroll = 0;
    private int loadsThisFrame = 0;
    private File enlarged = null;
    private Identifier fullId = null;     // full-res texture for the enlarged view
    private int[] fullDims = null;
    private File fullFor = null;
    private volatile String status = null;
    private volatile long statusUntil = 0L;

    public LuminaScreenshotGallery() { super(Component.literal("Screenshots")); }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        files.clear();
        File dir = new File(this.minecraft.gameDirectory, "screenshots");
        File[] found = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".png"));
        if (found != null) {
            Arrays.sort(found, Comparator.comparingLong(File::lastModified).reversed());
            for (int i = 0; i < found.length && i < MAX_FILES; i++) files.add(found[i]);
        }
    }

    // ── layout ───────────────────────────────────────────────────────────

    private int gridX() { return (this.width - gridW()) / 2; }
    private int gridW() { return Math.min(this.width - 80, 760); }
    private int cellW() { return (gridW() - (COLS - 1) * GAP) / COLS; }
    private int cellH() { return cellW() * 9 / 16; }
    private int gridTop() { return 56; }
    private int gridBottom() { return this.height - 40; }

    private int[] cellRect(int i) {
        int col = i % COLS, row = i / COLS;
        return new int[]{ gridX() + col * (cellW() + GAP), gridTop() + row * (cellH() + GAP) - scroll, cellW(), cellH() };
    }

    // ── lazy, throttled texture loading ──────────────────────────────────

    private Identifier texFor(File f) {
        Identifier existing = tex.get(f);
        if (existing != null) return existing;
        if (failed.contains(f) || loadsThisFrame >= 2) return null;
        loadsThisFrame++;
        try (InputStream in = Files.newInputStream(f.toPath())) {
            NativeImage full = NativeImage.read(in);
            int fw = full.getWidth(), fh = full.getHeight();
            int tw = Math.min(THUMB_W, fw), th = Math.max(1, (int) ((long) tw * fh / fw));
            NativeImage thumb = new NativeImage(tw, th, false);
            full.resizeSubRectTo(0, 0, fw, fh, thumb);
            full.close();
            Identifier id = Identifier.fromNamespaceAndPath("lumina", "ss/" + (idCounter++));
            this.minecraft.getTextureManager().register(id, new DynamicTexture(() -> "lumina-ss", thumb));
            tex.put(f, id);
            dims.put(f, new int[]{ tw, th });
            return id;
        } catch (Exception e) {
            failed.add(f);
            return null;
        }
    }

    // ── render ───────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        loadsThisFrame = 0;
        g.fillGradient(0, 0, this.width, this.height, LuminaTheme.SCRIM_TOP, LuminaTheme.SCRIM_BOT);
        g.fill(0, 0, this.width, this.height, 0x14140A28);

        if (enlarged != null) { renderEnlarged(g, mouseX, mouseY); drawStatus(g); return; }
        drawStatus(g);

        String title = "Screenshots  (" + files.size() + ")";
        g.drawString(this.font, title, gridX(), 30, LuminaTheme.ACCENT_SOFT, false);
        String hint = "Click to enlarge  ·  Esc to close";
        g.drawString(this.font, hint, gridX() + gridW() - this.font.width(hint), 32, LuminaTheme.TEXT_MUTED, false);

        if (files.isEmpty()) {
            String none = "No screenshots yet — press F2 in-game.";
            g.drawString(this.font, none, (this.width - this.font.width(none)) / 2, this.height / 2, LuminaTheme.TEXT_FAINT, false);
            return;
        }

        int gt = gridTop(), gb = gridBottom();
        int rows = (files.size() + COLS - 1) / COLS;
        int totalH = rows * (cellH() + GAP);
        int maxScroll = Math.max(0, totalH - (gb - gt) + GAP);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        g.enableScissor(gridX() - 4, gt, gridX() + gridW() + 4, gb);
        for (int i = 0; i < files.size(); i++) {
            int[] r = cellRect(i);
            if (r[1] + r[3] < gt || r[1] > gb) continue;
            boolean hover = mouseY >= gt && mouseY < gb && mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3];

            LuminaTheme.roundRect(g, r[0] - 2, r[1] - 2, r[2] + 4, r[3] + 4, 6, hover ? LuminaTheme.ACCENT : 0x66000000);
            g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0xFF0B0712);
            drawThumb(g, texFor(files.get(i)), files.get(i), r[0], r[1], r[2], r[3]);
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int viewH = gb - gt, barH = Math.max(20, viewH * viewH / totalH);
            int barY = gt + (viewH - barH) * scroll / maxScroll;
            LuminaTheme.roundRect(g, gridX() + gridW() + 8, barY, 3, barH, 1, LuminaTheme.ACCENT);
        }
    }

    private void drawThumb(GuiGraphics g, Identifier id, File f, int x, int y, int w, int h) {
        if (id == null) {
            String s = "…";
            g.drawString(this.font, s, x + (w - this.font.width(s)) / 2, y + h / 2 - 4, LuminaTheme.TEXT_MUTED, false);
            return;
        }
        int[] d = dims.get(f);
        int tw = d[0], th = d[1];
        int dw = w, dh = (int) ((long) w * th / tw);
        if (dh > h) { dh = h; dw = (int) ((long) h * tw / th); }
        int dx = x + (w - dw) / 2, dy = y + (h - dh) / 2;
        g.blit(RenderPipelines.GUI_TEXTURED, id, dx, dy, 0f, 0f, dw, dh, tw, th, tw, th);
    }

    private void renderEnlarged(GuiGraphics g, int mouseX, int mouseY) {
        if (fullFor == null || !fullFor.equals(enlarged)) loadFull(enlarged);

        g.drawString(this.font, enlarged.getName(), 20, 14, LuminaTheme.ACCENT_SOFT, false);
        String hint = "Click image: back";
        g.drawString(this.font, hint, this.width - 20 - this.font.width(hint), 14, LuminaTheme.TEXT_MUTED, false);

        if (fullId != null) {
            int tw = fullDims[0], th = fullDims[1];
            int maxW = this.width - 60, maxH = this.height - 80;
            int dw = maxW, dh = (int) ((long) maxW * th / tw);
            if (dh > maxH) { dh = maxH; dw = (int) ((long) maxH * tw / th); }
            int dx = (this.width - dw) / 2, dy = 32 + (maxH - dh) / 2;
            LuminaTheme.roundRect(g, dx - 3, dy - 3, dw + 6, dh + 6, 4, 0x66A78BFA);
            g.blit(RenderPipelines.GUI_TEXTURED, fullId, dx, dy, 0f, 0f, dw, dh, tw, th, tw, th);
        }

        button(g, mouseX, mouseY, delRect(), 0, "Delete", 0xFFFF6B6B);
        button(g, mouseX, mouseY, copyRect(), 1, "Copy", LuminaTheme.ACCENT_SOFT);
        button(g, mouseX, mouseY, folderRect(), 2, "Open folder", LuminaTheme.ACCENT_SOFT);
    }

    private void button(GuiGraphics g, int mx, int my, int[] r, int icon, String label, int col) {
        boolean h = inside(mx, my, r);
        int c = h ? LuminaTheme.CYAN : col;
        LuminaTheme.roundRect(g, r[0], r[1], r[2], r[3], 9, h ? LuminaTheme.ROW_HOVER : LuminaTheme.TAB_IDLE);
        int iconW = 11, gap = 5, x = r[0] + (r[2] - (iconW + gap + this.font.width(label))) / 2, iy = r[1] + 4;
        switch (icon) {
            case 0 -> drawTrash(g, x, iy, c);
            case 1 -> drawCopy(g, x, iy, c);
            case 2 -> drawFolder(g, x, iy + 1, c);
        }
        g.drawString(this.font, label, x + iconW + gap, r[1] + 6, c, false);
    }

    private static void drawTrash(GuiGraphics g, int x, int y, int c) {
        g.fill(x + 3, y, x + 8, y + 1, c);
        g.fill(x, y + 1, x + 11, y + 2, c);
        g.fill(x + 1, y + 3, x + 2, y + 12, c);
        g.fill(x + 9, y + 3, x + 10, y + 12, c);
        g.fill(x + 1, y + 11, x + 10, y + 12, c);
        g.fill(x + 4, y + 4, x + 5, y + 11, c);
        g.fill(x + 6, y + 4, x + 7, y + 11, c);
    }

    private static void drawCopy(GuiGraphics g, int x, int y, int c) {
        g.fill(x + 3, y, x + 11, y + 1, c);
        g.fill(x + 10, y, x + 11, y + 8, c);
        g.fill(x + 3, y, x + 4, y + 2, c);
        g.fill(x + 1, y + 7, x + 11, y + 8, c);
        g.fill(x, y + 3, x + 8, y + 4, c);
        g.fill(x, y + 3, x + 1, y + 12, c);
        g.fill(x + 7, y + 3, x + 8, y + 12, c);
        g.fill(x, y + 11, x + 8, y + 12, c);
    }

    private static void drawFolder(GuiGraphics g, int x, int y, int c) {
        g.fill(x, y, x + 5, y + 2, c);
        g.fill(x, y + 2, x + 12, y + 3, c);
        g.fill(x, y + 2, x + 1, y + 10, c);
        g.fill(x + 11, y + 2, x + 12, y + 10, c);
        g.fill(x, y + 9, x + 12, y + 10, c);
    }

    private void drawStatus(GuiGraphics g) {
        if (status != null && System.currentTimeMillis() < statusUntil)
            g.drawString(this.font, status, (this.width - this.font.width(status)) / 2, 14, LuminaTheme.CYAN, false);
    }

    private int btnY() { return this.height - 30; }
    private int[] delRect()    { return new int[]{ this.width / 2 - 132, btnY(), 74, 20 }; }
    private int[] copyRect()   { return new int[]{ this.width / 2 - 52,  btnY(), 64, 20 }; }
    private int[] folderRect() { return new int[]{ this.width / 2 + 20,  btnY(), 112, 20 }; }
    private static boolean inside(int mx, int my, int[] r) { return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]; }
    private void status(String s) { this.status = s; this.statusUntil = System.currentTimeMillis() + 1500; }

    private void loadFull(File f) {
        releaseFull();
        try (InputStream in = Files.newInputStream(f.toPath())) {
            NativeImage img = NativeImage.read(in);
            int w = img.getWidth(), h = img.getHeight();
            NativeImage use = img;
            if (w > 3840) {                          // guard against huge textures
                int nw = 3840, nh = Math.max(1, (int) ((long) nw * h / w));
                NativeImage small = new NativeImage(nw, nh, false);
                img.resizeSubRectTo(0, 0, w, h, small);
                img.close(); use = small; w = nw; h = nh;
            }
            fullId = Identifier.fromNamespaceAndPath("lumina", "ssfull/" + (idCounter++));
            this.minecraft.getTextureManager().register(fullId, new DynamicTexture(() -> "lumina-ssfull", use));
            fullDims = new int[]{ w, h };
        } catch (Exception e) {
            fullId = null; fullDims = null;
        }
        fullFor = f;
    }

    private void releaseFull() {
        if (fullId != null) {
            try { this.minecraft.getTextureManager().release(fullId); } catch (Exception ignored) {}
            fullId = null;
        }
        fullFor = null; fullDims = null;
    }

    private void deleteCurrent() {
        File f = enlarged;
        int idx = files.indexOf(f);
        Identifier t = tex.remove(f);
        if (t != null) try { this.minecraft.getTextureManager().release(t); } catch (Exception ignored) {}
        dims.remove(f);
        releaseFull();
        try { Files.deleteIfExists(f.toPath()); } catch (Exception ignored) {}
        files.remove(f);
        enlarged = files.isEmpty() ? null : files.get(Math.min(idx, files.size() - 1));
        status("Deleted");
    }

    private void copyCurrent() {
        final File f = enlarged;
        final boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        status("Copying…");
        new Thread(() -> {
            boolean ok = false;
            try {
                if (windows) {
                    // AWT's clipboard is unreliable inside the game; drive the Windows clipboard via PowerShell (STA).
                    String path = f.getAbsolutePath().replace("'", "''");
                    String ps = "Add-Type -AssemblyName System.Windows.Forms,System.Drawing; "
                            + "[System.Windows.Forms.Clipboard]::SetImage([System.Drawing.Image]::FromFile('" + path + "'))";
                    Process p = new ProcessBuilder("powershell", "-NoProfile", "-STA", "-Command", ps)
                            .redirectErrorStream(true).start();
                    ok = p.waitFor() == 0;
                } else {
                    final java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(f);
                    if (img != null) {
                        java.awt.datatransfer.Transferable tr = new java.awt.datatransfer.Transferable() {
                            public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() { return new java.awt.datatransfer.DataFlavor[]{ java.awt.datatransfer.DataFlavor.imageFlavor }; }
                            public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor fl) { return java.awt.datatransfer.DataFlavor.imageFlavor.equals(fl); }
                            public Object getTransferData(java.awt.datatransfer.DataFlavor fl) { return img; }
                        };
                        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(tr, null);
                        ok = true;
                    }
                }
            } catch (Throwable ignored) {}
            status(ok ? "Copied to clipboard" : "Copy failed");
        }, "lumina-ss-copy").start();
    }

    private void openFolder() {
        net.minecraft.util.Util.getPlatform().openFile(new File(this.minecraft.gameDirectory, "screenshots"));
    }

    // ── input ────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x(), my = (int) event.y();
        if (enlarged != null) {
            if (inside(mx, my, delRect()))    { deleteCurrent(); return true; }
            if (inside(mx, my, copyRect()))   { copyCurrent();  return true; }
            if (inside(mx, my, folderRect())) { openFolder();   return true; }
            enlarged = null; releaseFull(); return true;
        }
        if (event.button() == 0) {
            int gt = gridTop(), gb = gridBottom();
            if (my >= gt && my <= gb) {
                for (int i = 0; i < files.size(); i++) {
                    int[] r = cellRect(i);
                    if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                        enlarged = files.get(i);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (enlarged == null) scroll -= (int) (scrollY * 40);
        return true;
    }

    @Override
    public void removed() {
        for (Identifier id : tex.values()) {
            try { this.minecraft.getTextureManager().release(id); } catch (Exception ignored) {}
        }
        tex.clear(); dims.clear();
        releaseFull();
        super.removed();
    }
}
