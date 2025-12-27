package shapeviewer;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import com.jogamp.opengl.GL2;
import java.io.*;
import java.nio.*;
import java.util.*;

// Kombiniert Mesh, Object3D und OBJLoader
public class SceneData {

    public static class Object3D {
        public String name;
        public float[] vertices; // x,y,z, nx,ny,nz interlocked
        public int[] indices;
        public Vector3f position = new Vector3f(0,0,0);
        public Vector3f rotation = new Vector3f(0,0,0);
        public Vector3f scale = new Vector3f(1,1,1);
        public Vector3f color = new Vector3f(0.8f, 0.8f, 0.8f);

        // OpenGL IDs
        private int vao, vbo, ebo;
        private boolean initialized = false;

        // Bounding Box (vereinfacht)
        public Vector3f min = new Vector3f();
        public Vector3f max = new Vector3f();

        public Object3D(String name, float[] vertices, int[] indices) {
            this.name = name;
            this.vertices = vertices;
            this.indices = indices;
            calculateBounds();
        }

        private void calculateBounds() {
            min.set(Float.MAX_VALUE); max.set(-Float.MAX_VALUE);
            for(int i=0; i<vertices.length; i+=6) {
                float x = vertices[i], y = vertices[i+1], z = vertices[i+2];
                if(x < min.x) min.x = x; if(y < min.y) min.y = y; if(z < min.z) min.z = z;
                if(x > max.x) max.x = x; if(y > max.y) max.y = y; if(z > max.z) max.z = z;
            }
        }

        public void render(GL2 gl, int shaderId) {
            if(!initialized) init(gl);

            Matrix4f model = new Matrix4f()
                    .translate(position)
                    .rotateX(rotation.x).rotateY(rotation.y).rotateZ(rotation.z)
                    .scale(scale);

            // Upload Model Matrix
            int modelLoc = gl.glGetUniformLocation(shaderId, "model");
            gl.glUniformMatrix4fv(modelLoc, 1, false, model.get(new float[16]), 0);

            // Upload Color
            int colorLoc = gl.glGetUniformLocation(shaderId, "uColor");
            gl.glUniform3f(colorLoc, color.x, color.y, color.z);

            gl.glBindVertexArray(vao);
            gl.glDrawElements(GL2.GL_TRIANGLES, indices.length, GL2.GL_UNSIGNED_INT, 0);
            gl.glBindVertexArray(0);
        }

        private void init(GL2 gl) {
            int[] buffers = new int[3];
            gl.glGenVertexArrays(1, buffers, 0); vao = buffers[0];
            gl.glGenBuffers(2, buffers, 1); vbo = buffers[1]; ebo = buffers[2];

            gl.glBindVertexArray(vao);

            gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, vbo);
            FloatBuffer vb = FloatBuffer.wrap(vertices);
            gl.glBufferData(GL2.GL_ARRAY_BUFFER, (long)vertices.length*4, vb, GL2.GL_STATIC_DRAW);

            gl.glBindBuffer(GL2.GL_ELEMENT_ARRAY_BUFFER, ebo);
            IntBuffer ib = IntBuffer.wrap(indices);
            gl.glBufferData(GL2.GL_ELEMENT_ARRAY_BUFFER, (long)indices.length*4, ib, GL2.GL_STATIC_DRAW);

            // Pos (0) + Normal (1)
            gl.glEnableVertexAttribArray(0);
            gl.glVertexAttribPointer(0, 3, GL2.GL_FLOAT, false, 6*4, 0);
            gl.glEnableVertexAttribArray(1);
            gl.glVertexAttribPointer(1, 3, GL2.GL_FLOAT, false, 6*4, 3*4);

