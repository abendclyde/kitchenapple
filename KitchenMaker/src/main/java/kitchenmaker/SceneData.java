package kitchenmaker;

import com.jogamp.opengl.GL2;
import java.io.*;
import java.nio.*;
import java.util.*;

public class SceneData {

    // Enum für Erscheinungsmodi
    public enum AppearanceMode {
        NONE("Aus"),
        FALL_DOWN("Von oben fallen"),
        RISE_UP("Von unten steigen"),
        GROW("Wachsen");

        private final String displayName;

        AppearanceMode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public static class Object3D {
        public String name;
        public float[] vertices;
        public int[] indices;
        public Vec3 worldPosition = new Vec3(0, 0, 0);
        public Vec3 rotationAngles = new Vec3(0, 0, 0);
        public Vec3 scaleFactors = new Vec3(1, 1, 1);
        public Vec3 color = new Vec3(0.8f, 0.8f, 0.8f);

        // Animationsfelder
        public boolean isAnimating = false;
        public AppearanceMode animationMode = AppearanceMode.NONE;
        public long animationStartTime = 0;
        public float animationDuration = 0; // in Sekunden
        public Vec3 animationTargetPosition = new Vec3();
        public Vec3 animationTargetScale = new Vec3(1, 1, 1);
        public Vec3 animationStartPosition = new Vec3();
        public Vec3 animationStartScale = new Vec3(1, 1, 1);

        private int vao, vbo, ebo;
        private boolean initialized = false;
        public Vec3 boundingBoxMin = new Vec3();
        public Vec3 boundingBoxMax = new Vec3();

        public Object3D(String name, float[] vertices, int[] indices) {
            this.name = name;
            this.vertices = vertices;
            this.indices = indices;
            calculateBounds();
        }


        public void startAnimation(AppearanceMode mode, float durationSeconds) {
            if (mode == AppearanceMode.NONE || durationSeconds <= 0) {
                isAnimating = false;
                return;
            }

            this.animationMode = mode;
            this.animationDuration = durationSeconds;
            this.animationStartTime = System.currentTimeMillis();
            this.isAnimating = true;

            // Zielwerte speichern (aktuelle Position/Scale)
            this.animationTargetPosition.set(this.worldPosition);
            this.animationTargetScale.set(this.scaleFactors);

            // Startwerte je nach Modus setzen
            switch (mode) {
                case FALL_DOWN -> {
                    // Start von oben (Y = 5 über Ziel)
                    this.animationStartPosition.set(this.worldPosition.x, this.worldPosition.y + 5.0f, this.worldPosition.z);
                    this.animationStartScale.set(this.scaleFactors);
                    this.worldPosition.set(this.animationStartPosition);
                }
                case RISE_UP -> {
                    // Start von unten (Y = 5 unter Ziel)
                    this.animationStartPosition.set(this.worldPosition.x, this.worldPosition.y - 5.0f, this.worldPosition.z);
                    this.animationStartScale.set(this.scaleFactors);
                    this.worldPosition.set(this.animationStartPosition);
                }
                case GROW -> {
                    // Start mit Größe 0
                    this.animationStartPosition.set(this.worldPosition);
                    this.animationStartScale.set(0.01f, 0.01f, 0.01f);
                    this.scaleFactors.set(this.animationStartScale);
                }
                default -> isAnimating = false;
            }
        }

        /**
         * Aktualisiert die Animation basierend auf der verstrichenen Zeit.
         * Gibt true zurück, wenn die Animation noch läuft.
         */
        public boolean updateAnimation() {
            if (!isAnimating) return false;

            long currentTime = System.currentTimeMillis();
            float elapsed = (currentTime - animationStartTime) / 1000.0f; // in Sekunden
            float progress = Math.min(1.0f, elapsed / animationDuration);

            // Interpolation für Abbremsen
            float eased = 1.0f - (1.0f - progress) * (1.0f - progress);

            switch (animationMode) {
                case FALL_DOWN, RISE_UP -> {
                    worldPosition.x = lerp(animationStartPosition.x, animationTargetPosition.x, eased);
                    worldPosition.y = lerp(animationStartPosition.y, animationTargetPosition.y, eased);
                    worldPosition.z = lerp(animationStartPosition.z, animationTargetPosition.z, eased);
                }
                case GROW -> {
                    scaleFactors.x = lerp(animationStartScale.x, animationTargetScale.x, eased);
                    scaleFactors.y = lerp(animationStartScale.y, animationTargetScale.y, eased);
                    scaleFactors.z = lerp(animationStartScale.z, animationTargetScale.z, eased);
                }
                default -> {}
            }

            if (progress >= 1.0f) {
                // Animation abgeschlossen - Zielwerte setzen
                worldPosition.set(animationTargetPosition);
                scaleFactors.set(animationTargetScale);
                isAnimating = false;
                return false;
            }

            return true;
        }

        private float lerp(float start, float end, float t) {
            return start + t * (end - start);
        }

        private void calculateBounds() {
            boundingBoxMin.set(Float.MAX_VALUE);
            boundingBoxMax.set(-Float.MAX_VALUE);
            for (int i = 0; i < vertices.length; i += 6) {
                float x = vertices[i], y = vertices[i+1], z = vertices[i+2];
                boundingBoxMin.x = Math.min(boundingBoxMin.x, x);
                boundingBoxMin.y = Math.min(boundingBoxMin.y, y);
                boundingBoxMin.z = Math.min(boundingBoxMin.z, z);
                boundingBoxMax.x = Math.max(boundingBoxMax.x, x);
                boundingBoxMax.y = Math.max(boundingBoxMax.y, y);
                boundingBoxMax.z = Math.max(boundingBoxMax.z, z);
            }
        }

        public void render(GL2 gl, int shaderId, int modelLoc, int colorLoc) {
            draw(gl, modelLoc, colorLoc, GL2.GL_TRIANGLES);
        }

        public void renderLines(GL2 gl, int shaderId, int modelLoc, int colorLoc) {
            draw(gl, modelLoc, colorLoc, GL2.GL_LINES);
        }

        private void draw(GL2 gl, int modelLoc, int colorLoc, int drawMode) {
            if (!initialized) init(gl);

            Mat4 modelMatrix = new Mat4()
                    .translate(worldPosition)
                    .rotateAroundX(rotationAngles.x)
                    .rotateAroundY(rotationAngles.y)
                    .rotateAroundZ(rotationAngles.z)
                    .scale(scaleFactors);

            gl.glUniformMatrix4fv(modelLoc, 1, false, modelMatrix.toFloatArray(), 0);
            gl.glUniform3f(colorLoc, color.x, color.y, color.z);

            gl.glBindVertexArray(vao);
            gl.glDrawElements(drawMode, indices.length, GL2.GL_UNSIGNED_INT, 0);
            gl.glBindVertexArray(0);
        }

        private void init(GL2 gl) {
            int[] buffers = new int[3];
            gl.glGenVertexArrays(1, buffers, 0);
            vao = buffers[0];
            gl.glGenBuffers(2, buffers, 1);
            vbo = buffers[1];
            ebo = buffers[2];

            gl.glBindVertexArray(vao);

            gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, vbo);
            gl.glBufferData(GL2.GL_ARRAY_BUFFER, (long) vertices.length * 4, FloatBuffer.wrap(vertices), GL2.GL_STATIC_DRAW);

            gl.glBindBuffer(GL2.GL_ELEMENT_ARRAY_BUFFER, ebo);
            gl.glBufferData(GL2.GL_ELEMENT_ARRAY_BUFFER, (long) indices.length * 4, IntBuffer.wrap(indices), GL2.GL_STATIC_DRAW);

            gl.glEnableVertexAttribArray(0);
            gl.glVertexAttribPointer(0, 3, GL2.GL_FLOAT, false, 6 * 4, 0); // Pos
            gl.glEnableVertexAttribArray(1);
            gl.glVertexAttribPointer(1, 3, GL2.GL_FLOAT, false, 6 * 4, 3 * 4); // Normal

            gl.glBindVertexArray(0);
            initialized = true;
        }
    }

