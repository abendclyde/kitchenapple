package kitchenmaker;

import com.jogamp.opengl.*;

import java.util.List;

/**
 * Kernkomponente für das 3D-Rendering.
 * Sie verwaltet die Shader-Programme, die Kamera-Transformationen,
 * die Projektionsmatrizen sowie das eigentliche Zeichnen der Szenenobjekte und des Gitters.
 *
 * @author Niklas Puls
 */
public class RenderEngine implements GLEventListener {

    // Definition des Vertex-Shaders (GLSL Version 1.20).
    // Führt die Transformation der Vertices vom Modellraum in den Clip-Space durch (Model-View-Projection).
    // Berechnet zusätzlich die fragmentbasierten Positionen und Normalen für die Beleuchtungsberechnung.
    private static final String VERTEX_SHADER = """
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

    // Definition des Fragment-Shaders.
    // Implementiert Blinn-Phong, das diffuses, spekulares und ambientes Licht kombiniert.
    // Enthält Logik zur visuellen Hervorhebung selektierter Objekte durch Erhöhung der Helligkeit.
    private static final String FRAGMENT_SHADER = """
                #version 120
                varying vec3 Normal; varying vec3 FragPos;
                uniform vec3 uColor; uniform vec3 lightPos; uniform vec3 viewPos;
                uniform int isSelected;
                void main() {
                    vec3 norm = normalize(Normal);
                    vec3 lightDir = normalize(lightPos - FragPos);
            
                    // Konstanter Umgebungslichtanteil
                    float ambient = 0.15;
            
                    // Berechnung des diffusen Anteils (Lambert)
                    float diff = max(dot(norm, lightDir), 0.0);
            
                    // Berechnung des spekularen Anteils (Blinn-Phong)
                    vec3 viewDir = normalize(viewPos - FragPos);
                    vec3 halfDir = normalize(lightDir + viewDir);
                    float spec = pow(max(dot(norm, halfDir), 0.0), 32.0);
            
                    vec3 result = (ambient + diff * 0.7 + spec * 0.3) * uColor;
            
                    // Visuelles Feedback für Selektion: Helligkeit um 30% erhöhen
                    if(isSelected == 1) {
                        result *= 1.3;
                    }
            
                    gl_FragColor = vec4(result, 1.0);
                }
            """;

    private int programId;
    private final List<SceneData.Object3D> objects;

    // Cache für Uniform-Locations zur Leistungsoptimierung im Render-Loop
    private int locProjection, locView, locModel;
    private int locLightPos, locViewPos, locColor, locIsSelected;

    // Parameter für die Orbit-Kamera-Steuerung
    public float cameraYaw = 45.0f;
    public float cameraPitch = 30.0f;
    public float cameraDistance = 8.0f;
    public Vec3 cameraTarget = new Vec3(0, 0, 0);
    public float fov = 60.0f;

    public SceneData.Object3D selectedObject = null;
    private SceneData.Object3D grid;

    public RenderEngine(List<SceneData.Object3D> objects) {
        this.objects = objects;
        this.grid = SceneData.createGrid(20, 1.0f);
    }

    /**
     * Konvertiert die sphärischen Kamerakoordinaten (Yaw, Pitch, Radius) in kartesische Weltkoordinaten.
     */
    private Vec3 calculateCameraPosition() {
        float pitchInRadians = (float) Math.toRadians(cameraPitch);
        float yawInRadians = (float) Math.toRadians(cameraYaw);

        float x = cameraDistance * (float) (Math.cos(pitchInRadians) * Math.sin(yawInRadians));
        float y = cameraDistance * (float) Math.sin(pitchInRadians);
        float z = cameraDistance * (float) (Math.cos(pitchInRadians) * Math.cos(yawInRadians));

        return new Vec3(x, y, z).add(cameraTarget);
    }

    /**
     * Initialisierung der OpenGL-Ressourcen und Shader-Programme.
     */
    @Override
    public void init(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();

        // Aktivierung des Z-Buffers für korrekte Verdeckung
        gl.glEnable(GL2.GL_DEPTH_TEST);
        gl.glDepthFunc(GL2.GL_LEQUAL);

        // Setzen der Hintergrundfarbe (Dunkelgrau mit leichtem Blaustich)
        gl.glClearColor(0.12f, 0.12f, 0.15f, 1.0f);

        programId = createProgram(gl, VERTEX_SHADER, FRAGMENT_SHADER);

        // Initiales Abrufen der Shader-Uniform-Handles
        locProjection = gl.glGetUniformLocation(programId, "projection");
        locView = gl.glGetUniformLocation(programId, "view");
        locModel = gl.glGetUniformLocation(programId, "model");
        locLightPos = gl.glGetUniformLocation(programId, "lightPos");
        locViewPos = gl.glGetUniformLocation(programId, "viewPos");
        locColor = gl.glGetUniformLocation(programId, "uColor");
        locIsSelected = gl.glGetUniformLocation(programId, "isSelected");
    }

    @Override
    public void display(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();

        // Löschen des Farb- und Tiefenpuffers vor jedem Frame
        gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);
        gl.glUseProgram(programId);

        int w = drawable.getSurfaceWidth();
        int h = drawable.getSurfaceHeight();
        float aspect = (float) w / h;

        // Berechnung der View- und Projection-Matrizen
        Vec3 cameraPosition = calculateCameraPosition();
        Mat4 projectionMatrix = new Mat4().setPerspective((float) Math.toRadians(fov), aspect, 0.1f, 100f);
        Mat4 viewMatrix = new Mat4().setLookAt(cameraPosition, cameraTarget, new Vec3(0, 1, 0));

        // Übertragung der globalen Uniforms an den Shader
        gl.glUniformMatrix4fv(locProjection, 1, false, projectionMatrix.toFloatArray(), 0);
        gl.glUniformMatrix4fv(locView, 1, false, viewMatrix.toFloatArray(), 0);
        gl.glUniform3f(locLightPos, 5, 8, 5); // Fixe Lichtposition
        gl.glUniform3f(locViewPos, cameraPosition.x, cameraPosition.y, cameraPosition.z);

        // Rendering des Bodenrasters (nicht selektierbar)
        gl.glUniform1i(locIsSelected, 0);
        grid.renderLines(gl, locModel, locColor);

        // Rendering der Szenenobjekte
        // Synchronisation ist notwendig, da die Objektliste aus dem UI-Thread modifiziert werden kann
        synchronized (objects) {
            for (SceneData.Object3D obj : objects) {
                // Berechnung des nächsten Animationsschritts
                obj.updateAnimation();

                // Markierung des aktuell ausgewählten Objekts für den Shader
                gl.glUniform1i(locIsSelected, (obj == selectedObject) ? 1 : 0);
                obj.render(gl, locModel, locColor);
            }
        }
    }

    // Anpassung des OpenGL-Viewports an die Fenstergröße
    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int w, int h) {
        GL2 gl = drawable.getGL().getGL2();
        gl.glViewport(0, 0, w, h);
    }

    @Override
    public void dispose(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        if (programId != 0) {
            gl.glDeleteProgram(programId);
            programId = 0;
        }
    }

    /**
     * Kompiliert den gegebenen GLSL-Quelltext zu einem Shader-Object.
     *
     * Erstellt ein Shader-Handle des angegebenen Typs, lädt den Quelltext und
     * startet die Kompilierung. Prüft den Kompilierungsstatus; falls ein Fehler
     * auftritt, wird der vollständige Compiler-Log ausgelesen, der fehlerhafte
     * Shader gelöscht und eine RuntimeException mit dem Log geworfen.
     */
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
            String shaderType = (type == GL2.GL_VERTEX_SHADER) ? "Vertex" : "Fragment";
            throw new RuntimeException("Shader-Kompilierung fehlgeschlagen (" + shaderType + "):\n" + new String(log));
        }
        return shader;
    }

    /**
     * Erstellt und linkt das Shader-Programm aus Vertex- und Fragment-Shader.
     */
    private int createProgram(GL2 gl, String vertexSrc, String fragmentSrc) {
        int vertexShader = compileShader(gl, GL2.GL_VERTEX_SHADER, vertexSrc);
        int fragmentShader = compileShader(gl, GL2.GL_FRAGMENT_SHADER, fragmentSrc);

        int program = gl.glCreateProgram();
        gl.glAttachShader(program, vertexShader);
        gl.glAttachShader(program, fragmentShader);

        // Explizite Bindung der Vertex-Attribute an Indizes
        gl.glBindAttribLocation(program, 0, "aPos");
        gl.glBindAttribLocation(program, 1, "aNormal");
        gl.glLinkProgram(program);

        int[] linked = new int[1];
        gl.glGetProgramiv(program, GL2.GL_LINK_STATUS, linked, 0);
        if (linked[0] == 0) {
            throw new RuntimeException("Shader-Programm-Linking fehlgeschlagen");
        }
        return program;
    }
}