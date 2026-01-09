package kitchenmaker;

import com.formdev.flatlaf.FlatDarkLaf;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLJPanel;
import com.jogamp.opengl.util.FPSAnimator;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hauptklasse der KitchenMaker-Anwendung.
 * Diese Klasse fungiert als zentraler Controller, der die Initialisierung der Anwendung,
 * die Verwaltung des OpenGL-Kontexts sowie die Koordination zwischen Datenmodell (SceneData),
 * Rendering (RenderEngine) und Benutzeroberfläche (GUI) übernimmt.
 * Zudem werden hier die Eingabeverarbeitung (Mausinteraktion, Raycasting) und
 * die asynchrone Bildverarbeitung (Webcam-Integration) gesteuert.
 *
 * @author Niklas Puls
 */
public class KitchenApp {

    /** Schwellenwert in Pixeln, ab dem eine Mausbewegung als Drag-Operation erkannt wird. */
    private static final int DRAG_THRESHOLD = 5;

    /** Zeitintervall in Millisekunden, um wiederholte Formerkennungen zu begrenzen. */
    private static final long SHAPE_DETECTION_COOLDOWN = 3000;

    /** Flag, das anzeigt, ob die OpenCV-Bibliothek erfolgreich geladen wurde. */
    private static boolean opencvAvailable = false;

    /**
     * Thread-sichere Liste aller 3D-Objekte in der Szene.
     * Synchronisation ist erforderlich, da der Render-Thread (OpenGL) und der Event-Dispatch-Thread (Swing)
     * gleichzeitig auf diese Liste zugreifen können.
     */
    private final List<SceneData.Object3D> objects = Collections.synchronizedList(new ArrayList<>());

    private final RenderEngine renderer;
    private final GLJPanel gljPanel;

    private final DefaultListModel<SceneData.Object3D> listModel;
    private final JList<SceneData.Object3D> objectList;

    private final Vec3 dragOffsetVector = new Vec3();

    private final JLabel webcamLabel;

    private GUI gui;
    private ShapeDetector shapeDetector;
    private boolean shapeDetection = true;
    private long lastShapeDetectionTime = 0;

    private volatile boolean dialogOpen = false;
    private boolean webcamRunning = false;

    // Statusvariablen für die Mausinteraktion
    private boolean isDraggingObject = false;
    private boolean isDragging = false;
    private int lastMouseX, lastMouseY, pressedMouseX, pressedMouseY;

    /** Y-Koordinate der Ebene, auf der das aktuelle Objekt verschoben wird. */
    private float dragPlaneY = 0;

    /**
     * Einstiegspunkt der Anwendung.
     * Initialisiert das Look-and-Feel, lädt native Bibliotheken (OpenCV) und startet die GUI
     * im Event-Dispatch-Thread (EDT). Die übergebenen Kommandozeilenargumente werden ignoriert.
     */
    public static void main(String[] args) {
        FlatDarkLaf.setup();
        // UI-Anpassungen für konsistentes Design
        UIManager.put("Button.arc", 8);
        UIManager.put("Component.arc", 8);
        UIManager.put("TextComponent.arc", 8);

        try {
            nu.pattern.OpenCV.loadLocally();
            opencvAvailable = true;
        } catch (Throwable t) {
            opencvAvailable = false;
            System.err.println("Warnung: OpenCV konnte nicht geladen werden. Bildverarbeitungsfunktionen sind deaktiviert.");
        }

        SwingUtilities.invokeLater(KitchenApp::new);
    }

    /**
     * Konstruktor der Anwendung.
     * Initialisiert die OpenGL-Umgebung, den Renderer und die grafische Benutzeroberfläche.
     */
    public KitchenApp() {
        if (opencvAvailable) {
            shapeDetector = new ShapeDetector();
        }

        renderer = new RenderEngine(objects);

        // Konfiguration des OpenGL-Profils
        GLProfile glProfile = GLProfile.getDefault();
        GLCapabilities glCapabilities = new GLCapabilities(glProfile);
        glCapabilities.setDoubleBuffered(true); // Double Buffering zur Vermeidung von Flimmern
        glCapabilities.setHardwareAccelerated(true);

        gljPanel = new GLJPanel(glCapabilities);
        gljPanel.addGLEventListener(renderer);

        listModel = new DefaultListModel<>();
        objectList = new JList<>(listModel);

        // Initialisierung der Webcam-Vorschau-Komponente
        webcamLabel = new JLabel("Aus", SwingConstants.CENTER);
        webcamLabel.setPreferredSize(Theme.WEBCAM_PREVIEW);
        webcamLabel.setBackground(Theme.BACKGROUND_DARK);
        webcamLabel.setForeground(Theme.TEXT_DISABLED);
        webcamLabel.setOpaque(true);
        webcamLabel.setBorder(BorderFactory.createLineBorder(Theme.BORDER));

        // Erzeugung der Haupt-GUI
        gui = new GUI(this, objects, renderer, gljPanel, listModel, objectList, webcamLabel);

        setupInteraction();

        gui.setVisible(true);

        // Start des Render-Loops mit angestrebten 60 FPS
        new FPSAnimator(gljPanel, 60).start();
    }

