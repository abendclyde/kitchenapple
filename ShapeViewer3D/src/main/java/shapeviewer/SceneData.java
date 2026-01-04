package shapeviewer;

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

    public static Object3D createPyramid() {
        float[] v = {
                -0.5f, 0, 0.5f, 0, 0.5f, 0.8f, 0.5f, 0, 0.5f, 0, 0.5f, 0.8f, 0, 1, 0, 0, 0.5f, 0.8f, // Front
                0.5f, 0, 0.5f, 0.8f, 0.5f, 0, 0.5f, 0, -0.5f, 0.8f, 0.5f, 0, 0, 1, 0, 0.8f, 0.5f, 0, // Right
                0.5f, 0, -0.5f, 0, 0.5f, -0.8f, -0.5f, 0, -0.5f, 0, 0.5f, -0.8f, 0, 1, 0, 0, 0.5f, -0.8f, // Back
                -0.5f, 0, -0.5f, -0.8f, 0.5f, 0, -0.5f, 0, 0.5f, -0.8f, 0.5f, 0, 0, 1, 0, -0.8f, 0.5f, 0, // Left
                -0.5f, 0, 0.5f, 0, -1, 0, 0.5f, 0, 0.5f, 0, -1, 0, 0.5f, 0, -0.5f, 0, -1, 0, // Bottom 1
                -0.5f, 0, 0.5f, 0, -1, 0, 0.5f, 0, -0.5f, 0, -1, 0, -0.5f, 0, -0.5f, 0, -1, 0 // Bottom 2
        };
        int[] i = { 0,1,2, 3,4,5, 6,7,8, 9,10,11, 12,13,14, 15,16,17 };
        Object3D obj = new Object3D("Pyramid", v, i);
        obj.color.set(1.0f, 0.5f, 0.0f);
        return obj;
    }

    public static Object3D createCube() {
        float[] v = {
            // Front face
            -0.5f, -0.5f,  0.5f,  0, 0, 1,   0.5f, -0.5f,  0.5f,  0, 0, 1,   0.5f,  0.5f,  0.5f,  0, 0, 1,
            -0.5f, -0.5f,  0.5f,  0, 0, 1,   0.5f,  0.5f,  0.5f,  0, 0, 1,  -0.5f,  0.5f,  0.5f,  0, 0, 1,
            // Back face
            -0.5f, -0.5f, -0.5f,  0, 0,-1,  -0.5f,  0.5f, -0.5f,  0, 0,-1,   0.5f,  0.5f, -0.5f,  0, 0,-1,
            -0.5f, -0.5f, -0.5f,  0, 0,-1,   0.5f,  0.5f, -0.5f,  0, 0,-1,   0.5f, -0.5f, -0.5f,  0, 0,-1,
            // Top face
            -0.5f,  0.5f, -0.5f,  0, 1, 0,  -0.5f,  0.5f,  0.5f,  0, 1, 0,   0.5f,  0.5f,  0.5f,  0, 1, 0,
            -0.5f,  0.5f, -0.5f,  0, 1, 0,   0.5f,  0.5f,  0.5f,  0, 1, 0,   0.5f,  0.5f, -0.5f,  0, 1, 0,
            // Bottom face
            -0.5f, -0.5f, -0.5f,  0,-1, 0,   0.5f, -0.5f, -0.5f,  0,-1, 0,   0.5f, -0.5f,  0.5f,  0,-1, 0,
            -0.5f, -0.5f, -0.5f,  0,-1, 0,   0.5f, -0.5f,  0.5f,  0,-1, 0,  -0.5f, -0.5f,  0.5f,  0,-1, 0,
            // Right face
             0.5f, -0.5f, -0.5f,  1, 0, 0,   0.5f,  0.5f, -0.5f,  1, 0, 0,   0.5f,  0.5f,  0.5f,  1, 0, 0,
             0.5f, -0.5f, -0.5f,  1, 0, 0,   0.5f,  0.5f,  0.5f,  1, 0, 0,   0.5f, -0.5f,  0.5f,  1, 0, 0,
            // Left face
            -0.5f, -0.5f, -0.5f, -1, 0, 0,  -0.5f, -0.5f,  0.5f, -1, 0, 0,  -0.5f,  0.5f,  0.5f, -1, 0, 0,
            -0.5f, -0.5f, -0.5f, -1, 0, 0,  -0.5f,  0.5f,  0.5f, -1, 0, 0,  -0.5f,  0.5f, -0.5f, -1, 0, 0,
        };
        int[] indices = new int[36];
        for (int i = 0; i < 36; i++) indices[i] = i;
        Object3D obj = new Object3D("Cube", v, indices);
        obj.color.set(0.3f, 0.6f, 1.0f);
        return obj;
    }

    public static Object3D createSphere(int segments, int rings) {
        List<Float> verts = new ArrayList<>();
        List<Integer> inds = new ArrayList<>();

        for (int y = 0; y <= rings; y++) {
            float v = (float) y / rings;
            float phi = (float) (v * Math.PI);

            for (int x = 0; x <= segments; x++) {
                float u = (float) x / segments;
                float theta = (float) (u * 2 * Math.PI);

                float px = (float) (Math.cos(theta) * Math.sin(phi)) * 0.5f;
                float py = (float) Math.cos(phi) * 0.5f;
                float pz = (float) (Math.sin(theta) * Math.sin(phi)) * 0.5f;

                float nx = px * 2, ny = py * 2, nz = pz * 2;
                float len = (float) Math.sqrt(nx*nx + ny*ny + nz*nz);
                nx /= len; ny /= len; nz /= len;

                Collections.addAll(verts, px, py, pz, nx, ny, nz);
            }
        }

        for (int y = 0; y < rings; y++) {
            for (int x = 0; x < segments; x++) {
                int i0 = y * (segments + 1) + x;
                int i1 = i0 + 1;
                int i2 = i0 + (segments + 1);
                int i3 = i2 + 1;

                inds.add(i0); inds.add(i2); inds.add(i1);
                inds.add(i1); inds.add(i2); inds.add(i3);
            }
        }

        float[] vArr = new float[verts.size()];
        for (int i = 0; i < verts.size(); i++) vArr[i] = verts.get(i);
        int[] iArr = inds.stream().mapToInt(i -> i).toArray();

        Object3D obj = new Object3D("Sphere", vArr, iArr);
        obj.color.set(0.2f, 0.8f, 0.4f);
        return obj;
    }

    public static Object3D createCylinder(int segments) {
        List<Float> verts = new ArrayList<>();
        List<Integer> inds = new ArrayList<>();
        float radius = 0.5f, height = 1.0f;
        int idx = 0;

        // Side vertices
        for (int i = 0; i <= segments; i++) {
            float theta = (float) (i * 2 * Math.PI / segments);
            float x = (float) Math.cos(theta) * radius;
            float z = (float) Math.sin(theta) * radius;
            float nx = (float) Math.cos(theta), nz = (float) Math.sin(theta);

            Collections.addAll(verts, x, -height/2, z, nx, 0f, nz); // Bottom
            Collections.addAll(verts, x, height/2, z, nx, 0f, nz);  // Top
        }

        // Side indices
        for (int i = 0; i < segments; i++) {
            int b0 = i * 2, b1 = b0 + 1, b2 = b0 + 2, b3 = b0 + 3;
            inds.add(b0); inds.add(b2); inds.add(b1);
            inds.add(b1); inds.add(b2); inds.add(b3);
        }
        idx = (segments + 1) * 2;

        // Top cap center
        int topCenter = idx;
        Collections.addAll(verts, 0f, height/2, 0f, 0f, 1f, 0f);
        idx++;

        for (int i = 0; i <= segments; i++) {
            float theta = (float) (i * 2 * Math.PI / segments);
            float x = (float) Math.cos(theta) * radius;
            float z = (float) Math.sin(theta) * radius;
            Collections.addAll(verts, x, height/2, z, 0f, 1f, 0f);
            if (i > 0) {
                inds.add(topCenter); inds.add(idx - 1); inds.add(idx);
            }
            idx++;
        }

        // Bottom cap center
        int botCenter = idx;
        Collections.addAll(verts, 0f, -height/2, 0f, 0f, -1f, 0f);
        idx++;

        for (int i = 0; i <= segments; i++) {
            float theta = (float) (i * 2 * Math.PI / segments);
            float x = (float) Math.cos(theta) * radius;
            float z = (float) Math.sin(theta) * radius;
            Collections.addAll(verts, x, -height/2, z, 0f, -1f, 0f);
            if (i > 0) {
                inds.add(botCenter); inds.add(idx); inds.add(idx - 1);
            }
            idx++;
        }

        float[] vArr = new float[verts.size()];
        for (int i = 0; i < verts.size(); i++) vArr[i] = verts.get(i);
        int[] iArr = inds.stream().mapToInt(i -> i).toArray();

        Object3D obj = new Object3D("Cylinder", vArr, iArr);
        obj.color.set(0.8f, 0.3f, 0.6f);
        return obj;
    }

    public static Object3D createTorus(int segments, int rings) {
        List<Float> verts = new ArrayList<>();
        List<Integer> inds = new ArrayList<>();
        float R = 0.35f; // Major radius
        float r = 0.15f; // Minor radius

        for (int i = 0; i <= rings; i++) {
            float u = (float) (i * 2 * Math.PI / rings);
            float cosU = (float) Math.cos(u), sinU = (float) Math.sin(u);

            for (int j = 0; j <= segments; j++) {
                float v = (float) (j * 2 * Math.PI / segments);
                float cosV = (float) Math.cos(v), sinV = (float) Math.sin(v);

                float x = (R + r * cosV) * cosU;
                float y = r * sinV;
                float z = (R + r * cosV) * sinU;

                float nx = cosV * cosU;
                float ny = sinV;
                float nz = cosV * sinU;

                Collections.addAll(verts, x, y, z, nx, ny, nz);
            }
        }

        for (int i = 0; i < rings; i++) {
            for (int j = 0; j < segments; j++) {
                int i0 = i * (segments + 1) + j;
                int i1 = i0 + 1;
                int i2 = i0 + (segments + 1);
                int i3 = i2 + 1;

                inds.add(i0); inds.add(i2); inds.add(i1);
                inds.add(i1); inds.add(i2); inds.add(i3);
            }
        }

        float[] vArr = new float[verts.size()];
        for (int i = 0; i < verts.size(); i++) vArr[i] = verts.get(i);
        int[] iArr = inds.stream().mapToInt(i -> i).toArray();

        Object3D obj = new Object3D("Torus", vArr, iArr);
        obj.color.set(0.9f, 0.7f, 0.2f);
        return obj;
    }

    public static Object3D createPlane() {
        float[] v = {
            -1f, 0f, -1f,  0, 1, 0,   1f, 0f, -1f,  0, 1, 0,   1f, 0f,  1f,  0, 1, 0,
            -1f, 0f, -1f,  0, 1, 0,   1f, 0f,  1f,  0, 1, 0,  -1f, 0f,  1f,  0, 1, 0,
        };
        int[] indices = {0, 1, 2, 3, 4, 5};
        Object3D obj = new Object3D("Plane", v, indices);
        obj.color.set(0.5f, 0.5f, 0.5f);
        return obj;
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

    public static Object3D createFromDetectedShape(ShapeDetector.DetectedShape shape, int objectCount) {
        String name3D = shape.get3DObjectName();
        if (name3D == null) return null;

        Object3D obj = switch (name3D) {
            case "Pyramide" -> { Object3D o = createPyramid(); o.color.set(0.9f, 0.2f, 0.2f); yield o; }
            case "Kugel" -> { Object3D o = createSphere(24, 16); o.color.set(0.2f, 0.9f, 0.3f); yield o; }
            case "Würfel" -> { Object3D o = createCube(); o.color.set(0.2f, 0.4f, 0.9f); yield o; }
            default -> null;
        };
        if (obj != null) obj.name = obj.name + " " + (objectCount + 1);
        return obj;
    }
}