package shapeviewer;

import com.jogamp.opengl.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.List;

public class RenderEngine implements GLEventListener {

    private int programId;
    private final List<SceneData.Object3D> objects;

    // Orbit-Kamera Variablen
    public float cameraYaw = 45.0f;      // Horizontale Rotation (Grad)
    public float cameraPitch = 30.0f;    // Vertikale Rotation (Grad), begrenzt auf -85 bis 85
    public float cameraDistance = 8.0f;  // Abstand vom Origin
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
        gl.glClearColor(0.2f, 0.2f, 0.25f, 1.0f);

        // Shader kompilieren
        String vShader = """
            #version 330 core
            layout(location=0) in vec3 aPos;
            layout(location=1) in vec3 aNormal;
            uniform mat4 projection;
            uniform mat4 view;
            uniform mat4 model;
            out vec3 Normal;
            out vec3 FragPos;
            void main() {
                gl_Position = projection * view * model * vec4(aPos, 1.0);
                FragPos = vec3(model * vec4(aPos, 1.0));
                Normal = mat3(transpose(inverse(model))) * aNormal;
            }
        """;

        String fShader = """
            #version 330 core
            out vec4 FragColor;
            in vec3 Normal;
            in vec3 FragPos;
            uniform vec3 uColor;
            uniform vec3 lightPos;
            uniform int isSelected;
            void main() {
                vec3 norm = normalize(Normal);
                vec3 lightDir = normalize(lightPos - FragPos);
                float diff = max(dot(norm, lightDir), 0.2); // 0.2 ambient
                vec3 result = diff * uColor;
                if(isSelected == 1) result = result + vec3(0.2, 0.2, 0.0);
                FragColor = vec4(result, 1.0);
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
        float aspect = (float)w / h;

        // Kameraposition aus Orbit-Parametern berechnen
        Vector3f camPos = calculateCameraPosition();

        Matrix4f proj = new Matrix4f().perspective((float)Math.toRadians(fov), aspect, 0.1f, 100f);
        Matrix4f view = new Matrix4f().lookAt(camPos, camTarget, new Vector3f(0, 1, 0));

        gl.glUniformMatrix4fv(gl.glGetUniformLocation(programId, "projection"), 1, false, proj.get(new float[16]), 0);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(programId, "view"), 1, false, view.get(new float[16]), 0);

        gl.glUniform3f(gl.glGetUniformLocation(programId, "lightPos"), 5, 5, 5);

        // Grid zuerst rendern (mit GL_LINES)
        gl.glUniform1i(gl.glGetUniformLocation(programId, "isSelected"), 0);
        grid.renderLines(gl, programId);

        // Objekte rendern
        synchronized(objects) {
            for(SceneData.Object3D obj : objects) {
                gl.glUniform1i(gl.glGetUniformLocation(programId, "isSelected"), (obj == selectedObject) ? 1 : 0);
                obj.render(gl, programId);
            }
        }
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) {}
    @Override public void dispose(GLAutoDrawable d) {}

    private int createProgram(GL2 gl, String vSrc, String fSrc) {
        int v = gl.glCreateShader(GL2.GL_VERTEX_SHADER);
        gl.glShaderSource(v, 1, new String[]{vSrc}, null);
        gl.glCompileShader(v);

        int f = gl.glCreateShader(GL2.GL_FRAGMENT_SHADER);
        gl.glShaderSource(f, 1, new String[]{fSrc}, null);
        gl.glCompileShader(f);

        int p = gl.glCreateProgram();
        gl.glAttachShader(p, v);
        gl.glAttachShader(p, f);
        gl.glLinkProgram(p);
        return p;
    }
}