    /**
     * Erzeugt eine neue Objektinstanz basierend auf dem übergebenen Typbezeichner (z.B. "cube").
     */
    public void addObjectByType(String type) {
        SceneData.Object3D obj = SceneData.createByType(type);
        if (obj != null) {
            // Generierung eines eindeutigen Namens für die Anzeige
            obj.name = obj.name + " " + (objects.size() + 1);
            addObject(obj);
        }
    }

    /**
     * Fügt ein 3D-Objekt zur Szene hinzu und aktualisiert die UI-Komponenten.
     */
    void addObject(SceneData.Object3D obj) {
        // Startet die Initial-Animation (z.B. Skalierung beim Erscheinen)
        obj.startAnimation(gui.getCurrentAppearanceMode(), gui.getAnimationDurationSeconds());

        objects.add(obj);
        listModel.addElement(obj);
        objectList.setSelectedValue(obj, true);
        renderer.selectedObject = obj;
    }

    /**
     * Entfernt das aktuell ausgewählte Objekt aus der Szene und der Liste.
     * Die Selektion wird anschließend auf das vorherige Element verschoben.
     */
    public void deleteSelectedObject() {
        if (renderer.selectedObject != null) {
            int id = objects.indexOf(renderer.selectedObject);
            objects.remove(renderer.selectedObject);
            listModel.removeElement(renderer.selectedObject);

            // Intelligente Neuselektion
            renderer.selectedObject = objects.isEmpty() ? null : objects.get(Math.max(0, id - 1));
            if (renderer.selectedObject != null) {
                objectList.setSelectedValue(renderer.selectedObject, true);
            }
        }
    }

    /**
     * Öffnet einen Dateidialog zum Import von Wavefront OBJ-Dateien.
     */
    public void importObjFile() {
        JFileChooser fc = new JFileChooser();
        fc.setCurrentDirectory(new java.io.File("."));
        if (fc.showOpenDialog(gui) == JFileChooser.APPROVE_OPTION) {
            SceneData.Object3D obj = SceneData.loadObj(fc.getSelectedFile());
            if (obj != null) {
                addObject(obj);
            }
        }
    }

    public boolean isShapeDetection() {
        return shapeDetection;
    }

    public void setShapeDetection(boolean enabled) {
        this.shapeDetection = enabled;
    }

