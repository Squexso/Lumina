package com.luminamc.ui.components;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.shape.Box;
import javafx.scene.shape.Sphere;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;

/**
 * A real, interactive 3D Minecraft player model rendered with JavaFX 3D.
 *
 * <p>Each body part is a cuboid built from six textured quads whose UVs are cropped
 * from the 64×64 skin (Minecraft layout), so the skin maps correctly. Drag to rotate;
 * it idles with a slow spin. An optional violet Lumina cape is attached to the back.
 */
public final class SkinView3D {

    private static final int UPSCALE = 8;   // nearest-neighbour upscale per face → crisp pixels

    private final Image capeTex;            // cape back texture, or null for no cape
    private final Group model = new Group();
    private final Rotate rotX = new Rotate(-4, Rotate.X_AXIS);
    private final Rotate rotY = new Rotate(137, Rotate.Y_AXIS);   // 3/4 front: face + the cape on the back
    private Rotate auraSpin;                 // animated spin for the star aura (null if no aura)

    private double lastX, lastY;
    private boolean dragging;

    public SkinView3D(Image skin, boolean slim, Image capeTexture) {
        this(skin, slim, capeTexture, null, null);
    }

    public SkinView3D(Image skin, boolean slim, Image capeTexture, String accessory, javafx.scene.paint.Color accColor) {
        this.capeTex = capeTexture;
        int armW = slim ? 3 : 4;
        double armX = slim ? 5.5 : 6.0;

        Group body = new Group();
        // up = -Y, front = +Z, right = +X. Feet at y=0, head on top.
        body.getChildren().addAll(
                cuboid(0,  -28, 0,  8, 8, 8,  0,  0,  0.0, skin),    // head
                cuboid(0,  -28, 0,  8, 8, 8,  32, 0,  0.6, skin),    // hat overlay
                cuboid(0,  -18, 0,  8, 12, 4, 16, 16, 0.0, skin),    // body
                cuboid(0,  -18, 0,  8, 12, 4, 16, 32, 0.35, skin),   // jacket overlay
                cuboid(-armX, -18, 0, armW, 12, 4, 40, 16, 0.0, skin),  // right arm
                cuboid(-armX, -18, 0, armW, 12, 4, 40, 32, 0.35, skin), // right sleeve
                cuboid( armX, -18, 0, armW, 12, 4, 32, 48, 0.0, skin),  // left arm
                cuboid( armX, -18, 0, armW, 12, 4, 48, 48, 0.35, skin), // left sleeve
                cuboid(-2, -6, 0,  4, 12, 4,  0, 16, 0.0, skin),    // right leg
                cuboid(-2, -6, 0,  4, 12, 4,  0, 32, 0.35, skin),   // right pants
                cuboid( 2, -6, 0,  4, 12, 4,  16, 48, 0.0, skin),   // left leg
                cuboid( 2, -6, 0,  4, 12, 4,  0, 48, 0.35, skin));  // left pants

        if (capeTex != null) body.getChildren().add(capeNode());
        if (accessory != null) body.getChildren().add(accessoryNode(accessory, accColor != null ? accColor : Color.WHITE));

        body.setTranslateY(16);            // centre the 32-tall model on the origin
        model.getChildren().add(body);
        model.getTransforms().addAll(rotY, rotX);
    }

    // ── public API ────────────────────────────────────────────────────────

    /** Sets the view angles (degrees). */
    public void setView(double yDeg, double xDeg) { rotY.setAngle(yDeg); rotX.setAngle(xDeg); }

