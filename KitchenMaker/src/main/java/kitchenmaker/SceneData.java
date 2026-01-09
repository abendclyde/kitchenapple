package kitchenmaker;

import com.jogamp.opengl.GL2;

import java.io.*;
import java.nio.*;
import java.util.*;

/**
 * Zentrale Datenstruktur und Utility-Klasse für die Szene.
 * <p>
 * Diese Klasse definiert die Struktur eines 3D-Objekts (`Object3D`) inklusive seiner
 * Transformationsdaten (Position, Rotation, Skalierung) und Rendering-Informationen (VAO/VBO).
 * Zudem stellt sie statische Methoden zum Laden von Wavefront-OBJ-Dateien, zum Erzeugen
 * von Standard-Geometrien (wie dem Bodengitter) und zur Verwaltung von Animationen bereit.
 *
 * @author Niklas Puls
 */
public class SceneData {

    /**
     * Enum zur Definition der verschiedenen Animationsmodi beim Hinzufügen von Objekten.
     */
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

    /**
     * Repräsentiert ein einzelnes 3D-Objekt in der Szene.
     * Kapselt Geometriedaten (Vertices, Indizes), Transformationszustand und OpenGL-Puffer-IDs.
     */
    public static class Object3D {
        public String name;

        // Geometriedaten (Interleaved: x,y,z, nx,ny,nz)
        public float[] vertices;
        public int[] indices;

        // Transformationsvektoren
        public Vec3 worldPosition = new Vec3(0, 0, 0);
        public Vec3 rotationAngles = new Vec3(0, 0, 0);
        public Vec3 scaleFactors = new Vec3(1, 1, 1);
        public Vec3 color = new Vec3(0.8f, 0.8f, 0.8f);

        // Animationsstatus
        public boolean isAnimating = false;
        public AppearanceMode animationMode = AppearanceMode.NONE;
        public long animationStartTime = 0;
        public float animationDuration = 0; // Dauer in Sekunden

        // Interpolationsziele und Startwerte
        public Vec3 animationTargetPosition = new Vec3();
        public Vec3 animationTargetScale = new Vec3(1, 1, 1);
        public Vec3 animationStartPosition = new Vec3();
        public Vec3 animationStartScale = new Vec3(1, 1, 1);

        // OpenGL-Handle-IDs
        private int vao, vbo, ebo;
        private boolean initialized = false;

        // Axis-Aligned Bounding Box (AABB) für Kollisionserkennung
        public Vec3 boundingBoxMin = new Vec3();
        public Vec3 boundingBoxMax = new Vec3();

        /**
         * Konstruktor: Initialisiert das Objekt mit Geometriedaten und berechnet sofort die Bounding Box.
         */
        public Object3D(String name, float[] vertices, int[] indices) {
            this.name = name;
            this.vertices = vertices;
            this.indices = indices;
            calculateBounds();
        }

        /**
         * Startet eine Animation für das Objekt basierend auf dem gewählten Modus.
         * Setzt Start- und Zielparameter für die Interpolation.
         */
        public void startAnimation(AppearanceMode mode, float durationSeconds) {
            if (mode == AppearanceMode.NONE || durationSeconds <= 0) {
                isAnimating = false;
                return;
            }

            this.animationMode = mode;
            this.animationDuration = durationSeconds;
            this.animationStartTime = System.currentTimeMillis();
            this.isAnimating = true;

            // Speichern des Endzustands (Ziel)
            this.animationTargetPosition.set(this.worldPosition);
            this.animationTargetScale.set(this.scaleFactors);

            // Definition des Startzustands je nach Animationsmodus
            switch (mode) {
                case FALL_DOWN -> {
                    // Startet 5 Einheiten oberhalb der Zielposition
                    this.animationStartPosition.set(this.worldPosition.x, this.worldPosition.y + 5.0f, this.worldPosition.z);
                    this.animationStartScale.set(this.scaleFactors);
                    this.worldPosition.set(this.animationStartPosition);
                }
                case RISE_UP -> {
                    // Startet 5 Einheiten unterhalb der Zielposition
                    this.animationStartPosition.set(this.worldPosition.x, this.worldPosition.y - 5.0f, this.worldPosition.z);
                    this.animationStartScale.set(this.scaleFactors);
                    this.worldPosition.set(this.animationStartPosition);
                }
                case GROW -> {
                    // Startet fast unsichtbar klein (Skalierung nahe 0)
                    this.animationStartPosition.set(this.worldPosition);
                    this.animationStartScale.set(0.01f, 0.01f, 0.01f);
                    this.scaleFactors.set(this.animationStartScale);
                }
                default -> isAnimating = false;
            }
        }