    /**
     * Registriert Event-Listener für Tastatur- und Mausinteraktionen.
     * Behandelt Shortcuts, Objektauswahl (Picking) und Kamerasteuerung.
     */
    private void setupInteraction() {
        // Globaler KeyEventDispatcher für anwendungsweite Shortcuts
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) { // Entf oder Backspace für Löschen
                    deleteSelectedObject();
                    return true;
                } else if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_O) { // Strg + O für Import
                    importObjFile();
                    return true;
                }
            }
            return false;
        });

        gljPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                pressedMouseX = lastMouseX = e.getX();
                pressedMouseY = lastMouseY = e.getY();
                isDragging = false;
                isDraggingObject = false;

                if (SwingUtilities.isLeftMouseButton(e)) {
                    // Durchführung des Raycastings zur Objektselektion
                    SceneData.Object3D clicked = pickObject(e.getX(), e.getY());
                    if (clicked != null) {
                        renderer.selectedObject = clicked;
                        objectList.setSelectedValue(clicked, true);

                        // Berechnung des Offsets für präzises Verschieben
                        dragPlaneY = clicked.worldPosition.y;
                        Vec3 hitPoint = screenToGroundPlane(e.getX(), e.getY(), dragPlaneY);
                        dragOffsetVector.set(hitPoint).subtract(clicked.worldPosition);
                    } else {
                        renderer.selectedObject = null;
                        objectList.clearSelection();
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                isDragging = false;
                isDraggingObject = false;
            }
        });

        gljPanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                int dx = e.getX() - lastMouseX;
                int dy = e.getY() - lastMouseY;

                // Unterscheidung zwischen Klick und Drag anhand des Schwellenwerts
                if (Math.abs(e.getX() - pressedMouseX) > DRAG_THRESHOLD ||
                        Math.abs(e.getY() - pressedMouseY) > DRAG_THRESHOLD) {
                    isDragging = true;
                    if (renderer.selectedObject != null) {
                        isDraggingObject = true;
                    }
                }

                if (isDragging) {
                    if (isDraggingObject && renderer.selectedObject != null) {
                        moveObjectOnGround(renderer.selectedObject, e.getX(), e.getY());
                    } else {
                        // Kamerarotation (Orbit-Control)
                        renderer.cameraYaw -= dx * 0.5f;
                        // Begrenzung des Pitch-Winkels zur Vermeidung von Gimbal-Lock-ähnlichen Effekten
                        renderer.cameraPitch = Math.max(-85f, Math.min(85f, renderer.cameraPitch + dy * 0.5f));
                    }
                    gljPanel.repaint();
                }

                lastMouseX = e.getX();
                lastMouseY = e.getY();
            }
        });

        // Zoom-Funktionalität via Mausrad
        gljPanel.addMouseWheelListener(e ->
                renderer.cameraDistance = Math.max(1f, Math.min(50f,
                        renderer.cameraDistance + (float)e.getPreciseWheelRotation() * 0.5f))
        );
    }

    /** Hilfs-Record zur Repräsentation eines Strahls im 3D-Raum. */
    private record Ray(Vec3 rayOriginPoint, Vec3 rayDirectionVector) {}

    /**
     * Transformiert 2D-Mauskoordinaten in einen 3D-Strahl (Raycasting).
     * <p>
     * Die Methode führt eine Rückprojektion (Unprojection) von Screen-Koordinaten über
     * Normalized Device Coordinates (NDC) in den Weltraum durch, unter Verwendung
     * der aktuellen View- und Projection-Matrizen.
     * </p>
     */
    private Ray createRayFromMouse(int mouseX, int mouseY) {
        int w = Math.max(1, gljPanel.getWidth());
        int h = Math.max(1, gljPanel.getHeight());
        float aspect = (float) w / h;

        // Transformation in Normalized Device Coordinates (NDC) [-1, 1]
        float normalizedDeviceX = (2.0f * mouseX) / w - 1.0f;
        float normalizedDeviceY = 1.0f - (2.0f * mouseY) / h;

        // Berechnung der Kameraposition aus sphärischen Koordinaten
        float pitchInRadians = (float) Math.toRadians(renderer.cameraPitch);
        float yawInRadians = (float) Math.toRadians(renderer.cameraYaw);
        float cameraX = renderer.cameraDistance * (float)(Math.cos(pitchInRadians) * Math.sin(yawInRadians));
        float cameraY = renderer.cameraDistance * (float) Math.sin(pitchInRadians);
        float cameraZ = renderer.cameraDistance * (float)(Math.cos(pitchInRadians) * Math.cos(yawInRadians));
        Vec3 cameraPosition = new Vec3(cameraX, cameraY, cameraZ).add(renderer.cameraTarget);

        // Aufbau der Projektions- und View-Matrizen
        Mat4 projectionMatrix = new Mat4().setPerspective((float) Math.toRadians(renderer.fov), aspect, 0.1f, 100f);
        Mat4 viewMatrix = new Mat4().setLookAt(cameraPosition, renderer.cameraTarget, new Vec3(0, 1, 0));

        // Berechnung der Inversen View-Projection-Matrix für die Rücktransformation
        Mat4 inverseViewProjectionMatrix = new Mat4(projectionMatrix).multiplyMatrix(viewMatrix).invertMatrix();

        // Transformation der Near- und Far-Plane-Punkte von NDC in Weltkoordinaten
        Vec4 rayNearPoint = new Vec4(normalizedDeviceX, normalizedDeviceY, -1f, 1f).multiply(inverseViewProjectionMatrix);
        rayNearPoint.divideByW(); // Perspektivische Division
        Vec4 rayFarPoint = new Vec4(normalizedDeviceX, normalizedDeviceY, 1f, 1f).multiply(inverseViewProjectionMatrix);
        rayFarPoint.divideByW();

        Vec3 rayOriginPoint = new Vec3(rayNearPoint.x, rayNearPoint.y, rayNearPoint.z);
        Vec3 rayDirectionVector = new Vec3(rayFarPoint.x - rayNearPoint.x, rayFarPoint.y - rayNearPoint.y, rayFarPoint.z - rayNearPoint.z).normalize();

        return new Ray(rayOriginPoint, rayDirectionVector);
    }

    /**
     * Ermittelt das dem Betrachter am nächsten liegende Objekt, das vom Mausstrahl getroffen wird.
     * Gibt das getroffene Objekt zurück oder null, falls kein Schnittpunkt existiert.
     */
    private SceneData.Object3D pickObject(int mouseX, int mouseY) {
        Ray ray = createRayFromMouse(mouseX, mouseY);

        SceneData.Object3D closest = null;
        float closestDist = Float.MAX_VALUE;

        // Synchronisierter Zugriff auf die Objektliste zur Vermeidung von Race Conditions
        synchronized (objects) {
            for (SceneData.Object3D obj : objects) {
                float t = intersectAABB(ray.rayOriginPoint, ray.rayDirectionVector, obj);

                // Suche nach dem kleinsten positiven Schnittparameter t
                if (t > 0 && t < closestDist) {
                    closestDist = t;
                    closest = obj;
                }
            }
        }

        return closest;
    }

    /**
     * Berechnet den Schnittpunkt eines Strahls mit der Axis-Aligned Bounding Box (AABB) eines Objekts.
     * Implementiert den "Slab"-Algorithmus.
     * Gibt den Abstand t zum Eintrittspunkt zurück oder -1, falls kein Schnittpunkt existiert.
     */
    private float intersectAABB(Vec3 rayOriginPoint, Vec3 rayDirectionVector, SceneData.Object3D obj) {
        // Transformation der lokalen AABB in Weltkoordinaten
        Vec3 aabbMinWorld = new Vec3(obj.boundingBoxMin).multiply(obj.scaleFactors).add(obj.worldPosition);
        Vec3 aabbMaxWorld = new Vec3(obj.boundingBoxMax).multiply(obj.scaleFactors).add(obj.worldPosition);

        // Hinzufügen einer Toleranz (Padding) zur Verbesserung der Klickbarkeit
        float padding = 0.2f;
        aabbMinWorld.subtract(padding, padding, padding);
        aabbMaxWorld.add(padding, padding, padding);

        // Berechnung der Schnittintervalle für die X-Achse
        float tMinX = (aabbMinWorld.x - rayOriginPoint.x) / rayDirectionVector.x;
        float tMaxX = (aabbMaxWorld.x - rayOriginPoint.x) / rayDirectionVector.x;
        if (tMinX > tMaxX) { float tmp = tMinX; tMinX = tMaxX; tMaxX = tmp; }

        // Berechnung der Schnittintervalle für die Y-Achse
        float tMinY = (aabbMinWorld.y - rayOriginPoint.y) / rayDirectionVector.y;
        float tMaxY = (aabbMaxWorld.y - rayOriginPoint.y) / rayDirectionVector.y;
        if (tMinY > tMaxY) { float tmp = tMinY; tMinY = tMaxY; tMaxY = tmp; }

        // Prüfung auf Disjunktion der Intervalle
        if (tMinX > tMaxY || tMinY > tMaxX) return -1;

        // Intervall-Schnittbildung (Clipping)
        tMinX = Math.max(tMinX, tMinY);
        tMaxX = Math.min(tMaxX, tMaxY);

        // Berechnung der Schnittintervalle für die Z-Achse
        float tMinZ = (aabbMinWorld.z - rayOriginPoint.z) / rayDirectionVector.z;
        float tMaxZ = (aabbMaxWorld.z - rayOriginPoint.z) / rayDirectionVector.z;
        if (tMinZ > tMaxZ) { float tmp = tMinZ; tMinZ = tMaxZ; tMaxZ = tmp; }

        if (tMinX > tMaxZ || tMinZ > tMaxX) return -1;

        // Finaler Eintrittspunkt
        tMinX = Math.max(tMinX, tMinZ);

        return tMinX;
    }

    /**
     * Berechnet den Schnittpunkt des Mausstrahls mit einer horizontalen Ebene (y = const).
     * Gibt den Schnittpunkt im 3D-Raum zurück.
     */
    private Vec3 screenToGroundPlane(int mouseX, int mouseY, float planeY) {
        Ray ray = createRayFromMouse(mouseX, mouseY);
        // Ebenengleichung: P_y = planeY => origin.y + t * dir.y = planeY
        float t = (planeY - ray.rayOriginPoint.y) / ray.rayDirectionVector.y;
        return new Vec3(ray.rayOriginPoint).add(new Vec3(ray.rayDirectionVector).multiply(t));
    }

    /**
     * Verschiebt ein Objekt auf der definierten Ebene basierend auf der Mausposition.
     * Berücksichtigt den anfänglichen Klick-Offset, um Sprünge zu vermeiden.
     */
    private void moveObjectOnGround(SceneData.Object3D obj, int mouseX, int mouseY) {
        Vec3 hitPoint = screenToGroundPlane(mouseX, mouseY, dragPlaneY);
        obj.worldPosition.x = hitPoint.x - dragOffsetVector.x;
        obj.worldPosition.z = hitPoint.z - dragOffsetVector.z;
    }

    /**
     * Aktiviert oder deaktiviert die Webcam-Erfassung.
     * Startet bei Aktivierung einen separaten Thread für die Bildverarbeitung.
     */
    public void toggleWebcam() {
        if (webcamRunning) {
            webcamRunning = false;
            webcamLabel.setIcon(null);
            webcamLabel.setText("Aus");
            webcamLabel.setForeground(Theme.TEXT_DISABLED);
        } else {
            webcamRunning = true;
            webcamLabel.setText("Starte...");
            webcamLabel.setForeground(Theme.SUCCESS);
            new Thread(this::webcamLoop).start();
        }
    }

    /**
     * Hauptschleife des Webcam-Threads.
     * Liest Frames von der Kamera und führt die Formerkennung durch.
     */
    private void webcamLoop() {
        VideoCapture capture = new VideoCapture(0);

        if (!capture.isOpened()) {
            SwingUtilities.invokeLater(() -> {
                webcamLabel.setText("Keine Webcam");
                webcamLabel.setForeground(Theme.ERROR);
            });
            webcamRunning = false;
            return;
        }

        Mat frame = new Mat();
        while (webcamRunning) {
            if (capture.read(frame) && !frame.empty()) {
                // Optionale Formerkennung
                if (shapeDetection && shapeDetector != null) {
                    java.util.List<ShapeDetector.DetectedShape> detectedShapes = shapeDetector.detectShapes(frame);

                    long currentTime = System.currentTimeMillis();
                    // Überprüfung des Cooldowns und ob bereits ein Dialog offen ist
                    if (!dialogOpen && currentTime - lastShapeDetectionTime > SHAPE_DETECTION_COOLDOWN) {
                        for (ShapeDetector.DetectedShape shape : detectedShapes) {
                            if (ShapeDetector.isValidMapping(shape)) {
                                lastShapeDetectionTime = currentTime;
                                showAddShapeDialog(shape);
                                break;
                            }
                        }
                    }
                }

                // Konvertierung für Swing-Anzeige
                BufferedImage img = matToImage(frame);
                Image scaled = img.getScaledInstance(260, 195, Image.SCALE_FAST);
                SwingUtilities.invokeLater(() -> {
                    webcamLabel.setText(null);
                    webcamLabel.setIcon(new ImageIcon(scaled));
                });
            }
            try { Thread.sleep(50); } catch (InterruptedException e) { break; }
        }
        capture.release();
    }

    /**
     * Zeigt einen Bestätigungsdialog für ein erkanntes Objekt an.
     * Wird im Event-Dispatch-Thread ausgeführt.
     */
    private void showAddShapeDialog(ShapeDetector.DetectedShape shape) {
        dialogOpen = true;
        SwingUtilities.invokeLater(() -> {
            String shapeName = shape.getColorName() + "es " + shape.getShapeName();
            String objectName = shape.get3DObjectName();

            int result = JOptionPane.showConfirmDialog(
                    gui,
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

    /** OpenCV Mat zu BufferedImage umwandeln, sodass es im GUI genutzt werden kann */
    private BufferedImage matToImage(Mat m) {
        int type = (m.channels() > 1) ? BufferedImage.TYPE_3BYTE_BGR : BufferedImage.TYPE_BYTE_GRAY;
        byte[] b = new byte[m.channels() * m.cols() * m.rows()];
        m.get(0, 0, b);
        BufferedImage image = new BufferedImage(m.cols(), m.rows(), type);
        System.arraycopy(b, 0, ((DataBufferByte) image.getRaster().getDataBuffer()).getData(), 0, b.length);
        return image;
    }
}