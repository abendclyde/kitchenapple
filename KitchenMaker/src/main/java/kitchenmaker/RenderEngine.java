package kitchenmaker;

import com.jogamp.opengl.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.List;

public class RenderEngine implements GLEventListener {

    private int programId;
    private final List<SceneData.Object3D> objects;

    // Kamera
    public float cameraYaw = 45.0f;
    public float cameraPitch = 30.0f;
    public float cameraDistance = 8.0f;
    public Vector3f camTarget = new Vector3f(0, 0, 0);
    public float fov = 60.0f;

    public SceneData.Object3D selectedObject = null;
    private SceneData.Object3D grid;

    public RenderEngine(List<SceneData.Object3D> objects) {
        this.objects = objects;
        this.grid = SceneData.createGrid(20, 1.0f);
    }

    private Vector3f calculateCameraPosition() {
        float pitchRad = (float) Math.toRadians(cameraPitch);
        float yawRad = (float) Math.toRadians(cameraYaw);
        float x = cameraDistance * (float) (Math.cos(pitchRad) * Math.sin(yawRad));
        float y = cameraDistance * (float) Math.sin(pitchRad);
        float z = cameraDistance * (float) (Math.cos(pitchRad) * Math.cos(yawRad));
        return new Vector3f(x, y, z).add(camTarget);
    }

    @Override
    public void init(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        gl.glEnable(GL2.GL_DEPTH_TEST);
        gl.glDepthFunc(GL2.GL_LEQUAL);
        // Moderner dunkler Hintergrund mit leichtem Blauton
        gl.glClearColor(0.12f, 0.12f, 0.15f, 1.0f);

        String vShader = """
                    #version 120
                    attribute vec3 aPos; attribute vec3 aNormal;
                    uniform mat4 projection; uniform mat4 view; uniform mat4 model;
                    varying vec3 Normal; varying vec3 FragPos;
                    void main() {
                        gl_Position = projection * view * model * vec4(aPos, 1.0);
                        FragPos = vec3(model * vec4(aPos, 1.0));
                        Normal = mat3(model) * aNormal;
                    }
                """;

        String fShader = """
                    #version 120
                    varying vec3 Normal; varying vec3 FragPos;
                    uniform vec3 uColor; uniform vec3 lightPos; uniform vec3 viewPos;
                    uniform int isSelected;
                    void main() {
                        vec3 norm = normalize(Normal);
                        vec3 lightDir = normalize(lightPos - FragPos);
                        
                        // Ambient
                        float ambient = 0.15;
                        
                        // Diffuse
                        float diff = max(dot(norm, lightDir), 0.0);
                        
                        // Specular (Blinn-Phong)
                        vec3 viewDir = normalize(viewPos - FragPos);
                        vec3 halfDir = normalize(lightDir + viewDir);
                        float spec = pow(max(dot(norm, halfDir), 0.0), 32.0);
                        
                        vec3 result = (ambient + diff * 0.7 + spec * 0.3) * uColor;
                        
                        // Auswahl-Highlight: Aufhellung
                        if(isSelected == 1) {
                            result *= 1.3;
                        }
                        
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

        Vector3f camPos = calculateCameraPosition();
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(fov), aspect, 0.1f, 100f);
        Matrix4f view = new Matrix4f().lookAt(camPos, camTarget, new Vector3f(0, 1, 0));

        gl.glUniformMatrix4fv(gl.glGetUniformLocation(programId, "projection"), 1, false, proj.get(new float[16]), 0);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(programId, "view"), 1, false, view.get(new float[16]), 0);
        gl.glUniform3f(gl.glGetUniformLocation(programId, "lightPos"), 5, 8, 5);
        gl.glUniform3f(gl.glGetUniformLocation(programId, "viewPos"), camPos.x, camPos.y, camPos.z);

        // Grid rendern
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

    @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) {}
    @Override public void dispose(GLAutoDrawable d) {}

    private int compileShader(GL2 gl, int type, String src) {
        int shader = gl.glCreateShader(type);
        gl.glShaderSource(shader, 1, new String[]{src}, null);
        gl.glCompileShader(shader);

        int[] compiled = new int[1];
        gl.glGetShaderiv(shader, GL2.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            int[] logLen = new int[1];
            gl.glGetShaderiv(shader, GL2.GL_INFO_LOG_LENGTH, logLen, 0);
            byte[] log = new byte[logLen[0]];
            gl.glGetShaderInfoLog(shader, logLen[0], null, 0, log, 0);
            System.err.println("Shader Compilation Failed (" + (type == GL2.GL_VERTEX_SHADER ? "Vertex" : "Fragment") + "):\n" + new String(log));
            return -1;
        }
        return shader;
    }

    private int createProgram(GL2 gl, String vSrc, String fSrc) {
        int v = compileShader(gl, GL2.GL_VERTEX_SHADER, vSrc);
        int f = compileShader(gl, GL2.GL_FRAGMENT_SHADER, fSrc);
        if (v == -1 || f == -1) return -1;

        int p = gl.glCreateProgram();
        gl.glAttachShader(p, v);
        gl.glAttachShader(p, f);

        gl.glBindAttribLocation(p, 0, "aPos");
        gl.glBindAttribLocation(p, 1, "aNormal");

        gl.glLinkProgram(p);

        int[] linked = new int[1];
        gl.glGetProgramiv(p, GL2.GL_LINK_STATUS, linked, 0);
        if (linked[0] == 0) {
            System.err.println("Program Linking Failed");
            return -1;
        }
        return p;
    }
}