    /** Builds the interactive 3D sub-scene sized {@code w × h}. */
    public SubScene buildScene(double w, double h) {
        Group root = new Group(model);

        javafx.scene.AmbientLight ambient = new javafx.scene.AmbientLight(Color.color(0.82, 0.82, 0.82));
        javafx.scene.PointLight key = new javafx.scene.PointLight(Color.color(0.5, 0.5, 0.55));
        key.setTranslateZ(-80); key.setTranslateY(-60); key.setTranslateX(-40);
        root.getChildren().addAll(ambient, key);

        SubScene scene = new SubScene(root, w, h, true, SceneAntialiasing.BALANCED);
        scene.setFill(Color.TRANSPARENT);

        PerspectiveCamera cam = new PerspectiveCamera(true);
        cam.setFieldOfView(38);
        cam.setNearClip(0.1);
        cam.setFarClip(1000);
        cam.setTranslateZ(-72);
        scene.setCamera(cam);

        // Drag to rotate.
        scene.setOnMousePressed(e -> { lastX = e.getSceneX(); lastY = e.getSceneY(); dragging = true; });
        scene.setOnMouseReleased(e -> dragging = false);
        scene.setOnMouseDragged(e -> {
            // Drag left → model turns left (natural orbit feel). Vertical tilt is kept
            // small so the character always stands upright instead of tipping over.
            rotY.setAngle(rotY.getAngle() - (e.getSceneX() - lastX) * 0.5);
            double nx = rotX.getAngle() - (e.getSceneY() - lastY) * 0.25;
            rotX.setAngle(Math.max(-16, Math.min(16, nx)));
            lastX = e.getSceneX(); lastY = e.getSceneY();
        });

        // Animate the star aura (slow orbit; stars twinkle as they turn edge-on/face-on).
        if (auraSpin != null) {
            javafx.animation.AnimationTimer timer = new javafx.animation.AnimationTimer() {
                @Override public void handle(long now) { auraSpin.setAngle(auraSpin.getAngle() + 0.45); }
            };
            timer.start();
            scene.sceneProperty().addListener((o, was, is) -> { if (is == null) timer.stop(); });
        }

        return scene;
    }

    // ── cape ───────────────────────────────────────────────────────────────

