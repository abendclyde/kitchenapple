package kitchenmaker;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import com.jogamp.opengl.GL2;
import java.io.*;
import java.nio.*;
import java.util.*;

public class SceneData {

    public static class Object3D {
        public String name;
        public float[] vertices;
        public int[] indices;
        public Vector3f position = new Vector3f(0, 0, 0);
        public Vector3f rotation = new Vector3f(0, 0, 0);
        public Vector3f scale = new Vector3f(1, 1, 1);
        public Vector3f color = new Vector3f(0.8f, 0.8f, 0.8f);

        private int vao, vbo, ebo;
        private boolean initialized = false;
        public Vector3f min = new Vector3f();
        public Vector3f max = new Vector3f();

        public Object3D(String name, float[] vertices, int[] indices) {
            this.name = name;
            this.vertices = vertices;
            this.indices = indices;
            calculateBounds();
        }

        private void calculateBounds() {
            min.set(Float.MAX_VALUE);
            max.set(-Float.MAX_VALUE);
            for (int i = 0; i < vertices.length; i += 6) {
                float x = vertices[i], y = vertices[i+1], z = vertices[i+2];
                min.x = Math.min(min.x, x); min.y = Math.min(min.y, y); min.z = Math.min(min.z, z);
                max.x = Math.max(max.x, x); max.y = Math.max(max.y, y); max.z = Math.max(max.z, z);
            }
        }

        public void render(GL2 gl, int shaderId) {
            draw(gl, shaderId, GL2.GL_TRIANGLES);
        }

        public void renderLines(GL2 gl, int shaderId) {
            draw(gl, shaderId, GL2.GL_LINES);
        }

        private void draw(GL2 gl, int shaderId, int drawMode) {
            if (!initialized) init(gl);

            Matrix4f model = new Matrix4f()
                    .translate(position)
                    .rotateX(rotation.x).rotateY(rotation.y).rotateZ(rotation.z)
                    .scale(scale);

            gl.glUniformMatrix4fv(gl.glGetUniformLocation(shaderId, "model"), 1, false, model.get(new float[16]), 0);
            gl.glUniform3f(gl.glGetUniformLocation(shaderId, "uColor"), color.x, color.y, color.z);

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
        List<Float> v = new ArrayList<>(), vn = new ArrayList<>(), buffer = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.trim().split("\\s+");
                if (p.length == 0) continue;

                if (p[0].equals("v")) {
                    v.add(Float.valueOf(p[1])); v.add(Float.valueOf(p[2])); v.add(Float.valueOf(p[3]));
                } else if (p[0].equals("vn")) {
                    vn.add(Float.valueOf(p[1])); vn.add(Float.valueOf(p[2])); vn.add(Float.valueOf(p[3]));
                } else if (p[0].equals("f")) {
                    // Sammle alle Vertices der Face (unterstützt Tris, Quads und N-Gons)
                    List<int[]> faceVertices = new ArrayList<>();
                    for (int k = 1; k < p.length; k++) {
                        String[] fv = p[k].split("/");
                        int vi = Integer.parseInt(fv[0]) - 1;
                        int ni = fv.length > 2 && !fv[2].isEmpty() ? Integer.parseInt(fv[2]) - 1 : -1;
                        faceVertices.add(new int[]{vi, ni});
                    }

                    // Fan-Triangulierung: Für n Vertices erzeuge (n-2) Dreiecke
                    for (int i = 0; i < faceVertices.size() - 2; i++) {
                        int[][] triangle = {faceVertices.get(0), faceVertices.get(i + 1), faceVertices.get(i + 2)};

                        for (int[] vertex : triangle) {
                            int vi = vertex[0];
                            int ni = vertex[1];

                            buffer.add(v.get(vi * 3)); buffer.add(v.get(vi * 3 + 1)); buffer.add(v.get(vi * 3 + 2));
                            if (ni >= 0 && ni < vn.size()/3) {
                                buffer.add(vn.get(ni * 3)); buffer.add(vn.get(ni * 3 + 1)); buffer.add(vn.get(ni * 3 + 2));
                            } else {
                                buffer.add(0f); buffer.add(1f); buffer.add(0f);
                            }
                            indices.add(indices.size());
                        }
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); return null; }

        float[] vertArr = new float[buffer.size()];
        for (int i=0; i<buffer.size(); i++) vertArr[i] = buffer.get(i);
        return new Object3D(file.getName(), vertArr, indices.stream().mapToInt(i->i).toArray());
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

        Object3D obj = loadObjFromResource(resourcePath, displayName);
        if (obj != null) {
            obj.color.set(color[0], color[1], color[2]);
        }
        return obj;
    }

    private static Object3D loadObjFromResource(String resourcePath, String objectName) {
        List<Float> v = new ArrayList<>(), vn = new ArrayList<>(), buffer = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        try (InputStream is = SceneData.class.getResourceAsStream("/" + resourcePath);
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.trim().split("\\s+");
                if (p.length == 0) continue;

                if (p[0].equals("v")) {
                    v.add(Float.valueOf(p[1])); v.add(Float.valueOf(p[2])); v.add(Float.valueOf(p[3]));
                } else if (p[0].equals("vn")) {
                    vn.add(Float.valueOf(p[1])); vn.add(Float.valueOf(p[2])); vn.add(Float.valueOf(p[3]));
                } else if (p[0].equals("f")) {
                    List<int[]> faceVertices = new ArrayList<>();
                    for (int k = 1; k < p.length; k++) {
                        String[] fv = p[k].split("/");
                        int vi = Integer.parseInt(fv[0]) - 1;
                        int ni = fv.length > 2 && !fv[2].isEmpty() ? Integer.parseInt(fv[2]) - 1 : -1;
                        faceVertices.add(new int[]{vi, ni});
                    }

                    for (int i = 0; i < faceVertices.size() - 2; i++) {
                        int[][] triangle = {faceVertices.get(0), faceVertices.get(i + 1), faceVertices.get(i + 2)};

                        for (int[] vertex : triangle) {
                            int vi = vertex[0];
                            int ni = vertex[1];

                            buffer.add(v.get(vi * 3)); buffer.add(v.get(vi * 3 + 1)); buffer.add(v.get(vi * 3 + 2));
                            if (ni >= 0 && ni < vn.size()/3) {
                                buffer.add(vn.get(ni * 3)); buffer.add(vn.get(ni * 3 + 1)); buffer.add(vn.get(ni * 3 + 2));
                            } else {
                                buffer.add(0f); buffer.add(1f); buffer.add(0f);
                            }
                            indices.add(indices.size());
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        float[] vertArr = new float[buffer.size()];
        for (int i=0; i<buffer.size(); i++) vertArr[i] = buffer.get(i);
        return new Object3D(objectName, vertArr, indices.stream().mapToInt(i->i).toArray());
    }
}