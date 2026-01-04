# ShapeViewer3D - Technische Dokumentation

## Inhaltsverzeichnis
1. [Projektübersicht](#projektübersicht)
2. [Architektur](#architektur)
3. [Hauptklassen](#hauptklassen)
4. [Wichtige Code-Elemente](#wichtige-code-elemente)
5. [Technologien & Bibliotheken](#technologien--bibliotheken)
6. [Interaktionsmuster](#interaktionsmuster)

---

## Projektübersicht

**ShapeViewer3D** ist eine Java-Desktop-Anwendung zur 3D-Visualisierung geometrischer Formen. Die Anwendung kombiniert OpenGL-basiertes 3D-Rendering mit Computer-Vision-Funktionen zur automatischen Formerkennung über Webcam.

### Hauptfunktionen:
- 3D-Rendering verschiedener geometrischer Formen (Pyramide, Würfel, Kugel, Zylinder, Torus, Ebene)
- OBJ-Datei-Import
- Interaktive Kamerasteuerung (Orbit-Kamera)
- Objekt-Manipulation per Drag & Drop
- Webcam-basierte Formerkennung (OpenCV)
- Automatische 3D-Objekterstellung basierend auf erkannten Formen

---

## Architektur

```
┌─────────────────────────────────────────────────────────────────┐
│                       ShapeViewerApp                            │
│        (Hauptfenster, UI-Steuerung, Event-Handling)             │
├─────────────────────────────────────────────────────────────────┤
│                    ┌──────────────────────────────┐             │
│                    │       RenderEngine           │             │
│                    │  (OpenGL-Rendering, Shader)  │             │
│                    └──────────────────────────────┘             │
│                    ┌──────────────────────────────┐             │
│                    │       SceneData              │             │
│                    │ (3D-Objekte, Geometrie)      │             │
│                    └──────────────────────────────┘             │
│                    ┌──────────────────────────────┐             │
│                    │      ShapeDetector           │             │
│                    │ (OpenCV Formerkennung)       │             │
│                    └──────────────────────────────┘             │
└─────────────────────────────────────────────────────────────────┘
```

---

## Hauptklassen

### 1. ShapeViewerApp.java (776 Zeilen)

Die zentrale Klasse, die als Hauptfenster (`JFrame`) dient und alle UI-Komponenten verwaltet.

#### Wichtige Instanzvariablen:

```java
// Szenenobjekte (thread-sicher)
private final List<SceneData.Object3D> objects = Collections.synchronizedList(new ArrayList<>());

// OpenGL-Komponenten
private final RenderEngine renderer;
private final GLJPanel glCanvas;

// UI-Komponenten für Objektliste
private final DefaultListModel<SceneData.Object3D> listModel;
private final JList<SceneData.Object3D> objectList;

// Webcam-Steuerung
private JLabel lblWebcam;
private boolean webcamRunning = false;
private Thread webcamThread;
private static boolean opencvAvailable = false;

// Formerkennung
private ShapeDetector shapeDetector;
private boolean shapeDetectionEnabled = true;
private long lastShapeDetectionTime = 0;
private static final long SHAPE_DETECTION_COOLDOWN = 3000; // 3 Sekunden

// Drag & Drop
private SceneData.Object3D draggedObject = null;
private boolean isDraggingObject = false;
private static final int DRAG_THRESHOLD = 5;
```

#### Wichtige Methoden:

| Methode | Beschreibung |
|---------|--------------|
| `main()` | Einstiegspunkt; initialisiert FlatLaf Theme und OpenCV |
| `createToolBar()` | Erstellt die Toolbar mit Icons (Import, Add, Edit, Delete, Webcam) |
| `createSidePanel()` | Erstellt das Seitenpanel mit Objektliste und Webcam-Vorschau |
| `showEditDialog()` | Dialog zum Bearbeiten von Objekten (Position, Rotation, Farbe) |
| `setupInteraction()` | Registriert Maus- und Tastatur-Listener |
| `pickObject()` | Raycasting zur Objektselektion per Mausklick |
| `intersectAABB()` | AABB-Schnitttest für Raycasting |
| `moveObjectOnGround()` | Verschiebt Objekte auf der XZ-Ebene |
| `webcamLoop()` | Webcam-Capture-Loop in separatem Thread |
| `showAddShapeDialog()` | Dialog bei erkannter Form |

---

### 2. RenderEngine.java (168 Zeilen)

Implementiert `GLEventListener` und ist verantwortlich für das OpenGL-Rendering.

#### Kamera-Parameter:

```java
public float cameraYaw = 45.0f;      // Horizontale Rotation
public float cameraPitch = 30.0f;    // Vertikale Rotation
public float cameraDistance = 8.0f;  // Abstand zum Zielpunkt
public Vector3f camTarget = new Vector3f(0, 0, 0);  // Fokuspunkt
public float fov = 60.0f;            // Sichtfeld
```

#### Shader-Programme:

**Vertex Shader (GLSL 1.20):**
- Transformiert Vertices mit Model-View-Projection-Matrix
- Übergibt Normalenvektoren und Fragment-Position an Fragment Shader

**Fragment Shader (GLSL 1.20):**
- Implementiert Blinn-Phong-Beleuchtung
- Ambient + Diffuse + Specular Komponenten
- Goldener Rim-Effekt für ausgewählte Objekte

```java
// Beleuchtungsberechnung
float ambient = 0.15;
float diff = max(dot(norm, lightDir), 0.0);
float spec = pow(max(dot(norm, halfDir), 0.0), 32.0);
vec3 result = (ambient + diff * 0.7 + spec * 0.3) * uColor;

// Auswahl-Highlight
if(isSelected == 1) {
    float rim = 1.0 - max(dot(viewDir, norm), 0.0);
    rim = pow(rim, 2.0);
    result += vec3(1.0, 0.8, 0.2) * rim * 0.5;
}
```

#### Wichtige Methoden:

| Methode | Beschreibung |
|---------|--------------|
| `init()` | OpenGL-Initialisierung, Shader-Kompilierung |
| `display()` | Render-Loop: Clear, Setup Matrices, Render Objects |
| `calculateCameraPosition()` | Berechnet Kameraposition aus Orbit-Parametern |
| `compileShader()` | Kompiliert Vertex/Fragment Shader |
| `createProgram()` | Linkt Shader-Programm |

---

### 3. SceneData.java (383 Zeilen)

Enthält die Datenstrukturen und Factory-Methoden für 3D-Objekte.

#### Object3D - Innere Klasse:

```java
public static class Object3D {
    public String name;                           // Anzeigename
    public float[] vertices;                      // Vertex-Daten (pos + normal)
    public int[] indices;                         // Index-Buffer
    public Vector3f position = new Vector3f(0, 0, 0);   // Position
    public Vector3f rotation = new Vector3f(0, 0, 0);   // Rotation (Euler)
    public Vector3f scale = new Vector3f(1, 1, 1);      // Skalierung
    public Vector3f color = new Vector3f(0.8f, 0.8f, 0.8f); // Farbe
    public boolean isLineMode = false;            // Linien-Rendering
    
    private int vao, vbo, ebo;                    // OpenGL Buffer
    private boolean initialized = false;
    public Vector3f min = new Vector3f();         // AABB Minimum
    public Vector3f max = new Vector3f();         // AABB Maximum
}
```

#### Vertex-Format:

Jeder Vertex besteht aus 6 Floats:
```
[posX, posY, posZ, normalX, normalY, normalZ]
```

#### Factory-Methoden:

| Methode | Beschreibung |
|---------|--------------|
| `createPyramid()` | Erstellt Pyramide (18 Vertices, 6 Triangles) |
| `createCube()` | Erstellt Würfel (36 Vertices, 12 Triangles) |
| `createSphere(segments, rings)` | Erstellt UV-Kugel |
| `createCylinder(segments)` | Erstellt Zylinder mit Caps |
| `createTorus(segments, rings)` | Erstellt Torus (Donut) |
| `createPlane()` | Erstellt flache Ebene |
| `createGrid(size, spacing)` | Erstellt Bodengitter |
| `loadObj(file)` | Lädt OBJ-Datei |
| `createFromDetectedShape()` | Erstellt Objekt aus erkannter Form |

#### Rendering-Methoden in Object3D:

```java
public void render(GL2 gl, int shaderId) {
    draw(gl, shaderId, GL2.GL_TRIANGLES);
}

public void renderLines(GL2 gl, int shaderId) {
    draw(gl, shaderId, GL2.GL_LINES);
}

private void draw(GL2 gl, int shaderId, int drawMode) {
    if (!initialized) init(gl);  // Lazy initialization
    
    // Model-Matrix berechnen
    Matrix4f model = new Matrix4f()
        .translate(position)
        .rotateX(rotation.x).rotateY(rotation.y).rotateZ(rotation.z)
        .scale(scale);
    
    // Uniforms setzen
    gl.glUniformMatrix4fv(...);
    gl.glUniform3f(...);
    
    // Zeichnen
    gl.glBindVertexArray(vao);
    gl.glDrawElements(drawMode, indices.length, GL2.GL_UNSIGNED_INT, 0);
    gl.glBindVertexArray(0);
}
```

---

### 4. ShapeDetector.java (202 Zeilen)

Implementiert die Computer-Vision-basierte Formerkennung mittels OpenCV.

#### DetectedShape - Innere Klasse:

```java
public static class DetectedShape {
    public enum ShapeType { TRIANGLE, CIRCLE, RECTANGLE, UNKNOWN }
    public enum ColorType { RED, GREEN, BLUE, UNKNOWN }
    
    public final ShapeType shapeType;
    public final ColorType colorType;
    public final MatOfPoint contour;
    public final Point center;
    public final double area;
}
```

#### Farb-Mapping zu 3D-Objekten:

| 2D-Form | Farbe | 3D-Objekt |
|---------|-------|-----------|
| Dreieck | Rot   | Pyramide  |
| Kreis   | Grün  | Kugel     |
| Viereck | Blau  | Würfel    |

#### HSV-Farbbereiche:

```java
// Rot (zwei Bereiche wegen Hue-Wrap)
private static final Scalar RED_LOW1 = new Scalar(0, 150, 100);
private static final Scalar RED_HIGH1 = new Scalar(10, 255, 255);
private static final Scalar RED_LOW2 = new Scalar(160, 150, 100);
private static final Scalar RED_HIGH2 = new Scalar(180, 255, 255);

// Grün
private static final Scalar GREEN_LOW = new Scalar(35, 120, 80);
private static final Scalar GREEN_HIGH = new Scalar(85, 255, 255);

// Blau
private static final Scalar BLUE_LOW = new Scalar(100, 150, 80);
private static final Scalar BLUE_HIGH = new Scalar(130, 255, 255);
```

#### Erkennungs-Pipeline:

1. **Farbkonvertierung**: BGR → HSV
2. **Farbfilterung**: `Core.inRange()` für jede Farbe
3. **Morphologische Operationen**: Open + Close zum Entrauschen
4. **Konturfindung**: `Imgproc.findContours()`
5. **Kontur-Approximation**: `Imgproc.approxPolyDP()`
6. **Form-Klassifizierung**: Basierend auf Vertex-Anzahl

#### Form-Klassifizierung:

```java
private DetectedShape.ShapeType classifyShape(int vertices, MatOfPoint contour, double area) {
    if (vertices == 3) {
        return DetectedShape.ShapeType.TRIANGLE;
    } else if (vertices == 4) {
        // Prüfe Aspect Ratio und Fill Ratio
        Rect boundingRect = Imgproc.boundingRect(contour);
        double aspectRatio = (double) boundingRect.width / boundingRect.height;
        double fillRatio = area / (boundingRect.width * boundingRect.height);
        if (fillRatio > 0.75 && aspectRatio >= 0.5 && aspectRatio <= 2.0) {
            return DetectedShape.ShapeType.RECTANGLE;
        }
    } else if (vertices > 6) {
        // Zirkularität prüfen
        double circularity = (4 * Math.PI * area) / (perimeter * perimeter);
        if (circularity > 0.75) return DetectedShape.ShapeType.CIRCLE;
    }
    return DetectedShape.ShapeType.UNKNOWN;
}
```

---

## Wichtige Code-Elemente

### Raycasting / Object Picking

Die Anwendung verwendet Raycasting zur Objektselektion:

```java
private SceneData.Object3D pickObject(int mouseX, int mouseY) {
    // 1. Mauskoordinaten in NDC (Normalized Device Coordinates)
    float ndcX = (2.0f * mouseX) / w - 1.0f;
    float ndcY = 1.0f - (2.0f * mouseY) / h;
    
    // 2. Inverse View-Projection-Matrix
    Matrix4f invVP = new Matrix4f(proj).mul(view).invert();
    
    // 3. Ray berechnen (Near → Far)
    Vector4f rayNear = new Vector4f(ndcX, ndcY, -1, 1).mul(invVP);
    Vector4f rayFar = new Vector4f(ndcX, ndcY, 1, 1).mul(invVP);
    
    // 4. AABB-Schnitttest für jedes Objekt
    for (SceneData.Object3D obj : objects) {
        float t = intersectAABB(rayOrigin, rayDir, obj);
        if (t > 0 && t < closestDist) {
            closestDist = t;
            closest = obj;
        }
    }
    return closest;
}
```

### Thread-sichere Objektliste

Die Objektliste ist thread-sicher für parallelen Zugriff (UI-Thread + Webcam-Thread):

```java
private final List<SceneData.Object3D> objects = 
    Collections.synchronizedList(new ArrayList<>());

// Bei Iteration synchronisieren:
synchronized (objects) {
    for (SceneData.Object3D obj : objects) {
        obj.render(gl, programId);
    }
}
```

### Lazy OpenGL Buffer Initialization

Objekte werden erst beim ersten Rendern initialisiert:

```java
private void draw(GL2 gl, int shaderId, int drawMode) {
    if (!initialized) init(gl);  // Lazy initialization
    // ...
}

private void init(GL2 gl) {
    // VAO, VBO, EBO erstellen
    int[] buffers = new int[3];
    gl.glGenVertexArrays(1, buffers, 0);
    vao = buffers[0];
    gl.glGenBuffers(2, buffers, 1);
    vbo = buffers[1];
    ebo = buffers[2];
    // ...
    initialized = true;
}
```

### Orbit-Kamera

Die Kamera rotiert um einen Zielpunkt:

```java
private Vector3f calculateCameraPosition() {
    float pitchRad = (float) Math.toRadians(cameraPitch);
    float yawRad = (float) Math.toRadians(cameraYaw);
    
    float x = cameraDistance * (float) (Math.cos(pitchRad) * Math.sin(yawRad));
    float y = cameraDistance * (float) Math.sin(pitchRad);
    float z = cameraDistance * (float) (Math.cos(pitchRad) * Math.cos(yawRad));
    
    return new Vector3f(x, y, z).add(camTarget);
}
```

---

## Technologien & Bibliotheken

| Bibliothek | Version | Verwendungszweck |
|------------|---------|------------------|
| **JOGL** | - | OpenGL-Binding für Java |
| **JOML** | - | Mathe-Bibliothek (Matrix, Vector) |
| **OpenCV** | - | Computer Vision, Webcam-Zugriff |
| **FlatLaf** | - | Modernes Look-and-Feel (Dark Theme) |
| **Swing** | Java Standard | GUI-Framework |

### Build-System

Das Projekt verwendet **Maven** (`pom.xml`).

---

## Interaktionsmuster

### Tastaturkürzel

| Taste | Aktion |
|-------|--------|
| `Delete` / `Backspace` | Ausgewähltes Objekt löschen |
| `Ctrl+O` | OBJ-Datei importieren |
| Doppelklick (Liste) | Objekt bearbeiten |

### Maussteuerung

| Aktion | Funktion |
|--------|----------|
| Linksklick (Objekt) | Objekt auswählen |
| Linksziehen (Objekt) | Objekt auf Boden verschieben |
| Linksziehen (leer) | Kamera rotieren |
| Mausrad | Zoom (Kameraabstand) |

### Webcam-Formerkennung

1. Webcam über Toolbar-Button starten
2. Formerkennung läuft automatisch
3. Bei erkannter gültiger Form (z.B. rotes Dreieck) erscheint Dialog
4. 3-Sekunden-Cooldown zwischen Erkennungen

---

## Dateistruktur

```
ShapeViewer3D/
├── pom.xml                          # Maven Build-Konfiguration
├── run_mac.sh                       # Start-Script (macOS)
├── run_win.bat                      # Start-Script (Windows)
├── resources/
│   ├── defaultcube.mtl              # Material-Datei
│   └── defaultcube.obj              # Standard-OBJ
├── src/main/
│   ├── java/shapeviewer/
│   │   ├── ShapeViewerApp.java      # Hauptanwendung
│   │   ├── RenderEngine.java        # OpenGL Renderer
│   │   ├── SceneData.java           # 3D-Objekte & Geometrie
│   │   └── ShapeDetector.java       # OpenCV Formerkennung
│   └── resources/icons/             # SVG-Icons für Toolbar
│       ├── camera.svg
│       ├── cube.svg
│       ├── cylinder.svg
│       ├── delete.svg
│       ├── edit.svg
│       ├── import.svg
│       ├── plane.svg
│       ├── plus.svg
│       ├── pyramid.svg
│       ├── sphere.svg
│       └── torus.svg
└── target/                          # Build-Output
```

---

*Dokumentation erstellt am 04.01.2026*