    private Node capeNode() {
        // The cape hangs straight down and curls gently back only at its side edges
        // (a symmetric horizontal wrap around the back). Its depth (z) depends ONLY on
        // x, never on height — a height-dependent backward tilt makes the lower rows
        // swing sideways under rotation, shearing the cape into a leaning parallelogram
        // ("schief"). Keeping z constant down the whole drop makes it hang dead-straight
        // at every angle.
        //
        // It is a plain straight rectangle exactly the width of the torso back (half 4):
        // the edges line up with the body edge and never overhang into the arms, so no arm
        // can clip a corner (no notch), and there is no taper to read as "twisted". It
        // stands clear of the body in z so the torso never swallows it, and the preview
        // defaults to a straight-back angle (see ModelPreview) so the full, centred,
        // back-covering cape is what you see first.
        int rows = 12, cols = 8;
        double halfW = 4.0;         // exactly torso-width: covers the whole back; edges abut the arms with no overhang to be clipped
        double topY = -24.0, height = 21.0;
        double zBase = -2.6;        // constant distance behind the back the whole way down → no lean; stands clear of the torso so it's not swallowed
        double wrap = 0.15;         // a very subtle side-edge curl so it reads as fabric, kept small so it never looks twisted
        TriangleMesh mesh = new TriangleMesh();
        for (int r = 0; r <= rows; r++) {
            double tv = r / (double) rows;                  // 0 shoulders → 1 hem
            double y = topY + tv * height;
            for (int c = 0; c <= cols; c++) {
                double tu = c / (double) cols;              // 0 left → 1 right
                double nx = tu * 2 - 1;                     // -1 left … +1 right
                double x = nx * halfW;
                double z = zBase - wrap * nx * nx;          // symmetric wrap: centre flat on the back, edges curl back
                mesh.getPoints().addAll((float) x, (float) y, (float) z);
                mesh.getTexCoords().addAll((float) tu, (float) tv);
            }
        }
        int stride = cols + 1;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int p00 = r * stride + c, p10 = p00 + 1;
                int p01 = (r + 1) * stride + c, p11 = p01 + 1;
                mesh.getFaces().addAll(p00, p00, p01, p01, p11, p11,  p00, p00, p11, p11, p10, p10);
            }
        }
        MeshView mv = new MeshView(mesh);
        PhongMaterial m = new PhongMaterial();
        if (capeTex != null) m.setDiffuseMap(capeTex);
        else m.setDiffuseColor(Color.web("#7C3AED"));
        m.setSpecularColor(Color.TRANSPARENT);
        mv.setMaterial(m);
        mv.setCullFace(CullFace.NONE);
        return mv;
    }

    // ── accessories ──────────────────────────────────────────────────────────

    private Node accessoryNode(String type, Color color) {
        return switch (type) {
            case "halo"  -> halo(color);
            case "aura"  -> aura(color);
            case "wings" -> wings(color);
            default      -> new Group();
        };
    }

    /** A delicate glowing golden halo floating flat above the head. */
    private Node halo(Color color) {
        PhongMaterial m = new PhongMaterial(color.interpolate(Color.WHITE, 0.16));
        m.setSpecularColor(Color.web("#FFF6D6"));
        MeshView ring = torus(4.3, 0.42, 56, 18, m);
        ring.setTranslateY(-37.0);                                 // a clear gap above the head
        ring.getTransforms().add(new Rotate(28, Rotate.X_AXIS));   // mostly flat → reads as a floating halo
        return ring;
    }

    /** A slowly-orbiting cluster of glowing 5-pointed stars around the body. */
    private Node aura(Color color) {
        Group g = new Group();
        Color bright = color.interpolate(Color.WHITE, 0.2);   // keep the hue so warm/cool auras read clearly differently
        double[][] stars = {
                {-10, -26, -2, 1.6}, {10, -24, -3, 1.3}, {-11, -15, -4, 1.5}, {11, -13, -2, 1.6},
                {0, -31, -5, 1.2}, {-6, -8, -3, 1.1}, {7, -7, -4, 1.4}, {3, -2, -5, 1.1}, {-3, -19, -6, 1.3}};
        java.util.Random rnd = new java.util.Random(7);
        for (double[] s : stars) {
            MeshView star = starMesh(s[3], bright);
            star.setTranslateX(s[0]); star.setTranslateY(s[1]); star.setTranslateZ(s[2]);
            star.getTransforms().addAll(
                    new Rotate(rnd.nextDouble() * 360, Rotate.Y_AXIS),
                    new Rotate(rnd.nextDouble() * 360, Rotate.Z_AXIS));
            g.getChildren().add(star);
        }
        auraSpin = new Rotate(0, 0, -16, 0, Rotate.Y_AXIS);   // orbit around the body's vertical axis
        g.getTransforms().add(auraSpin);
        return g;
    }

    /** A flat 5-pointed star mesh. */
    private MeshView starMesh(double outerR, Color color) {
        double innerR = outerR * 0.42;
        TriangleMesh mesh = new TriangleMesh();
        mesh.getPoints().addAll(0, 0, 0);
        mesh.getTexCoords().addAll(0.5f, 0.5f);
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            double r = (i % 2 == 0) ? outerR : innerR;
            mesh.getPoints().addAll((float) (Math.cos(a) * r), (float) (Math.sin(a) * r), 0);
            mesh.getTexCoords().addAll(0.5f, 0.5f);
        }
        for (int i = 0; i < 10; i++) mesh.getFaces().addAll(0, 0, 1 + i, 1 + i, 1 + (i + 1) % 10, 1 + (i + 1) % 10);
        MeshView mv = new MeshView(mesh);
        PhongMaterial m = new PhongMaterial(color);
        m.setSpecularColor(Color.WHITE);
        mv.setMaterial(m);
        mv.setCullFace(CullFace.NONE);
        return mv;
    }

    /** Two layered voxel angel wings on the back. */
    private Node wings(Color color) {
        return new Group(wing(color, false), wing(color, true));
    }

    /**
     * A clean voxel angel wing in the style of the reference: feathers fan out from a
     * curved top edge and hang down as straight diagonal stairs (the feather grain),
     * built in two shingled layers (long primaries behind, shorter coverts in front),
     * each feather ramping from a shaded root to a bright glowing tip. Mirrored by
     * {@code leftSide}.
     */
    private Node wing(Color color, boolean leftSide) {
        double s = leftSide ? 1 : -1;                                // +X is the left wing
        double u = 2.0;                                              // cube edge — big enough to read as deliberate voxel art
        Group g = new Group();

        // Shades derived from the accessory's OWN colour (not fixed greys), so a dark
        // colour gives dark wings (Shadow Wings) while a white one stays bright (Angel Wings).
        Color root = color.deriveColor(0, 1.0, 0.50, 1.0);          // a darker shade of the colour
        Color mid  = color.deriveColor(0, 1.05, 0.90, 1.0);         // the colour itself
        Color tip  = color.interpolate(Color.WHITE, 0.42);          // lifted toward white, keeps the hue

        // Primary layer — long feathers spanning the whole top edge.
        int primaries = 11;
        for (int f = 0; f < primaries; f++) {
            double v = f / (primaries - 1.0);                        // 0 inner shoulder → 1 outer wrist
            double rx = s * (2.5 + v * 15.0);                       // top-edge root sweeps outward
            double ry = -22.0 - Math.pow(Math.sin(v * Math.PI * 0.62), 1.25) * 13.0;  // edge arcs up to the wrist
            double rz = -3.4 - v * 2.2;
            int len = (int) Math.round(4 + bell(v, 0.74) * 9);       // longest primaries toward the outer-lower edge
            featherStair(g, rx, ry, rz, len, u, s, root, mid, tip);
        }

        // Covert layer — shorter feathers shingled just in front of the upper half for depth.
        int coverts = 7;
        for (int f = 0; f < coverts; f++) {
            double v = f / (coverts - 1.0);
            double rx = s * (3.0 + v * 11.0);
            double ry = -23.5 - Math.pow(Math.sin(v * Math.PI * 0.60), 1.25) * 11.0;
            double rz = -2.3 - v * 1.7;                              // sits a touch in front of the primaries
            int len = (int) Math.round(3 + bell(v, 0.60) * 4);
            featherStair(g, rx, ry, rz, len, u, s,
                    root.interpolate(Color.WHITE, 0.16),
                    mid.interpolate(Color.WHITE, 0.20),
                    tip);
        }
        return g;
    }

    /** Lays a single feather as a straight diagonal stair of cubes, ramping root→tip brightness. */
    private void featherStair(Group g, double rx, double ry, double rz, int len,
                              double u, double s, Color root, Color mid, Color tip) {
        for (int r = 0; r < len; r++) {
            double f = len == 1 ? 1.0 : r / (len - 1.0);            // 0 root → 1 tip
            Box b = new Box(u, u, u);
            b.setTranslateX(rx + s * -0.34 * u * r);                // grain drifts toward centre as it falls
            b.setTranslateY(ry + 0.86 * u * r);                     // hangs down
            b.setTranslateZ(rz - 0.05 * u * r);
            Color c = f < 0.5 ? root.interpolate(mid, f * 2.0)
                              : mid.interpolate(tip, (f - 0.5) * 2.0);
            b.setMaterial(blockMat(c));
            g.getChildren().add(b);
        }
    }

    /** Bell curve peaking at {@code peak} (0 at both ends). */
    private static double bell(double a, double peak) {
        double x = (a - peak) / Math.max(peak, 1 - peak);
        return Math.max(0, 1 - x * x);
    }

    private PhongMaterial blockMat(Color c) {
        PhongMaterial m = new PhongMaterial(c);
        m.setSpecularColor(Color.web("#FFFFFF"));
        return m;
    }

    private MeshView colorQuad(float[] a, float[] b, float[] c, float[] d, Color color) {
        TriangleMesh mesh = new TriangleMesh();
        mesh.getPoints().addAll(a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], d[0], d[1], d[2]);
        mesh.getTexCoords().addAll(0, 0, 1, 0, 1, 1, 0, 1);
        mesh.getFaces().addAll(0, 0, 1, 1, 2, 2,  0, 0, 2, 2, 3, 3);
        MeshView mv = new MeshView(mesh);
        PhongMaterial m = new PhongMaterial(color);
        m.setSpecularColor(Color.web("#FFFFFF", 0.4));
        mv.setMaterial(m);
        mv.setCullFace(CullFace.NONE);
        return mv;
    }

    /** A torus (ring) mesh with major radius R and tube radius r. */
    private MeshView torus(double R, double r, int nMajor, int nMinor, PhongMaterial mat) {
        TriangleMesh mesh = new TriangleMesh();
        for (int i = 0; i < nMajor; i++) {
            double u = i * 2 * Math.PI / nMajor;
            for (int j = 0; j < nMinor; j++) {
                double v = j * 2 * Math.PI / nMinor;
                double x = (R + r * Math.cos(v)) * Math.cos(u);
                double y = r * Math.sin(v);
                double z = (R + r * Math.cos(v)) * Math.sin(u);
                mesh.getPoints().addAll((float) x, (float) y, (float) z);
                mesh.getTexCoords().addAll((float) i / nMajor, (float) j / nMinor);
            }
        }
        for (int i = 0; i < nMajor; i++) {
            for (int j = 0; j < nMinor; j++) {
                int i1 = (i + 1) % nMajor, j1 = (j + 1) % nMinor;
                int a = i * nMinor + j, b = i1 * nMinor + j, c = i1 * nMinor + j1, d = i * nMinor + j1;
                mesh.getFaces().addAll(a, a, b, b, c, c,  a, a, c, c, d, d);
            }
        }
        MeshView mv = new MeshView(mesh);
        mv.setMaterial(mat);
        mv.setCullFace(CullFace.NONE);
        return mv;
    }

    // ── cuboid built from six textured quads ────────────────────────────────

    private Group cuboid(double cx, double cy, double cz,
                         int w, int h, int d, int u, int v, double exp, Image skin) {
        double hx = w / 2.0 + exp, hy = h / 2.0 + exp, hz = d / 2.0 + exp;
        // corners — name = X(L/R) Y(U/D) Z(B/F)
        float[] LUB = p(cx - hx, cy - hy, cz - hz), RUB = p(cx + hx, cy - hy, cz - hz);
        float[] RDB = p(cx + hx, cy + hy, cz - hz), LDB = p(cx - hx, cy + hy, cz - hz);
        float[] LUF = p(cx - hx, cy - hy, cz + hz), RUF = p(cx + hx, cy - hy, cz + hz);
        float[] RDF = p(cx + hx, cy + hy, cz + hz), LDF = p(cx - hx, cy + hy, cz + hz);

        Group g = new Group();
        g.getChildren().addAll(
                face(LUB, RUB, RUF, LUF, crop(skin, u + d,       v,     w, d)),   // top
                face(LDF, RDF, RDB, LDB, crop(skin, u + d + w,   v,     w, d)),   // bottom
                face(LUF, RUF, RDF, LDF, crop(skin, u + d,       v + d, w, h)),   // front
                face(RUB, LUB, LDB, RDB, crop(skin, u + 2*d + w, v + d, w, h)),   // back
                face(LUB, LUF, LDF, LDB, crop(skin, u,           v + d, d, h)),   // right side (x = -hx)
                face(RUF, RUB, RDB, RDF, crop(skin, u + d + w,   v + d, d, h)));  // left side  (x = +hx)
        return g;
    }

    /** A quad whose texture top-left/top-right/bottom-right/bottom-left map to tl,tr,br,bl. */
    private MeshView face(float[] tl, float[] tr, float[] br, float[] bl, Image tex) {
        if (tex == null) return new MeshView(new TriangleMesh());
        TriangleMesh mesh = new TriangleMesh();
        mesh.getPoints().addAll(
                tl[0], tl[1], tl[2], tr[0], tr[1], tr[2], br[0], br[1], br[2], bl[0], bl[1], bl[2]);
        mesh.getTexCoords().addAll(0, 0, 1, 0, 1, 1, 0, 1);
        mesh.getFaces().addAll(0, 0, 1, 1, 2, 2,  0, 0, 2, 2, 3, 3);
        MeshView mv = new MeshView(mesh);
        PhongMaterial mat = new PhongMaterial();
        mat.setDiffuseMap(tex);
        mat.setSpecularColor(Color.TRANSPARENT);
        mv.setMaterial(mat);
        mv.setCullFace(CullFace.NONE);
        return mv;
    }

    private static float[] p(double x, double y, double z) { return new float[]{(float) x, (float) y, (float) z}; }

    /** Crops the {@code sw×sh} skin region at (sx,sy) and nearest-neighbour-upscales it. Null if fully transparent. */
    private static WritableImage crop(Image skin, int sx, int sy, int sw, int sh) {
        if (skin == null || skin.getPixelReader() == null) return null;
        PixelReader r = skin.getPixelReader();
        int srcW = (int) skin.getWidth(), srcH = (int) skin.getHeight();
        WritableImage out = new WritableImage(sw * UPSCALE, sh * UPSCALE);
        PixelWriter pw = out.getPixelWriter();
        boolean anyOpaque = false;
        for (int y = 0; y < sh * UPSCALE; y++) {
            int yy = Math.min(srcH - 1, sy + y / UPSCALE);
            for (int x = 0; x < sw * UPSCALE; x++) {
                int xx = Math.min(srcW - 1, sx + x / UPSCALE);
                int argb = r.getArgb(xx, yy);
                if ((argb >>> 24) != 0) anyOpaque = true;
                pw.setArgb(x, y, argb);
            }
        }
        return anyOpaque ? out : null;   // skip fully-transparent overlay faces
    }
}