        /**
         * Aktualisiert den Animationsfortschritt pro Frame.
         * Verwendet eine Easing-Funktion für natürlichere Bewegungen.
         * Gibt true zurück, solange die Animation aktiv ist.
         */
        public boolean updateAnimation() {
            if (!isAnimating) return false;

            long currentTime = System.currentTimeMillis();
            float elapsed = (currentTime - animationStartTime) / 1000.0f; // Konvertierung in Sekunden
            float progress = Math.min(1.0f, elapsed / animationDuration);

            // Ease-Out Berechnung: Startet schnell, bremst am Ende ab
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
                default -> {
                }
            }

            // Prüfung auf Animationsende
            if (progress >= 1.0f) {
                worldPosition.set(animationTargetPosition);
                scaleFactors.set(animationTargetScale);
                isAnimating = false;
                return false;
            }

            return true;
        }

        /**
         * Lineare Interpolation zwischen zwei Werten.
         */
        private float lerp(float start, float end, float t) {
            return start + t * (end - start);
        }

        /**
         * Berechnet die minimalen und maximalen Koordinaten (AABB) des Objekts.
         * Wird für Raycasting benötigt.
         */
        private void calculateBounds() {
            boundingBoxMin.set(Float.MAX_VALUE);
            boundingBoxMax.set(-Float.MAX_VALUE);
            // Iteration über alle Vertices (Stride 6: x,y,z, nx,ny,nz)
            for (int i = 0; i < vertices.length; i += 6) {
                float x = vertices[i], y = vertices[i + 1], z = vertices[i + 2];
                boundingBoxMin.x = Math.min(boundingBoxMin.x, x);
                boundingBoxMin.y = Math.min(boundingBoxMin.y, y);
                boundingBoxMin.z = Math.min(boundingBoxMin.z, z);
                boundingBoxMax.x = Math.max(boundingBoxMax.x, x);
                boundingBoxMax.y = Math.max(boundingBoxMax.y, y);
                boundingBoxMax.z = Math.max(boundingBoxMax.z, z);
            }
        }

        public void render(GL2 gl, int modelLoc, int colorLoc) {
            draw(gl, modelLoc, colorLoc, GL2.GL_TRIANGLES);
        }

        public void renderLines(GL2 gl, int modelLoc, int colorLoc) {
            draw(gl, modelLoc, colorLoc, GL2.GL_LINES);
        }

        /**
         * Kern-Render-Methode.
         * Initialisiert Puffer bei Bedarf, setzt Transformationsmatrizen und führt den Draw-Call aus.
         */
        private void draw(GL2 gl, int modelLoc, int colorLoc, int drawMode) {
            if (!initialized) init(gl);

            // Aufbau der Modellmatrix: Translation -> Rotation -> Skalierung
            Mat4 modelMatrix = new Mat4()
                    .translate(worldPosition)
                    .rotateAroundX(rotationAngles.x)
                    .rotateAroundY(rotationAngles.y)
                    .rotateAroundZ(rotationAngles.z)
                    .scale(scaleFactors);

            // Übermittlung an den Shader
            gl.glUniformMatrix4fv(modelLoc, 1, false, modelMatrix.toFloatArray(), 0);
            gl.glUniform3f(colorLoc, color.x, color.y, color.z);

            gl.glBindVertexArray(vao);
            gl.glDrawElements(drawMode, indices.length, GL2.GL_UNSIGNED_INT, 0);
            gl.glBindVertexArray(0);
        }

