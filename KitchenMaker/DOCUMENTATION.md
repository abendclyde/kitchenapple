# KitchenMaker - Technische Dokumentation

Diese Dokumentation erklärt die technischen Implementierungsdetails des KitchenMaker-Projekts - einer 3D-Küchenplanungsanwendung mit OpenCV-basierter Formenerkennung.

**Version:** 1.0  
**Aktualisiert:** 2026-01-05  
**Technologie-Stack:** Java 17, JOGL 2.6.0, OpenCV 4.9.0, FlatLaf 3.4, JOML 1.10.5

---

## Inhaltsverzeichnis

1. [Fenster, Buttons und Theme](#1-fenster-buttons-und-theme)
2. [3D-Ansicht](#2-3d-ansicht)
3. [Click to Select](#3-click-to-select)
4. [Click and Drag für Kamera drehen](#4-click-and-drag-für-kamera-drehen)
5. [Click and Drag für Objekte bewegen](#5-click-and-drag-für-objekte-bewegen)
6. [Kamera laden](#6-kamera-laden)
7. [OBJ-Files lesen und rendern](#7-obj-files-lesen-und-rendern)
8. [Farbauswahl für Objekte](#8-farbauswahl-für-objekte)
9. [Objekterkennung im Webcam-Feed](#9-objekterkennung-im-webcam-feed)
10. [Theme-System](#10-theme-system)
11. [Tastaturkürzel und Bedienung](#11-tastaturkürzel-und-bedienung)

---

## 1. Fenster, Buttons und Theme

### 1.1 FlatLaf Dark Theme

Das Projekt verwendet **FlatLaf** als modernes Look-and-Feel-Framework. Die Initialisierung erfolgt in der `main()`-Methode von `KitchenApp.java`:

```java
FlatDarkLaf.setup();
UIManager.put("Button.arc", 8);       // Abgerundete Ecken für Buttons
UIManager.put("Component.arc", 8);    // Abgerundete Ecken für Komponenten
UIManager.put("TextComponent.arc", 8); // Abgerundete Ecken für Textfelder
```

### 1.2 Hauptfenster

Das Hauptfenster wird als `JFrame` erstellt mit:
- **Größe**: 1400 x 900 Pixel
- **Layout**: `BorderLayout` mit drei Hauptbereichen:
  - `NORTH`: Toolbar mit Aktionsbuttons
  - `CENTER`: OpenGL-Canvas für 3D-Darstellung
  - `WEST`: Seitenpanel mit Objektliste und Webcam-Vorschau

```java
setSize(1400, 900);
setLayout(new BorderLayout(0, 0));
getContentPane().setBackground(new Color(30, 30, 30));
```

### 1.3 Toolbar-Buttons

Die Toolbar-Buttons werden durch die Methode `createToolbarButton()` erstellt:

```java
private JButton createToolbarButton(String iconPath, String tooltip) {
    JButton btn = new JButton();
    FlatSVGIcon icon = new FlatSVGIcon(iconPath, 20, 20);  // SVG-Icon laden
    btn.setIcon(icon);
    btn.setToolTipText(tooltip);
    btn.setFocusPainted(false);
    btn.setBorderPainted(false);
    btn.setContentAreaFilled(false);
    btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    btn.setPreferredSize(new Dimension(36, 36));
    // Hover-Effekt
    btn.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent e) {
            btn.setContentAreaFilled(true);
            btn.setBackground(new Color(60, 60, 70));
        }
        @Override
        public void mouseExited(MouseEvent e) {
            btn.setContentAreaFilled(false);
        }
    });
    return btn;
}
```

**Verfügbare Buttons:**
- **Import** (`import.svg`): OBJ-Dateien importieren (Ctrl+O)
- **Hinzufügen** (`plus.svg`): Öffnet Menü zum Hinzufügen von Küchenobjekten
- **Bearbeiten** (`edit.svg`): Ausgewähltes Objekt bearbeiten (auch per Doppelklick)
- **Löschen** (`delete.svg`): Ausgewähltes Objekt entfernen (auch per Delete-Taste)
- **Webcam** (`camera.svg`): Webcam ein-/ausschalten für automatische Formenerkennung

Das **Hinzufügen-Menü** enthält folgende Küchenelemente:
- **Kühlschrank** (Fridge) - `kuehlschrank.obj`
- **Mikrowelle** (Microwave) - `mikrowelle.obj`
- **Backofen** (Oven) - `backofen.obj`
- **Theke** (Counter) - `theke.obj`
- **Theke Innenecke** (Counter Inner Corner) - `theke_ecke_innen.obj`
- **Theke Außenecke** (Counter Outer Corner) - `theke_ecke_aussen.obj`
- **Waschbecken** (Sink) - `waschbecken.obj`

### 1.4 Seitenpanel

Das Seitenpanel enthält:
1. **Küchenelemente-Liste** (`JList`): Zeigt alle platzierten Küchenmöbel mit farbigen Icons
2. **Webcam-Panel**: Zeigt den Live-Feed mit Echtzeit-Formenerkennung (260x195 Pixel)

Die Küchenelemente-Liste verwendet einen benutzerdefinierten `CellRenderer`:
```java
private static class ObjectListCellRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, 
            int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        
        if (value instanceof SceneData.Object3D obj) {
            setText(obj.name);
            
            // Farbiges Icon basierend auf Objektfarbe
            BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = icon.createGraphics();
            g.setColor(new Color(obj.color.x, obj.color.y, obj.color.z));
            g.fillRect(0, 0, 16, 16);
            g.dispose();
            setIcon(new ImageIcon(icon));
            
            // Auswahl-Highlighting
            if (isSelected) {
                setBackground(Theme.SELECTION);
                setForeground(Theme.TEXT_PRIMARY);
            } else {
                setBackground(Theme.LIST_BACKGROUND);
                setForeground(Theme.TEXT_SECONDARY);
            }
        }
        return this;
    }
}
```

---

## 2. 3D-Ansicht

### 2.1 OpenGL-Initialisierung

Die 3D-Ansicht verwendet **JOGL** (Java OpenGL Bindings). Die Konfiguration erfolgt in `KitchenApp`:

```java
GLProfile glProfile = GLProfile.getDefault();
GLCapabilities glCapabilities = new GLCapabilities(glProfile);
glCapabilities.setDoubleBuffered(true);      // Doppelpufferung für flüssige Darstellung
glCapabilities.setHardwareAccelerated(true); // GPU-Beschleunigung

GLJPanel glCanvas = new GLJPanel(glCapabilities);
glCanvas.addGLEventListener(renderer);       // RenderEngine als Listener
```

### 2.2 RenderEngine

Die `RenderEngine` implementiert `GLEventListener` und ist für das Rendering verantwortlich:

#### Shader-Programme

Es werden **GLSL 1.20 Shader** verwendet. Ein Shader ist ein kleines Programm, das direkt auf der Grafikkarte (GPU) ausgeführt wird. Die Rendering-Pipeline verwendet zwei Shader-Typen:

##### Vertex-Shader (vShader)

Der **Vertex-Shader** wird für jeden Eckpunkt (Vertex) eines 3D-Objekts einmal ausgeführt. Seine Hauptaufgaben sind:

1. **Koordinatentransformation**: Umrechnung der 3D-Objektkoordinaten in 2D-Bildschirmkoordinaten
2. **Datenweiterleitung**: Vorbereitung von Daten für den Fragment-Shader

| Element | Typ | Beschreibung |
|---------|-----|--------------|
| `aPos` | attribute | Eingangsposition des Vertex im lokalen Objektkoordinatensystem |
| `aNormal` | attribute | Normalenvektor des Vertex (für Beleuchtung) |
| `projection` | uniform | Projektionsmatrix (Perspektive, FOV) |
| `view` | uniform | Kameramatrix (Position und Blickrichtung) |
| `model` | uniform | Objektmatrix (Position, Rotation, Skalierung) |
| `Normal` | varying | Transformierte Normale → wird an Fragment-Shader übergeben |
| `FragPos` | varying | Weltposition des Fragments → wird an Fragment-Shader übergeben |

Die Transformation erfolgt durch Matrix-Multiplikation: `projection * view * model * position`

```glsl
#version 120
attribute vec3 aPos; attribute vec3 aNormal;
uniform mat4 projection; uniform mat4 view; uniform mat4 model;
varying vec3 Normal; varying vec3 FragPos;

void main() {
    gl_Position = projection * view * model * vec4(aPos, 1.0);
    FragPos = vec3(model * vec4(aPos, 1.0));
    Normal = mat3(model) * aNormal;
}
```

##### Fragment-Shader (fShader)

Der **Fragment-Shader** wird für jedes Pixel (Fragment) ausgeführt, das ein 3D-Objekt auf dem Bildschirm einnimmt. Er berechnet die endgültige Farbe jedes Pixels basierend auf:

1. **Beleuchtung**: Blinn-Phong-Modell mit Ambient, Diffuse und Specular
2. **Materialfarbe**: Die Objektfarbe (`uColor`)
3. **Auswahlzustand**: Aufhellung für selektierte Objekte

| Element | Typ | Beschreibung |
|---------|-----|--------------|
| `Normal` | varying | Interpolierte Normale vom Vertex-Shader |
| `FragPos` | varying | Interpolierte Weltposition vom Vertex-Shader |
| `uColor` | uniform | Objektfarbe (RGB, 0.0-1.0) |
| `lightPos` | uniform | Position der Lichtquelle in Weltkoordinaten |
| `viewPos` | uniform | Position der Kamera in Weltkoordinaten |
| `isSelected` | uniform | 1 = Objekt ist ausgewählt, 0 = nicht ausgewählt |

**Blinn-Phong Beleuchtungsmodell:**
- **Ambient** (0.15): Grundhelligkeit, simuliert indirektes Licht
- **Diffuse** (0.7): Hauptbeleuchtung, abhängig vom Winkel zwischen Lichtrichtung und Normale
- **Specular** (0.3): Glanzlichter, berechnet mit dem Half-Vektor (Blinn-Variante)

```glsl
#version 120
varying vec3 Normal; varying vec3 FragPos;
uniform vec3 uColor; uniform vec3 lightPos; uniform vec3 viewPos;
uniform int isSelected;

void main() {
    vec3 norm = normalize(Normal);
    vec3 lightDir = normalize(lightPos - FragPos);
    
    // Ambient, Diffuse, Specular
    float ambient = 0.15;
    float diff = max(dot(norm, lightDir), 0.0);
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
```

#### Render-Schleife

Die `display()`-Methode wird 60x pro Sekunde aufgerufen (via `FPSAnimator`):

1. **Buffer löschen**: Farb- und Tiefenpuffer
2. **Matrizen berechnen**: Projection, View basierend auf Kameraposition
3. **Grid rendern**: Bodenraster als Linien
4. **Objekte rendern**: Jedes Object3D mit seiner Model-Matrix

```java
@Override
public void display(GLAutoDrawable drawable) {
    GL2 gl = drawable.getGL().getGL2();
    gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);
    
    // Kamera-Setup
    Vector3f camPos = calculateCameraPosition();
    Matrix4f proj = new Matrix4f().perspective(...);
    Matrix4f view = new Matrix4f().lookAt(camPos, camTarget, ...);
    
    // Uniforms setzen
    gl.glUniformMatrix4fv(..., "projection", ...);
    gl.glUniformMatrix4fv(..., "view", ...);
    
    // Grid und Objekte rendern
    grid.renderLines(gl, programId);
    for (SceneData.Object3D obj : objects) {
        obj.render(gl, programId);
    }
}
```

---

## 3. Click to Select

### 3.1 Ray-Casting Prinzip

Beim Klicken auf das Canvas wird ein **Strahl (Ray)** von der Kamera durch den Mauszeiger in die Szene geschossen.

### 3.2 Ray-Erstellung

```java
private Ray createRayFromMouse(int mouseX, int mouseY) {
    // 1. Mauskoordinaten in NDC (Normalized Device Coordinates) umwandeln
    float ndcX = (2.0f * mouseX) / width - 1.0f;
    float ndcY = 1.0f - (2.0f * mouseY) / height;
    
    // 2. Kameraposition berechnen
    Vector3f camPos = calculateCameraPosition();
    
    // 3. Projection- und View-Matrix invertieren
    Matrix4f invVP = new Matrix4f(projection).mul(view).invert();
    
    // 4. Near- und Far-Plane Punkte berechnen
    Vector4f rayNear = new Vector4f(ndcX, ndcY, -1, 1).mul(invVP);
    Vector4f rayFar = new Vector4f(ndcX, ndcY, 1, 1).mul(invVP);
    
    // 5. Ray-Richtung normalisieren
    Vector3f rayDir = new Vector3f(rayFar.xyz - rayNear.xyz).normalize();
    
    return new Ray(rayOrigin, rayDir);
}
```

### 3.3 AABB-Kollisionserkennung

Für jedes Objekt wird geprüft, ob der Ray dessen **Axis-Aligned Bounding Box (AABB)** schneidet:

```java
private float intersectAABB(Vector3f origin, Vector3f dir, SceneData.Object3D obj) {
    // Bounding Box mit Scale und Position transformieren
    Vector3f min = new Vector3f(obj.min).mul(obj.scale).add(obj.position);
    Vector3f max = new Vector3f(obj.max).mul(obj.scale).add(obj.position);
    
    // Padding für bessere Auswahl
    min.sub(0.2f, 0.2f, 0.2f);
    max.add(0.2f, 0.2f, 0.2f);
    
    // Slab-Methode: t-Werte für jeden Achsenbereich berechnen
    // Rückgabe: t-Wert des Schnittpunkts oder -1 bei keinem Treffer
}
```

### 3.4 Objekt-Auswahl

```java
private SceneData.Object3D pickObject(int mouseX, int mouseY) {
    Ray ray = createRayFromMouse(mouseX, mouseY);
    
    SceneData.Object3D closest = null;
    float closestDist = Float.MAX_VALUE;
    
    for (SceneData.Object3D obj : objects) {
        float t = intersectAABB(ray.origin, ray.direction, obj);
        if (t > 0 && t < closestDist) {
            closestDist = t;
            closest = obj;
        }
    }
    return closest;  // Nächstes getroffenes Objekt
}
```

---

## 4. Click and Drag für Kamera drehen

### 4.1 Kamera-Parameter

Die Kamera verwendet ein **Orbit-System** um einen Zielpunkt:

```java
public float cameraYaw = 45.0f;      // Horizontale Rotation (Grad)
public float cameraPitch = 30.0f;    // Vertikale Rotation (Grad)
public float cameraDistance = 8.0f;  // Abstand zum Zielpunkt
public Vector3f camTarget = new Vector3f(0, 0, 0);  // Zielpunkt
```

### 4.2 Kameraposition berechnen

```java
private Vector3f calculateCameraPosition() {
    float pitchRad = (float) Math.toRadians(cameraPitch);
    float yawRad = (float) Math.toRadians(cameraYaw);
    
    // Kugelkoordinaten zu kartesischen Koordinaten
    float x = cameraDistance * (float)(Math.cos(pitchRad) * Math.sin(yawRad));
    float y = cameraDistance * (float) Math.sin(pitchRad);
    float z = cameraDistance * (float)(Math.cos(pitchRad) * Math.cos(yawRad));
    
    return new Vector3f(x, y, z).add(camTarget);
}
```

### 4.3 Drag-Handling

```java
glCanvas.addMouseMotionListener(new MouseMotionAdapter() {
    @Override
    public void mouseDragged(MouseEvent e) {
        int dx = e.getX() - lastMouseX;
        int dy = e.getY() - lastMouseY;
        
        // Threshold überprüfen (verhindert unbeabsichtigtes Drehen)
        if (Math.abs(dx) > DRAG_THRESHOLD || Math.abs(dy) > DRAG_THRESHOLD) {
            isDragging = true;
        }
        
        if (isDragging && !isDraggingObject) {
            // Kamera rotieren
            renderer.cameraYaw -= dx * 0.5f;
            renderer.cameraPitch = Math.max(-85f, Math.min(85f, 
                renderer.cameraPitch + dy * 0.5f));  // Pitch begrenzen
            glCanvas.repaint();
        }
        
        lastMouseX = e.getX();
        lastMouseY = e.getY();
    }
});

// Zoom mit Mausrad
glCanvas.addMouseWheelListener(e -> 
    renderer.cameraDistance = Math.max(1f, Math.min(50f,
        renderer.cameraDistance + (float)e.getPreciseWheelRotation() * 0.5f))
);
```

---

## 5. Click and Drag für Objekte bewegen

### 5.1 Drag-Initialisierung

Beim Klicken auf ein Objekt wird der **Offset** zwischen Mausposition und Objektposition berechnet:

```java
@Override
public void mousePressed(MouseEvent e) {
    if (SwingUtilities.isLeftMouseButton(e)) {
        SceneData.Object3D clicked = pickObject(e.getX(), e.getY());
        if (clicked != null) {
            draggedObject = clicked;
            
            // Drag-Ebene auf Objekt-Y-Höhe setzen
            dragPlaneY = clicked.position.y;
            
            // Offset berechnen für sanftes Dragging
            Vector3f hitPoint = screenToGroundPlane(e.getX(), e.getY(), dragPlaneY);
            dragOffset.set(hitPoint).sub(clicked.position);
        }
    }
}
```

### 5.2 Screen-to-World Transformation

Der Mauscursor wird auf eine horizontale Ebene projiziert:

```java
private Vector3f screenToGroundPlane(int mouseX, int mouseY, float planeY) {
    Ray ray = createRayFromMouse(mouseX, mouseY);
    
    // Schnittpunkt mit horizontaler Ebene bei Y = planeY
    float t = (planeY - ray.origin.y) / ray.direction.y;
    return new Vector3f(ray.origin).add(new Vector3f(ray.direction).mul(t));
}
```

### 5.3 Objekt-Bewegung

```java
private void moveObjectOnGround(SceneData.Object3D obj, int mouseX, int mouseY) {
    Vector3f hitPoint = screenToGroundPlane(mouseX, mouseY, dragPlaneY);
    
    // Position mit Offset aktualisieren (nur X und Z)
    obj.position.x = hitPoint.x - dragOffset.x;
    obj.position.z = hitPoint.z - dragOffset.z;
}
```

---

## 6. Kamera laden

### 6.1 Webcam-Initialisierung

Die Webcam wird über **OpenCV** (`VideoCapture`) angesprochen:

```java
private void toggleWebcam() {
    if (!opencvAvailable) {
        JOptionPane.showMessageDialog(this, 
            "OpenCV konnte nicht geladen werden.", "OpenCV Fehler", ...);
        return;
    }
    
    if (webcamRunning) {
        webcamRunning = false;
        lblWebcam.setText("Aus");
    } else {
        webcamRunning = true;
        webcamThread = new Thread(this::webcamLoop);
        webcamThread.start();
    }
}
```

### 6.2 Webcam-Loop

```java
private void webcamLoop() {
    // Kamera öffnen (versucht Index 0, dann 1)
    VideoCapture capture = new VideoCapture(0);
    if (!capture.isOpened()) capture = new VideoCapture(1);
    
    if (!capture.isOpened()) {
        System.err.println("Keine Webcam gefunden.");
        return;
    }
    
    Mat frame = new Mat();
    while (webcamRunning) {
        if (capture.read(frame) && !frame.empty()) {
            // Formerkennung durchführen
            if (shapeDetectionEnabled) {
                shapeDetector.detectShapes(frame);
            }
            
            // Frame anzeigen
            BufferedImage img = matToImage(frame);
            Image scaled = img.getScaledInstance(260, 195, Image.SCALE_FAST);
            lblWebcam.setIcon(new ImageIcon(scaled));
        }
        Thread.sleep(50);  // ~20 FPS
    }
    capture.release();
}
```

### 6.3 Mat zu BufferedImage Konvertierung

```java
private BufferedImage matToImage(Mat m) {
    int type = (m.channels() > 1) ? 
        BufferedImage.TYPE_3BYTE_BGR : BufferedImage.TYPE_BYTE_GRAY;
    
    byte[] b = new byte[m.channels() * m.cols() * m.rows()];
    m.get(0, 0, b);
    
    BufferedImage image = new BufferedImage(m.cols(), m.rows(), type);
    System.arraycopy(b, 0, 
        ((DataBufferByte) image.getRaster().getDataBuffer()).getData(), 0, b.length);
    return image;
}
```

---

## 7. OBJ-Files lesen und rendern

### 7.1 OBJ-Parser

Die Methode `SceneData.loadObj()` liest Wavefront OBJ-Dateien:

```java
public static Object3D loadObj(File file) {
    List<Float> v = new ArrayList<>();   // Vertex-Positionen
    List<Float> vn = new ArrayList<>();  // Vertex-Normalen
    List<Float> buffer = new ArrayList<>(); // Finaler Vertex-Buffer
    List<Integer> indices = new ArrayList<>();
    
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
        String line;
        while ((line = br.readLine()) != null) {
            String[] p = line.trim().split("\\s+");
            
            switch (p[0]) {
                case "v" -> {  // Vertex-Position
                    v.add(Float.valueOf(p[1]));
                    v.add(Float.valueOf(p[2]));
                    v.add(Float.valueOf(p[3]));
                }
                case "vn" -> { // Vertex-Normale
                    vn.add(Float.valueOf(p[1]));
                    vn.add(Float.valueOf(p[2]));
                    vn.add(Float.valueOf(p[3]));
                }
                case "f" -> {  // Face (unterstützt Tris, Quads, N-Gons)
                    // Face-Vertices sammeln
                    List<int[]> faceVertices = new ArrayList<>();
                    for (int k = 1; k < p.length; k++) {
                        String[] fv = p[k].split("/");
                        int vi = Integer.parseInt(fv[0]) - 1;
                        int ni = fv.length > 2 ? Integer.parseInt(fv[2]) - 1 : -1;
                        faceVertices.add(new int[]{vi, ni});
                    }
                    
                    // Fan-Triangulierung für N-Gons
                    for (int i = 0; i < faceVertices.size() - 2; i++) {
                        // Dreieck: Vertex 0, i+1, i+2
                        // Vertex-Daten in Buffer schreiben
                    }
                }
            }
        }
    }
    
    return new Object3D(file.getName(), vertArr, indices);
}
```

### 7.2 Objekt-Definitionen

Die verfügbaren Küchenmöbel werden in einer zentralen Map definiert:

```java
private static final Map<String, Object[]> OBJECT_DEFINITIONS = Map.of(
    "Fridge", new Object[]{
        "kuehlschrank.obj", 
        "Kühlschrank", 
        new float[]{0.9f, 0.95f, 1.0f}  // Hellblau/Weiß
    },
    "Microwave", new Object[]{
        "mikrowelle.obj", 
        "Mikrowelle", 
        new float[]{0.2f, 0.2f, 0.2f}   // Dunkelgrau
    },
    "Oven", new Object[]{
        "backofen.obj", 
        "Backofen", 
        new float[]{0.15f, 0.15f, 0.15f} // Schwarz
    },
    "Counter", new Object[]{
        "theke.obj", 
        "Theke", 
        new float[]{0.6f, 0.5f, 0.4f}   // Holzfarbe
    },
    "Counter Inner Corner", new Object[]{
        "theke_ecke_innen.obj", 
        "Theke Innenecke", 
        new float[]{0.6f, 0.5f, 0.4f}
    },
    "Counter Outer Corner", new Object[]{
        "theke_ecke_aussen.obj", 
        "Theke Außenecke", 
        new float[]{0.6f, 0.5f, 0.4f}
    },
    "Sink", new Object[]{
        "waschbecken.obj", 
        "Waschbecken", 
        new float[]{0.8f, 0.85f, 0.9f}  // Silber/Metall
    }
);
```

**Objekt-Erstellung:**
```java
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
```

### 7.3 Vertex-Format

Jeder Vertex besteht aus **6 Floats**:
- Position: `x, y, z`
- Normale: `nx, ny, nz`

### 7.4 Bounding-Box Berechnung

```java
private void calculateBounds() {
    min.set(Float.MAX_VALUE);
    max.set(-Float.MAX_VALUE);
    
    for (int i = 0; i < vertices.length; i += 6) {
        float x = vertices[i], y = vertices[i+1], z = vertices[i+2];
        min.x = Math.min(min.x, x); max.x = Math.max(max.x, x);
        min.y = Math.min(min.y, y); max.y = Math.max(max.y, y);
        min.z = Math.min(min.z, z); max.z = Math.max(max.z, z);
    }
}
```

Die Bounding-Box wird für **Ray-Casting** (Objekt-Selektion per Mausklick) und **Collision Detection** verwendet.

### 7.5 VAO/VBO-Initialisierung

```java
private void init(GL2 gl) {
    int[] buffers = new int[3];
    gl.glGenVertexArrays(1, buffers, 0);
    vao = buffers[0];
    gl.glGenBuffers(2, buffers, 1);
    vbo = buffers[1];
    ebo = buffers[2];
    
    gl.glBindVertexArray(vao);
    
    // Vertex Buffer
    gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, vbo);
    gl.glBufferData(GL2.GL_ARRAY_BUFFER, vertices.length * 4, 
        FloatBuffer.wrap(vertices), GL2.GL_STATIC_DRAW);
    
    // Index Buffer
    gl.glBindBuffer(GL2.GL_ELEMENT_ARRAY_BUFFER, ebo);
    gl.glBufferData(GL2.GL_ELEMENT_ARRAY_BUFFER, indices.length * 4, 
        IntBuffer.wrap(indices), GL2.GL_STATIC_DRAW);
    
    // Vertex-Attribute
    gl.glEnableVertexAttribArray(0);
    gl.glVertexAttribPointer(0, 3, GL2.GL_FLOAT, false, 6*4, 0);      // Position
    gl.glEnableVertexAttribArray(1);
    gl.glVertexAttribPointer(1, 3, GL2.GL_FLOAT, false, 6*4, 3*4);   // Normal
    
    gl.glBindVertexArray(0);
    initialized = true;
}
```

---

## 8. Farbauswahl für Objekte

### 8.1 Bearbeiten-Dialog

Der Farbauswahl-Dialog wird in `showEditDialog()` implementiert:

```java
JPanel colorPanel = createLabeledField("Farbe:");
JButton btnColor = new JButton("  ");

// Initiale Farbe aus Objekt
Color initialColor = new Color(obj.color.x, obj.color.y, obj.color.z);
btnColor.setBackground(initialColor);

// Farbauswahl per JColorChooser
btnColor.addActionListener(e -> {
    Color newColor = JColorChooser.showDialog(dialog, "Farbe wählen", 
        btnColor.getBackground());
    if (newColor != null) {
        btnColor.setBackground(newColor);
    }
});
```

### 8.2 Farbe anwenden

```java
btnApply.addActionListener(e -> {
    // ... andere Eigenschaften ...
    
    // Farbe von AWT-Color zu Vector3f konvertieren
    Color c = btnColor.getBackground();
    obj.color.set(
        c.getRed() / 255f, 
        c.getGreen() / 255f, 
        c.getBlue() / 255f
    );
    
    objectList.repaint();  // Liste aktualisieren
    dialog.dispose();
});
```

### 8.3 Farbe im Shader

Die Farbe wird als Uniform an den Shader übergeben:

```java
gl.glUniform3f(gl.glGetUniformLocation(shaderId, "uColor"), 
    color.x, color.y, color.z);
```

---

## 9. Objekterkennung im Webcam-Feed

### 9.1 ShapeDetector Klasse

Die `ShapeDetector`-Klasse erkennt farbige geometrische Formen im Webcam-Bild.

### 9.2 Farbbereiche (HSV)

```java
// Rot (zwei Bereiche wegen Wrap-Around bei H=0/180)
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

### 9.3 Erkennungsalgorithmus

```java
public List<DetectedShape> detectShapes(Mat frame) {
    List<DetectedShape> detectedShapes = new ArrayList<>();
    
    // 1. BGR zu HSV konvertieren
    Mat hsv = new Mat();
    Imgproc.cvtColor(frame, hsv, Imgproc.COLOR_BGR2HSV);
    
    // 2. Für jede Farbe (Rot, Grün, Blau) Formen erkennen
    for (ColorType color : ColorType.values()) {
        detectColoredShapes(hsv, frame, color, detectedShapes);
    }
    
    return detectedShapes;
}
```

### 9.4 Farbige Formen erkennen

```java
private void detectColoredShapes(Mat hsv, Mat frame, ColorType colorType, ...) {
    Mat mask = new Mat();
    
    // 1. Farbmaske erstellen
    switch (colorType) {
        case RED -> {
            Mat mask1 = new Mat(), mask2 = new Mat();
            Core.inRange(hsv, RED_LOW1, RED_HIGH1, mask1);
            Core.inRange(hsv, RED_LOW2, RED_HIGH2, mask2);
            Core.add(mask1, mask2, mask);
        }
        case GREEN -> Core.inRange(hsv, GREEN_LOW, GREEN_HIGH, mask);
        case BLUE -> Core.inRange(hsv, BLUE_LOW, BLUE_HIGH, mask);
    }
    
    // 2. Morphologische Operationen (Rauschentfernung)
    Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(7, 7));
    Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel);
    Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel);
    
    // 3. Konturen finden
    List<MatOfPoint> contours = new ArrayList<>();
    Imgproc.findContours(mask, contours, hierarchy, 
        Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
    
    // 4. Konturen analysieren
    for (MatOfPoint contour : contours) {
        double area = Imgproc.contourArea(contour);
        if (area < MIN_CONTOUR_AREA) continue;
        
        // Polygonale Approximation
        MatOfPoint2f approx = new MatOfPoint2f();
        Imgproc.approxPolyDP(contour2f, approx, 0.04 * perimeter, true);
        
        // Form klassifizieren
        ShapeType shapeType = classifyShape(approx.total(), contour, area);
    }
}
```

### 9.5 Form-Klassifikation

```java
private ShapeType classifyShape(int vertices, MatOfPoint contour, double area) {
    if (vertices == 3) {
        return ShapeType.TRIANGLE;  // Dreieck
    } else if (vertices == 4) {
        // Rechteck-Überprüfung
        Rect boundingRect = Imgproc.boundingRect(contour);
        double aspectRatio = (double) boundingRect.width / boundingRect.height;
        double fillRatio = area / (boundingRect.width * boundingRect.height);
        
        if (fillRatio > 0.75 && aspectRatio >= 0.5 && aspectRatio <= 2.0) {
            return ShapeType.RECTANGLE;  // Viereck/Quadrat
        }
    } else if (vertices > 6) {
        // Kreiserkennung über Circularität
        double circularity = (4 * Math.PI * area) / (perimeter * perimeter);
        if (circularity > 0.75) {
            return ShapeType.CIRCLE;  // Kreis
        }
    }
    return ShapeType.UNKNOWN;
}
```

### 9.6 Form-zu-3D-Objekt Mapping

```java
private static final Map<String, String> SHAPE_TO_OBJECT = Map.of(
    "RED_TRIANGLE", "Fridge",              // Rot + Dreieck → Kühlschrank
    "GREEN_CIRCLE", "Sink",                // Grün + Kreis → Waschbecken
    "BLUE_RECTANGLE", "Microwave",         // Blau + Viereck → Mikrowelle
    "BLUE_TRIANGLE", "Oven",               // Blau + Dreieck → Backofen
    "GREEN_RECTANGLE", "Counter",          // Grün + Viereck → Theke
    "RED_CIRCLE", "Counter Inner Corner",  // Rot + Kreis → Innenecke
    "BLUE_CIRCLE", "Counter Outer Corner"  // Blau + Kreis → Außenecke
);

public String get3DObjectName() {
    return SHAPE_TO_OBJECT.get(colorType.name() + "_" + shapeType.name());
}
```

**Erkennungsregeln:**
| Farbe | Form | 3D-Küchenelement |
|-------|------|------------------|
| Rot | Dreieck | Kühlschrank (Fridge) |
| Grün | Kreis | Waschbecken (Sink) |
| Blau | Viereck | Mikrowelle (Microwave) |
| Blau | Dreieck | Backofen (Oven) |
| Grün | Viereck | Theke (Counter) |
| Rot | Kreis | Theken-Innenecke |
| Blau | Kreis | Theken-Außenecke |

### 9.7 Dialog bei Erkennung

Bei erkannter Form erscheint nach einem **3-Sekunden-Cooldown** ein Dialog:

```java
private void showAddShapeDialog(ShapeDetector.DetectedShape shape) {
    dialogOpen = true;
    SwingUtilities.invokeLater(() -> {
        String shapeName = shape.getColorName() + "es " + shape.getShapeName();
        String objectName = shape.get3DObjectName();

        int result = JOptionPane.showConfirmDialog(
            this,
            shapeName + " erkannt!\n\nMöchten Sie " + objectName + " zur Szene hinzufügen?",
            "Form erkannt",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );

        if (result == JOptionPane.YES_OPTION) {
            SceneData.Object3D obj = SceneData.createFromDetectedShape(shape, objects.size());
            if (obj != null) addObject(obj);
        }

        dialogOpen = false;
        lastShapeDetectionTime = System.currentTimeMillis();
    });
}
```

**Verhalten:**
- **Cooldown**: 3 Sekunden zwischen Erkennungen
- **Dialog-Sperre**: Während ein Dialog offen ist, werden keine weiteren Formen erkannt
- **Automatische Platzierung**: Erkannte Objekte werden bei (0, 0, 0) platziert
- **Nummerierung**: Automatische Durchnummerierung (z.B. "Fridge 1", "Fridge 2")

Die erkannten Formen werden automatisch mit den passenden OBJ-Modellen verknüpft und in der Szene platziert.

---

## 10. Theme-System

### 10.1 Zentrale Theme-Klasse

Die Klasse `Theme.java` definiert alle UI-Farben, Schriftarten und Dimensionen zentral:

```java
public final class Theme {
    // Hintergrundfarben
    public static final Color BACKGROUND = new Color(30, 30, 30);
    public static final Color BACKGROUND_DARK = new Color(25, 25, 30);
    public static final Color PANEL = new Color(35, 35, 40);
    public static final Color LIST_BACKGROUND = new Color(40, 40, 45);

    // Textfarben
    public static final Color TEXT_PRIMARY = Color.WHITE;
    public static final Color TEXT_SECONDARY = new Color(220, 220, 220);
    public static final Color TEXT_MUTED = new Color(180, 180, 180);
    public static final Color TEXT_LABEL = new Color(150, 150, 150);
    public static final Color TEXT_DISABLED = new Color(100, 100, 100);

    // Akzentfarben
    public static final Color SELECTION = new Color(70, 130, 180);
    public static final Color HOVER = new Color(60, 60, 70);
    public static final Color BORDER = new Color(60, 60, 65);
    
    // Statusfarben
    public static final Color SUCCESS = new Color(100, 180, 100);
    public static final Color ERROR = new Color(200, 80, 80);

    // Schriftarten
    public static final Font TITLE = new Font("SansSerif", Font.BOLD, 14);
    public static final Font LABEL = new Font("SansSerif", Font.BOLD, 12);
    public static final Font LABEL_SMALL = new Font("SansSerif", Font.PLAIN, 11);

    // Standard-Dimensionen
    public static final Dimension TOOLBAR_BUTTON = new Dimension(36, 36);
    public static final Dimension SIDE_PANEL = new Dimension(280, 0);
    public static final Dimension WEBCAM_PREVIEW = new Dimension(260, 195);
    public static final Dimension COLOR_BUTTON = new Dimension(80, 28);
}
```

### 10.2 Verwendung im Code

Die Theme-Konstanten werden durchgängig in der gesamten Anwendung verwendet:

```java
// Panel-Styling
panel.setBackground(Theme.BACKGROUND);
label.setForeground(Theme.TEXT_PRIMARY);

// Button-Dimensionen
button.setPreferredSize(Theme.TOOLBAR_BUTTON);

// Hover-Effekte
button.setBackground(Theme.HOVER);
```

### 10.3 Vorteile des Theme-Systems

- ✅ **Konsistenz**: Einheitliches Erscheinungsbild in der gesamten Anwendung
- ✅ **Wartbarkeit**: Farben und Größen zentral änderbar
- ✅ **Lesbarkeit**: Semantische Namen statt Hex-Codes
- ✅ **Skalierbarkeit**: Einfaches Hinzufügen neuer Theme-Varianten

---

## 11. Tastaturkürzel und Bedienung

### 11.1 Tastaturkürzel

| Taste | Funktion |
|-------|----------|
| **Ctrl+O** | OBJ-Datei importieren |
| **Delete** | Ausgewähltes Objekt löschen |
| **Doppelklick** | Objekt in der Liste bearbeiten |

### 11.2 Maussteuerung

| Aktion | Funktion |
|--------|----------|
| **Linksklick** | Objekt auswählen |
| **Linksklick + Ziehen** | Objekt auf dem Boden bewegen |
| **Rechtsklick + Ziehen** | Kamera rotieren (Orbit) |
| **Mausrad** | Zoom ein/aus (Kamera-Distanz) |

### 11.3 Kamera-Steuerung

- **Yaw** (Horizontal): -∞ bis +∞ Grad
- **Pitch** (Vertikal): -85° bis +85° (begrenzt, verhindert Überschlag)
- **Distance** (Zoom): 1 bis 50 Einheiten
- **FOV** (Sichtfeld): 60° (konfigurierbar)

### 11.4 Objekt-Bearbeitung

Der **Bearbeitungs-Dialog** bietet folgende Optionen:
- **Name**: Umbenennung des Objekts
- **Position**: X, Y, Z Koordinaten
- **Rotation**: X, Y, Z Achsen (in Radianten)
- **Skalierung**: X, Y, Z Faktoren
- **Farbe**: RGB-Farbauswahl via ColorChooser

---

## Zusammenfassung

**KitchenMaker** ist eine 3D-Küchenplanungsanwendung, die moderne Technologien für intuitive Bedienung kombiniert:

| Komponente | Technologie | Version |
|------------|-------------|---------|
| GUI-Framework | Swing + FlatLaf | 3.4 |
| 3D-Rendering | JOGL (OpenGL 2.0) | 2.6.0 |
| Shader | GLSL 1.20 (Blinn-Phong) | - |
| Mathematik | JOML | 1.10.5 |
| Webcam/CV | OpenCV | 4.9.0 |
| Icons | FlatSVGIcon (FlatLaf Extras) | 3.4 |
| Build-Tool | Maven | - |
| Java-Version | Java 17 | - |

### Hauptfunktionen:
- ✅ **3D-Visualisierung** mit OpenGL und modernem Shader-basiertem Rendering
- ✅ **Interaktive Kamera-Steuerung** (Orbit, Zoom, Pan)
- ✅ **Objekt-Manipulation** (Click-to-Select, Drag-to-Move)
- ✅ **Automatische Formenerkennung** via OpenCV und Webcam
- ✅ **Küchenelemente-Bibliothek** (7 verschiedene OBJ-Modelle)
- ✅ **Dark Theme UI** mit FlatLaf für moderne Optik
- ✅ **Echtzeit-Rendering** mit 60 FPS

### Verfügbare Küchenmöbel:
1. **Theke** (Counter) - `theke.obj`
2. **Theken-Innenecke** - `theke_ecke_innen.obj`
3. **Theken-Außenecke** - `theke_ecke_aussen.obj`
4. **Kühlschrank** (Fridge) - `kuehlschrank.obj`
5. **Backofen** (Oven) - `backofen.obj`
6. **Mikrowelle** (Microwave) - `mikrowelle.obj`
7. **Waschbecken** (Sink) - `waschbecken.obj`

Die Anwendung kombiniert klassische Desktop-UI-Entwicklung mit moderner 3D-Grafik und Computer Vision für eine intuitive Küchenplanung.

---

## Build und Ausführung

### Voraussetzungen
- **Java 17** oder höher
- **Maven 3.6+**
- **Webcam** (optional, für Formenerkennung)

### Projekt kompilieren

```bash
mvn clean compile
```

### Anwendung ausführen

**Windows:**
```batch
run_win.bat
```

**macOS/Linux:**
```bash
./run_mac.sh
```

**Oder direkt mit Maven:**
```bash
mvn exec:java
```

### JAR-Datei erstellen

```bash
mvn clean package
```

Die ausführbare JAR-Datei wird im `target/` Verzeichnis erstellt:
- `kitchenmaker-1.0.jar` - JAR mit allen Abhängigkeiten

**JAR ausführen:**
```bash
java -jar target/kitchenmaker-1.0.jar
```

### Abhängigkeiten validieren

```bash
mvn validate
```

Prüft die pom.xml und alle Dependencies auf Verfügbarkeit.

---

## Projektstruktur

```
KitchenMaker/
├── pom.xml                          # Maven-Konfiguration
├── run_win.bat                      # Windows-Startskript
├── run_mac.sh                       # macOS/Linux-Startskript
├── DOCUMENTATION.md                 # Diese Dokumentation
├── src/main/
│   ├── java/kitchenmaker/
│   │   ├── KitchenApp.java         # Hauptanwendung + GUI
│   │   ├── RenderEngine.java       # OpenGL Render-Engine
│   │   ├── SceneData.java          # 3D-Objekt-Management
│   │   ├── ShapeDetector.java      # OpenCV Formenerkennung
│   │   └── Theme.java              # UI-Theme-Definitionen
│   └── resources/
│       ├── *.obj                    # 3D-Modelle (7 Küchenmöbel)
│       └── icons/*.svg              # UI-Icons (13 SVGs)
└── target/                          # Build-Ausgabe (generiert)
```

### Wichtige Dateien

| Datei | Zweck |
|-------|-------|
| `KitchenApp.java` | Hauptklasse, UI-Setup, Event-Handling |
| `RenderEngine.java` | OpenGL-Rendering, Shader, Kamera |
| `SceneData.java` | OBJ-Loader, Grid-Generator, Objekt-Definitions |
| `ShapeDetector.java` | OpenCV-Formenerkennung (Farbe + Shape) |
| `Theme.java` | Zentrale Theme-Konstanten |
| `pom.xml` | Maven-Abhängigkeiten und Build-Config |

---

**Entwickelt mit ❤️ für intuitive Küchenplanung**  
**Technologie-Stack:** Java 17 • JOGL 2.6.0 • OpenCV 4.9.0 • FlatLaf 3.4 • JOML 1.10.5

