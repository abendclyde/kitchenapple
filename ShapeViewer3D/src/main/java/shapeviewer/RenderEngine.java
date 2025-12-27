package shapeviewer;

import com.jogamp.opengl.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.List;

public class RenderEngine implements GLEventListener {

    private int programId;
    private final List<SceneData.Object3D> objects;

    // Orbit-Kamera Variablen
    public float cameraYaw = 45.0f; // Horizontale Rotation (Grad)
    public float cameraPitch = 30.0f; // Vertikale Rotation (Grad), begrenzt auf -85 bis 85
    public float cameraDistance = 8.0f; // Abstand vom Origin
    public Vector3f camTarget = new Vector3f(0, 0, 0);
    public float fov = 60.0f;

    public SceneData.Object3D selectedObject = null;

    // Grid für den Boden
    private SceneData.Object3D grid;

    public RenderEngine(List<SceneData.Object3D> objects) {
        this.objects = objects;
        // Grid erstellen: 20x20, 1 Unit Abstand
        this.grid = SceneData.createGrid(20, 1.0f);
    }

    // Berechnet Kameraposition aus sphärischen Koordinaten
    private Vector3f calculateCameraPosition() {
        float pitchRad = (float) Math.toRadians(cameraPitch);
        float yawRad = (float) Math.toRadians(cameraYaw);

        float x = cameraDistance * (float) Math.cos(pitchRad) * (float) Math.sin(yawRad);
        float y = cameraDistance * (float) Math.sin(pitchRad);
        float z = cameraDistance * (float) Math.cos(pitchRad) * (float) Math.cos(yawRad);

        return new Vector3f(x, y, z).add(camTarget);
    }

    @Override
    public void init(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        gl.glEnable(GL2.GL_DEPTH_TEST);
        gl.glDepthFunc(GL2.GL_LEQUAL);
        gl.glClearColor(0.2f, 0.2f, 0.25f, 1.0f);

        // Shader kompilieren - GLSL 120 für macOS Kompatibilität
        String vShader = """
                    #version 120
                    attribute vec3 aPos;
                    attribute vec3 aNormal;
                    uniform mat4 projection;
                    uniform mat4 view;
                    uniform mat4 model;
                    varying vec3 Normal;
                    varying vec3 FragPos;
                    void main() {
                        gl_Position = projection * view * model * vec4(aPos, 1.0);
                        FragPos = vec3(model * vec4(aPos, 1.0));
                        Normal = mat3(model) * aNormal;
                    }
                """;

        String fShader = """
                    #version 120
                    varying vec3 Normal;
                    varying vec3 FragPos;
                    uniform vec3 uColor;
                    uniform vec3 lightPos;
                    uniform int isSelected;
                    void main() {
                        vec3 norm = normalize(Normal);
                        vec3 lightDir = normalize(lightPos - FragPos);
                        float diff = max(dot(norm, lightDir), 0.2);
                        vec3 result = diff * uColor;
                        if(isSelected == 1) result = result + vec3(0.2, 0.2, 0.0);
                        gl_FragColor = vec4(result, 1.0);
                    }
                """;

        programId = createProgram(gl, vShader, fShader);
    }

    @Override
    public void display(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);
        gl.glUseProgram(programId);

        int w = drawable.getSurfaceWidth();
        int h = drawable.getSurfaceHeight();
        float aspect = (float) w / h;

        // Kameraposition aus Orbit-Parametern berechnen
        Vector3f camPos = calculateCameraPosition();

        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(fov), aspect, 0.1f, 100f);
        Matrix4f view = new Matrix4f().lookAt(camPos, camTarget, new Vector3f(0, 1, 0));

        gl.glUniformMatrix4fv(gl.glGetUniformLocation(programId, "projection"), 1, false, proj.get(new float[16]), 0);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(programId, "view"), 1, false, view.get(new float[16]), 0);

        gl.glUniform3f(gl.glGetUniformLocation(programId, "lightPos"), 5, 5, 5);

        // Grid zuerst rendern (mit GL_LINES)
        gl.glUniform1i(gl.glGetUniformLocation(programId, "isSelected"), 0);
        grid.renderLines(gl, programId);

        // Objekte rendern
        synchronized (objects) {
            for (SceneData.Object3D obj : objects) {
                gl.glUniform1i(gl.glGetUniformLocation(programId, "isSelected"), (obj == selectedObject) ? 1 : 0);
                obj.render(gl, programId);
            }
        }
    }

    @Override
    public void reshape(GLAutoDrawable d, int x, int y, int w, int h) {
    }

    @Override
    public void dispose(GLAutoDrawable d) {
    }

    private int createProgram(GL2 gl, String vSrc, String fSrc) {
        int v = gl.glCreateShader(GL2.GL_VERTEX_SHADER);
        gl.glShaderSource(v, 1, new String[] { vSrc }, null);
        gl.glCompileShader(v);

        // Check vertex shader compilation
        int[] compiled = new int[1];
        gl.glGetShaderiv(v, GL2.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            int[] logLength = new int[1];
            gl.glGetShaderiv(v, GL2.GL_INFO_LOG_LENGTH, logLength, 0);
            byte[] log = new byte[logLength[0]];
            gl.glGetShaderInfoLog(v, logLength[0], null, 0, log, 0);
            System.err.println("[ERROR] Vertex Shader Compilation Failed:\n" + new String(log));
        } else {
            System.out.println("[OK] Vertex Shader compiled successfully");
        }

        int f = gl.glCreateShader(GL2.GL_FRAGMENT_SHADER);
        gl.glShaderSource(f, 1, new String[] { fSrc }, null);
        gl.glCompileShader(f);

        // Check fragment shader compilation
        gl.glGetShaderiv(f, GL2.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            int[] logLength = new int[1];
            gl.glGetShaderiv(f, GL2.GL_INFO_LOG_LENGTH, logLength, 0);
            byte[] log = new byte[logLength[0]];
            gl.glGetShaderInfoLog(f, logLength[0], null, 0, log, 0);
            System.err.println("[ERROR] Fragment Shader Compilation Failed:\n" + new String(log));
        } else {
            System.out.println("[OK] Fragment Shader compiled successfully");
        }

        int p = gl.glCreateProgram();
        gl.glAttachShader(p, v);
        gl.glAttachShader(p, f);

        // Bind attribute locations explicitly for GLSL 120 compatibility
        gl.glBindAttribLocation(p, 0, "aPos");
        gl.glBindAttribLocation(p, 1, "aNormal");

        gl.glLinkProgram(p);

        // Check program linking
        int[] linked = new int[1];
        gl.glGetProgramiv(p, GL2.GL_LINK_STATUS, linked, 0);
        if (linked[0] == 0) {
            int[] logLength = new int[1];
            gl.glGetProgramiv(p, GL2.GL_INFO_LOG_LENGTH, logLength, 0);
            byte[] log = new byte[logLength[0]];
            gl.glGetProgramInfoLog(p, logLength[0], null, 0, log, 0);
            System.err.println("[ERROR] Shader Program Linking Failed:\n" + new String(log));
        } else {
            System.out.println("[OK] Shader Program linked successfully");
        }

        return p;
    }
}