        /**
         * Initialisiert Vertex Array Object (VAO) und Buffer Objects (VBO, EBO).
         * Lädt die Geometriedaten in den Grafikspeicher.
         */
        private void init(GL2 gl) {
            int[] buffers = new int[3];
            gl.glGenVertexArrays(1, buffers, 0);
            vao = buffers[0];
            gl.glGenBuffers(2, buffers, 1);
            vbo = buffers[1];
            ebo = buffers[2];

            gl.glBindVertexArray(vao);

            // Vertex Buffer: Enthält Positionen und Normalen
            gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, vbo);
            gl.glBufferData(GL2.GL_ARRAY_BUFFER, (long) vertices.length * 4, FloatBuffer.wrap(vertices), GL2.GL_STATIC_DRAW);

            // Element Buffer: Enthält die Indizes für DrawElements
            gl.glBindBuffer(GL2.GL_ELEMENT_ARRAY_BUFFER, ebo);
            gl.glBufferData(GL2.GL_ELEMENT_ARRAY_BUFFER, (long) indices.length * 4, IntBuffer.wrap(indices), GL2.GL_STATIC_DRAW);

            // Attribut 0: Position (3 Floats)
            gl.glEnableVertexAttribArray(0);
            gl.glVertexAttribPointer(0, 3, GL2.GL_FLOAT, false, 6 * 4, 0);

            // Attribut 1: Normale (3 Floats), Offset 12 Bytes (3*4)
            gl.glEnableVertexAttribArray(1);
            gl.glVertexAttribPointer(1, 3, GL2.GL_FLOAT, false, 6 * 4, 3 * 4);