    public static Object3D createGrid(int size, float spacing) {
        List<Float> verts = new ArrayList<>();
        List<Integer> inds = new ArrayList<>();
        float halfSize = size * spacing / 2.0f;
        int idx = 0;

        for (int i = -size / 2; i <= size / 2; i++) {
            float p = i * spacing;
            // X-Achse
            Collections.addAll(verts, -halfSize, 0f, p, 0f, 1f, 0f);
            Collections.addAll(verts, halfSize, 0f, p, 0f, 1f, 0f);
            inds.add(idx++); inds.add(idx++);
            // Z-Achse
            Collections.addAll(verts, p, 0f, -halfSize, 0f, 1f, 0f);
            Collections.addAll(verts, p, 0f, halfSize, 0f, 1f, 0f);
            inds.add(idx++); inds.add(idx++);
        }

        float[] vArr = new float[verts.size()];
        for (int i=0; i<verts.size(); i++) vArr[i] = verts.get(i);
        int[] iArr = inds.stream().mapToInt(i->i).toArray();

        Object3D grid = new Object3D("Grid", vArr, iArr);
        grid.color.set(0.25f, 0.28f, 0.35f);
        return grid;
    }


    public static Object3D loadObj(File file) {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            return parseObj(br, file.getName());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Mapping: 3D-Objektname -> {Ressourcendatei, Anzeigename, Farbe (r, g, b)}
    private static final Map<String, Object[]> OBJECT_DEFINITIONS = Map.of(
        "Fridge", new Object[]{"kuehlschrank.obj", "Kühlschrank", new float[]{0.9f, 0.95f, 1.0f}},
        "Microwave", new Object[]{"mikrowelle.obj", "Mikrowelle", new float[]{0.2f, 0.2f, 0.2f}},
        "Oven", new Object[]{"backofen.obj", "Backofen", new float[]{0.15f, 0.15f, 0.15f}},
        "Counter", new Object[]{"theke.obj", "Theke", new float[]{0.6f, 0.5f, 0.4f}},
        "Counter Inner Corner", new Object[]{"theke_ecke_innen.obj", "Theke Innenecke", new float[]{0.6f, 0.5f, 0.4f}},
        "Counter Outer Corner", new Object[]{"theke_ecke_aussen.obj", "Theke Außenecke", new float[]{0.6f, 0.5f, 0.4f}},
        "Sink", new Object[]{"waschbecken.obj", "Waschbecken", new float[]{0.8f, 0.85f, 0.9f}}
    );

    public static Object3D createFromDetectedShape(ShapeDetector.DetectedShape shape, int objectCount) {
        String name3D = shape.get3DObjectName();
        if (name3D == null) return null;

        Object3D obj = createByType(name3D);
        if (obj != null) {
            obj.name = obj.name + " " + (objectCount + 1);
        }
        return obj;
    }

    public static Object3D createByType(String typeName) {
        Object[] definition = OBJECT_DEFINITIONS.get(typeName);
        if (definition == null) return null;

        String resourcePath = (String) definition[0];
        String displayName = (String) definition[1];
        float[] color = (float[]) definition[2];

        try (InputStream is = SceneData.class.getResourceAsStream("/" + resourcePath);
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            Object3D obj = parseObj(br, displayName);
            if (obj != null) obj.color.set(color[0], color[1], color[2]);
            return obj;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Gemeinsame OBJ-Parsing-Logik
    private static Object3D parseObj(BufferedReader br, String objectName) throws IOException {
        List<Float> v = new ArrayList<>(), vn = new ArrayList<>(), buffer = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        String line;
        while ((line = br.readLine()) != null) {
            String[] p = line.trim().split("\\s+");
            if (p.length == 0) continue;

            switch (p[0]) {
                case "v" -> { v.add(Float.valueOf(p[1])); v.add(Float.valueOf(p[2])); v.add(Float.valueOf(p[3])); }
                case "vn" -> { vn.add(Float.valueOf(p[1])); vn.add(Float.valueOf(p[2])); vn.add(Float.valueOf(p[3])); }
                case "f" -> {
                    // Sammle alle Vertices der Face (unterstützt Tris, Quads und N-Gons)
                    List<int[]> faceVertices = new ArrayList<>();
                    for (int k = 1; k < p.length; k++) {
                        String[] fv = p[k].split("/");
                        int vi = Integer.parseInt(fv[0]) - 1;
                        int ni = fv.length > 2 && !fv[2].isEmpty() ? Integer.parseInt(fv[2]) - 1 : -1;
                        faceVertices.add(new int[]{vi, ni});
                    }
                    // Fan-Triangulierung
                    for (int i = 0; i < faceVertices.size() - 2; i++) {
                        int[][] triangle = {faceVertices.get(0), faceVertices.get(i + 1), faceVertices.get(i + 2)};
                        for (int[] vertex : triangle) {
                            int vi = vertex[0], ni = vertex[1];
                            buffer.add(v.get(vi * 3)); buffer.add(v.get(vi * 3 + 1)); buffer.add(v.get(vi * 3 + 2));
                            if (ni >= 0 && ni < vn.size() / 3) {
                                buffer.add(vn.get(ni * 3)); buffer.add(vn.get(ni * 3 + 1)); buffer.add(vn.get(ni * 3 + 2));
                            } else {
                                buffer.add(0f); buffer.add(1f); buffer.add(0f);
                            }
                            indices.add(indices.size());
                        }
                    }
                }
            }
        }

        float[] vertArr = new float[buffer.size()];
        for (int i = 0; i < buffer.size(); i++) vertArr[i] = buffer.get(i);
        return new Object3D(objectName, vertArr, indices.stream().mapToInt(i -> i).toArray());
    }
}