            gl.glBindVertexArray(0);
            initialized = true;
        }
    }

    // --- Simpler Generator für Standardformen ---
    public static Object3D createCube() {
        // Würfel mit Normals (vereinfacht, eigentlich müssten Vertices dupliziert werden für harte Kanten)
        // Der Einfachheit halber hier ein simpler Würfel
        float[] v = {
                -0.5f,-0.5f,0.5f, 0,0,1,  0.5f,-0.5f,0.5f, 0,0,1,  0.5f,0.5f,0.5f, 0,0,1,  -0.5f,0.5f,0.5f, 0,0,1, // Front
                -0.5f,-0.5f,-0.5f, 0,0,-1, -0.5f,0.5f,-0.5f, 0,0,-1, 0.5f,0.5f,-0.5f, 0,0,-1, 0.5f,-0.5f,-0.5f, 0,0,-1 // Back
        };
        // Indizes sind hier stark vereinfacht für das Beispiel. Für korrekte Beleuchtung braucht man 24 Vertices.
        // Wir nutzen hier einen kleinen Hack: Load OBJ ist besser.
        // Dies ist ein Platzhalter. Besser ist createPyramid unten.
        return createPyramid();
    }

    public static Object3D createPyramid() {
        float[] v = {
                -0.5f, 0, 0.5f, 0, 0.5f, 0.8f,  0.5f, 0, 0.5f, 0, 0.5f, 0.8f,  0, 1, 0, 0, 0.5f, 0.8f, // Front
                0.5f, 0, 0.5f, 0.8f, 0.5f, 0,   0.5f, 0, -0.5f, 0.8f, 0.5f, 0, 0, 1, 0, 0.8f, 0.5f, 0, // Right
                0.5f, 0, -0.5f, 0, 0.5f, -0.8f, -0.5f, 0, -0.5f, 0, 0.5f, -0.8f, 0, 1, 0, 0, 0.5f, -0.8f, // Back
                -0.5f, 0, -0.5f, -0.8f, 0.5f, 0, -0.5f, 0, 0.5f, -0.8f, 0.5f, 0, 0, 1, 0, -0.8f, 0.5f, 0, // Left
                -0.5f, 0, 0.5f, 0,-1,0,  0.5f, 0, 0.5f, 0,-1,0,  0.5f, 0, -0.5f, 0,-1,0, // Bottom 1
                -0.5f, 0, 0.5f, 0,-1,0,  0.5f, 0, -0.5f, 0,-1,0, -0.5f, 0, -0.5f, 0,-1,0 // Bottom 2
        };
        int[] i = {
                0,1,2, 3,4,5, 6,7,8, 9,10,11, 12,13,14, 15,16,17
        };
        Object3D obj = new Object3D("Pyramid", v, i);
        obj.color.set(1.0f, 0.5f, 0.0f);
        return obj;
    }

    // Minimalistischer OBJ Loader direkt integriert
    public static Object3D loadObj(File file) {
        List<Float> v = new ArrayList<>();
        List<Float> vn = new ArrayList<>();
        List<Float> buffer = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while((line = br.readLine()) != null) {
                String[] p = line.split("\\s+");
                if(p[0].equals("v")) {
                    v.add(Float.valueOf(p[1])); v.add(Float.valueOf(p[2])); v.add(Float.valueOf(p[3]));
                } else if (p[0].equals("vn")) {
                    vn.add(Float.valueOf(p[1])); vn.add(Float.valueOf(p[2])); vn.add(Float.valueOf(p[3]));
                } else if (p[0].equals("f")) {
                    // Sehr einfacher Triangulator (nimmt an, es sind Dreiecke oder Quads)
                    for(int k=1; k<=3; k++) {
                        String[] fv = p[k].split("/");
                        int vi = Integer.parseInt(fv[0])-1;
                        int ni = fv.length > 2 && !fv[2].isEmpty() ? Integer.parseInt(fv[2])-1 : 0;

                        buffer.add(v.get(vi*3)); buffer.add(v.get(vi*3+1)); buffer.add(v.get(vi*3+2));
                        if(!vn.isEmpty() && ni < vn.size()/3) {
                            buffer.add(vn.get(ni*3)); buffer.add(vn.get(ni*3+1)); buffer.add(vn.get(ni*3+2));
                        } else {
                            buffer.add(0f); buffer.add(1f); buffer.add(0f); // Default normal
                        }
                        indices.add(indices.size());
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); return null; }

        float[] vertArr = new float[buffer.size()];
        for(int i=0;i<buffer.size();i++) vertArr[i] = buffer.get(i);
        int[] indArr = indices.stream().mapToInt(i->i).toArray();

        return new Object3D(file.getName(), vertArr, indArr);
    }
}