            gl.glBindVertexArray(0);
            initialized = true;
        }
    }

    /**
     * Erzeugt ein Gitter-Objekt für den Boden.
     * Dient als visuelle Referenz im Raum.
     */
    public static Object3D createGrid(int size, float spacing) {
        List<Float> verts = new ArrayList<>();
        List<Integer> inds = new ArrayList<>();
        float halfSize = size * spacing / 2.0f;
        int idx = 0;

        for (int i = -size / 2; i <= size / 2; i++) {
            float p = i * spacing;
            // Linien entlang der X-Achse
            Collections.addAll(verts, -halfSize, 0f, p, 0f, 1f, 0f);
            Collections.addAll(verts, halfSize, 0f, p, 0f, 1f, 0f);
            inds.add(idx++);
            inds.add(idx++);
            // Linien entlang der Z-Achse
            Collections.addAll(verts, p, 0f, -halfSize, 0f, 1f, 0f);
            Collections.addAll(verts, p, 0f, halfSize, 0f, 1f, 0f);
            inds.add(idx++);
            inds.add(idx++);
        }

        float[] vArr = new float[verts.size()];
        for (int i = 0; i < verts.size(); i++) vArr[i] = verts.get(i);
        int[] iArr = inds.stream().mapToInt(i -> i).toArray();

        Object3D grid = new Object3D("Grid", vArr, iArr);
        grid.color.set(0.25f, 0.28f, 0.35f); // Dezentes Blaugrau
        return grid;
    }

    /**
     * Lädt ein 3D-Objekt aus einer Datei im Dateisystem (für Import-Funktion).
     */
    public static Object3D loadObj(File file) {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            return parseObj(br, file.getName());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Mapping-Tabelle für interne Typen zu Ressourcendateien und Metadaten
    private static final Map<String, Object[]> OBJECT_DEFINITIONS = Map.of(
            "Fridge", new Object[]{"kuehlschrank.obj", "Kühlschrank", new float[]{0.9f, 0.95f, 1.0f}},
            "Microwave", new Object[]{"mikrowelle.obj", "Mikrowelle", new float[]{0.2f, 0.2f, 0.2f}},
            "Oven", new Object[]{"backofen.obj", "Backofen", new float[]{0.15f, 0.15f, 0.15f}},
            "Counter", new Object[]{"theke.obj", "Theke", new float[]{0.6f, 0.5f, 0.4f}},
            "Counter Inner Corner", new Object[]{"theke_ecke_innen.obj", "Theke Innenecke", new float[]{0.6f, 0.5f, 0.4f}},
            "Counter Outer Corner", new Object[]{"theke_ecke_aussen.obj", "Theke Außenecke", new float[]{0.6f, 0.5f, 0.4f}},
            "Sink", new Object[]{"waschbecken.obj", "Waschbecken", new float[]{0.8f, 0.85f, 0.9f}}
    );

    /**
     * Erstellt ein 3D-Objekt basierend auf einem von der Webcam erkannten Form-Mapping.
     */
    public static Object3D createFromDetectedShape(ShapeDetector.DetectedShape shape, int objectCount) {
        String name3D = shape.get3DObjectName();
        if (name3D == null) return null;

        Object3D obj = createByType(name3D);
        if (obj != null) {
            obj.name = obj.name + " " + (objectCount + 1);
        }
        return obj;
    }

    /**
     * Methode zum Erstellen von Objekten anhand ihres Typnamens.
     * Lädt die entsprechende OBJ-Ressource aus dem Classpath.
     */
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

    /**
     * Parsed die OBJ-Datei.
     * Unterstützt Vertices (v), Vertex-Normalen (vn) und Faces (f).
     * Trianguliert Polygone automatisch (Triangle Fan).
     */
    private static Object3D parseObj(BufferedReader br, String objectName) throws IOException {
        // Temporäre Listen für Parsing-Daten
        List<Float> v = new ArrayList<>(), vn = new ArrayList<>(), buffer = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        String line;
        while ((line = br.readLine()) != null) {
            String[] p = line.trim().split("\\s+");
            if (p.length == 0 || p[0].isEmpty()) continue;

            switch (p[0]) {
                case "v" -> {
                    // Vertex-Position
                    v.add(Float.valueOf(p[1]));
                    v.add(Float.valueOf(p[2]));
                    v.add(Float.valueOf(p[3]));
                }
                case "vn" -> {
                    // Vertex-Normale
                    vn.add(Float.valueOf(p[1]));
                    vn.add(Float.valueOf(p[2]));
                    vn.add(Float.valueOf(p[3]));
                }
                case "f" -> {
                    // Flächen-Definition (Indizes zu v/vt/vn)
                    List<int[]> faceVertices = new ArrayList<>();
                    for (int k = 1; k < p.length; k++) {
                        String[] fv = p[k].split("/");
                        int vi = Integer.parseInt(fv[0]) - 1; // OBJ ist 1-basiert
                        int ni = fv.length > 2 && !fv[2].isEmpty() ? Integer.parseInt(fv[2]) - 1 : -1;
                        faceVertices.add(new int[]{vi, ni});
                    }

                    // Triangulierung für Polygone mit > 3 Ecken
                    for (int i = 0; i < faceVertices.size() - 2; i++) {
                        int[][] triangle = {faceVertices.get(0), faceVertices.get(i + 1), faceVertices.get(i + 2)};
                        for (int[] vertex : triangle) {
                            int vi = vertex[0], ni = vertex[1];

                            // Position hinzufügen
                            buffer.add(v.get(vi * 3));
                            buffer.add(v.get(vi * 3 + 1));
                            buffer.add(v.get(vi * 3 + 2));

                            // Normale hinzufügen (oder Fallback, falls keine vorhanden)
                            if (ni >= 0 && ni < vn.size() / 3) {
                                buffer.add(vn.get(ni * 3));
                                buffer.add(vn.get(ni * 3 + 1));
                                buffer.add(vn.get(ni * 3 + 2));
                            } else {
                                // Default Up-Vektor als Fallback
                                buffer.add(0f);
                                buffer.add(1f);
                                buffer.add(0f);
                            }
                            indices.add(indices.size());
                        }
                    }
                }
                default -> {
                    // Andere OBJ-Tags (vt, usemtl, etc.) werden aktuell ignoriert
                }
            }
        }

        // Konvertierung in Arrays für OpenGL
        float[] vertArr = new float[buffer.size()];
        for (int i = 0; i < buffer.size(); i++) vertArr[i] = buffer.get(i);
        return new Object3D(objectName, vertArr, indices.stream().mapToInt(i -> i).toArray());
    }
}