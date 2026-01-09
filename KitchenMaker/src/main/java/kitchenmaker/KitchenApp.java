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
 * Hauptanwendung für den KitchenMaker-Editor.
 * Verwaltet die Kernlogik der Anwendung: 3D-Objektinteraktion, Raycasting,
 * Webcam-Integration und Formerkennung. Die GUI wird von {@link GUI} verwaltet.
 *
 * @author Niklas Puls
 */
public class KitchenApp {

    private static final int DRAG_THRESHOLD = 5;
    private static final long SHAPE_DETECTION_COOLDOWN = 3000;
    private static boolean opencvAvailable = false;

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

    private boolean isDraggingObject = false;
    private boolean isDragging = false;
    private int lastMouseX, lastMouseY, pressedMouseX, pressedMouseY;
    private float dragPlaneY = 0;

    /**
     * Anwendungseintrittspunkt. Lädt OpenCV falls verfügbar und startet die UI im EDT.
     */
    public static void main(String[] args) {
        FlatDarkLaf.setup();
        UIManager.put("Button.arc", 8);
        UIManager.put("Component.arc", 8);
        UIManager.put("TextComponent.arc", 8);

        try {
            nu.pattern.OpenCV.loadLocally();
            opencvAvailable = true;
        } catch (Throwable t) {
            opencvAvailable = false;
        }

        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            new KitchenApp();
        } else {
            SwingUtilities.invokeLater(KitchenApp::new);
        }
    }

    /**
     * Konstruktor: initialisiert Renderer, OpenGL und GUI.
     */
    public KitchenApp() {
        if (opencvAvailable) {
            shapeDetector = new ShapeDetector();
        }

        renderer = new RenderEngine(objects);

        GLProfile glProfile = GLProfile.getDefault();
        GLCapabilities glCapabilities = new GLCapabilities(glProfile);
        glCapabilities.setDoubleBuffered(true);
        glCapabilities.setHardwareAccelerated(true);

        gljPanel = new GLJPanel(glCapabilities);
        gljPanel.addGLEventListener(renderer);

        listModel = new DefaultListModel<>();
        objectList = new JList<>(listModel);

        webcamLabel = new JLabel("Aus", SwingConstants.CENTER);
        webcamLabel.setPreferredSize(Theme.WEBCAM_PREVIEW);
        webcamLabel.setBackground(Theme.BACKGROUND_DARK);
        webcamLabel.setForeground(Theme.TEXT_DISABLED);
        webcamLabel.setOpaque(true);
        webcamLabel.setBorder(BorderFactory.createLineBorder(Theme.BORDER));

        // Erstelle die GUI
        gui = new GUI(this, objects, renderer, gljPanel, listModel, objectList, webcamLabel);

        setupInteraction();

        gui.setVisible(true);

        new FPSAnimator(gljPanel, 60).start();
    }

    /**
     * Fügt ein Objekt nach Typ hinzu.
     */
    public void addObjectByType(String type) {
        SceneData.Object3D obj = SceneData.createByType(type);
        if (obj != null) {
            obj.name = obj.name + " " + (objects.size() + 1);
            addObject(obj);
        }
    }

    /**
     * Fügt ein Objekt zur Szene hinzu.
     */
    void addObject(SceneData.Object3D obj) {
        obj.startAnimation(gui.getCurrentAppearanceMode(), gui.getAnimationDurationSeconds());

        objects.add(obj);
        listModel.addElement(obj);
        objectList.setSelectedValue(obj, true);
        renderer.selectedObject = obj;
    }

    /**
     * Löscht das ausgewählte Objekt.
     */
    public void deleteSelectedObject() {
        if (renderer.selectedObject != null) {
            int id = objects.indexOf(renderer.selectedObject);
            objects.remove(renderer.selectedObject);
            listModel.removeElement(renderer.selectedObject);
            renderer.selectedObject = objects.isEmpty() ? null : objects.get(Math.max(0, id - 1));
            if (renderer.selectedObject != null) {
                objectList.setSelectedValue(renderer.selectedObject, true);
            }
        }
    }

    /**
     * Öffnet einen Datei-Dialog zum Importieren einer OBJ-Datei.
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

    private void setupInteraction() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                    deleteSelectedObject();
                    return true;
                } else if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_O) {
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
                    SceneData.Object3D clicked = pickObject(e.getX(), e.getY());
                    if (clicked != null) {
                        renderer.selectedObject = clicked;
                        objectList.setSelectedValue(clicked, true);

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
                        renderer.cameraYaw -= dx * 0.5f;
                        renderer.cameraPitch = Math.max(-85f, Math.min(85f, renderer.cameraPitch + dy * 0.5f));
                    }
                    gljPanel.repaint();
                }

                lastMouseX = e.getX();
                lastMouseY = e.getY();
            }
        });

        gljPanel.addMouseWheelListener(e ->
            renderer.cameraDistance = Math.max(1f, Math.min(50f,
                renderer.cameraDistance + (float)e.getPreciseWheelRotation() * 0.5f))
        );
    }

    private record Ray(Vec3 rayOriginPoint, Vec3 rayDirectionVector) {}

    private Ray createRayFromMouse(int mouseX, int mouseY) {
        // Robuste Abfrage der Panel-Größe (vermeidet Division durch Null)
        int w = Math.max(1, gljPanel.getWidth());
        int h = Math.max(1, gljPanel.getHeight());
        float aspect = (float) w / h;

        // Normalisierte Gerätekoordinaten (NDC) im Bereich [-1,1]
        float normalizedDeviceX = (2.0f * mouseX) / w - 1.0f;
        float normalizedDeviceY = 1.0f - (2.0f * mouseY) / h;

        // Kamera-Position (berechnet aus spherical coordinates - yaw/pitch/distance)
        float pitchInRadians = (float) Math.toRadians(renderer.cameraPitch);
        float yawInRadians = (float) Math.toRadians(renderer.cameraYaw);
        float cameraX = renderer.cameraDistance * (float)(Math.cos(pitchInRadians) * Math.sin(yawInRadians));
        float cameraY = renderer.cameraDistance * (float) Math.sin(pitchInRadians);
        float cameraZ = renderer.cameraDistance * (float)(Math.cos(pitchInRadians) * Math.cos(yawInRadians));
        Vec3 cameraPosition = new Vec3(cameraX, cameraY, cameraZ).add(renderer.cameraTarget);

        // Erstelle Projektions- und View-Matrizen wie im Renderer
        Mat4 projectionMatrix = new Mat4().setPerspective((float) Math.toRadians(renderer.fov), aspect, 0.1f, 100f);
        Mat4 viewMatrix = new Mat4().setLookAt(cameraPosition, renderer.cameraTarget, new Vec3(0, 1, 0));

        // Inverse der View-Projection-Matrix: wir wollen von NDC zurück in Weltkoordinaten
        // Achtung: Mat4.invertMatrix setzt bei nicht-invertierbarer Matrix die Identität;
        // das ist eine Fallback-Strategie, die hier beibehalten wird.
        Mat4 inverseViewProjectionMatrix = new Mat4(projectionMatrix).multiplyMatrix(viewMatrix).invertMatrix();

        // Erzeuge Punkte in Clip-Space für nahe und ferne Ebene und transformiere sie in Weltkoordinaten
        Vec4 rayNearPoint = new Vec4(normalizedDeviceX, normalizedDeviceY, -1f, 1f).multiply(inverseViewProjectionMatrix);
        rayNearPoint.divideByW();
        Vec4 rayFarPoint = new Vec4(normalizedDeviceX, normalizedDeviceY, 1f, 1f).multiply(inverseViewProjectionMatrix);
        rayFarPoint.divideByW();

        // Origin ist der Punkt bei z=-1 (in Weltkoordinaten). Die Richtung ist (far - near).
        Vec3 rayOriginPoint = new Vec3(rayNearPoint.x, rayNearPoint.y, rayNearPoint.z);
        Vec3 rayDirectionVector = new Vec3(rayFarPoint.x - rayNearPoint.x, rayFarPoint.y - rayNearPoint.y, rayFarPoint.z - rayNearPoint.z).normalize();

        return new Ray(rayOriginPoint, rayDirectionVector);
    }

    private SceneData.Object3D pickObject(int mouseX, int mouseY) {
        // Erzeuge einen Ray aus der Mausposition
        Ray ray = createRayFromMouse(mouseX, mouseY);

        SceneData.Object3D closest = null;
        float closestDist = Float.MAX_VALUE;

        // Synchronisiere Zugriff auf die Objektliste (gleiche Semantik wie zuvor)
        synchronized (objects) {
            for (SceneData.Object3D obj : objects) {
                // intersectAABB liefert den Parameter t des Schnittpunkts (Eintrittspunkt)
                float t = intersectAABB(ray.rayOriginPoint, ray.rayDirectionVector, obj);

                // Wir interessieren uns für Treffers mit positivem t (vor dem Ray-Start) und suchen den nächsten
                if (t > 0 && t < closestDist) {
                    closestDist = t;
                    closest = obj;
                }
            }
        }

        return closest;
    }

    /*
     * Grundidee des AABB-Ray-Tests (Slab method):
     * Für jede Achse (X, Y, Z) berechnen wir das Intervall [tMin, tMax], in dem
     * der Ray die Ebene zwischen den min/max Koordinaten der AABB schneidet.
     *
     * Ray-Punkt: P(t) = origin + t * direction
     * Schnitt mit Ebene x = a => t = (a - origin.x) / direction.x
     *
     * Für jede Achse erhalten wir zwei t-Werte; wir sorgen durch Tausch dafür,
     * dass tMin <= tMax gilt (falls die Richtung negativ ist, vertauschen sich die Werte).
     * Die Gesamtschnittmenge ist der Schnitt der Achsen-Intervalle. Falls die
     * Intervalle keine gemeinsame Überlappung haben, existiert kein Schnittpunkt.
     */

    private float intersectAABB(Vec3 rayOriginPoint, Vec3 rayDirectionVector, SceneData.Object3D obj) {
        // Transformiere die lokale Axis-Aligned Bounding Box (AABB) in Weltkoordinaten.
        // Lokale Bounds werden zuerst skaliert und dann um die Weltposition verschoben.
        Vec3 aabbMinWorld = new Vec3(obj.boundingBoxMin).multiply(obj.scaleFactors).add(obj.worldPosition);
        Vec3 aabbMaxWorld = new Vec3(obj.boundingBoxMax).multiply(obj.scaleFactors).add(obj.worldPosition);

        // Optionaler Puffer, um kleine Lücken/Toleranzen im Modell auszugleichen.
        float padding = 0.2f;
        aabbMinWorld.subtract(padding, padding, padding);
        aabbMaxWorld.add(padding, padding, padding);

        float tMinX = (aabbMinWorld.x - rayOriginPoint.x) / rayDirectionVector.x;
        float tMaxX = (aabbMaxWorld.x - rayOriginPoint.x) / rayDirectionVector.x;
        if (tMinX > tMaxX) { float tmp = tMinX; tMinX = tMaxX; tMaxX = tmp; }

        float tMinY = (aabbMinWorld.y - rayOriginPoint.y) / rayDirectionVector.y;
        float tMaxY = (aabbMaxWorld.y - rayOriginPoint.y) / rayDirectionVector.y;
        if (tMinY > tMaxY) { float tmp = tMinY; tMinY = tMaxY; tMaxY = tmp; }

        // Schnellabbruch: Wenn X- und Y-Intervalle sich nicht überlappen, gibt es keinen Schnitt.
        if (tMinX > tMaxY || tMinY > tMaxX) return -1;

        /*
         * Kombiniere die X- und Y-Intervalle zu einem temporären Intervall.
         * Das kombinierte Minimum T tMinX ist das größere der beiden Startwerte (weil beide erfüllt sein müssen),
         * das kombinierte Maximum T tMaxX ist das kleinere der beiden Endwerte (beide dürfen nicht überschritten werden).
         *
         * Wir verwenden Math.max/Math.min hier, weil es die Absicht klar ausdrückt und
         * für statische Analysen besser lesbar ist als direkte Zuweisungen.
         */
        tMinX = Math.max(tMinX, tMinY); // kombiniertes Intervall-Start
        tMaxX = Math.min(tMaxX, tMaxY); // kombiniertes Intervall-Ende

        // Z-Achse: gleiche Berechnung wie zuvor
        float tMinZ = (aabbMinWorld.z - rayOriginPoint.z) / rayDirectionVector.z;
        float tMaxZ = (aabbMaxWorld.z - rayOriginPoint.z) / rayDirectionVector.z;
        if (tMinZ > tMaxZ) { float tmp = tMinZ; tMinZ = tMaxZ; tMaxZ = tmp; }

        // Überprüfe, ob das kombinierte XY-Intervall mit dem Z-Intervall überlappt.
        if (tMinX > tMaxZ || tMinZ > tMaxX) return -1;

        // Endgültiges kombiniertes Start-t ist das Maximum der bisherigen Starts (einschließlich Z)
        tMinX = Math.max(tMinX, tMinZ);

        // Ergebnis: tMinX ist der Eintrittsparameter; negativ bedeutet, der Eintrittspunkt liegt
        // hinter dem Ray-Start (falls benötigt, kann der Aufrufer dies filtern).
        return tMinX;
    }

    private Vec3 screenToGroundPlane(int mouseX, int mouseY, float planeY) {
        Ray ray = createRayFromMouse(mouseX, mouseY);
        float t = (planeY - ray.rayOriginPoint.y) / ray.rayDirectionVector.y;
        return new Vec3(ray.rayOriginPoint).add(new Vec3(ray.rayDirectionVector).multiply(t));
    }

    private void moveObjectOnGround(SceneData.Object3D obj, int mouseX, int mouseY) {
        Vec3 hitPoint = screenToGroundPlane(mouseX, mouseY, dragPlaneY);
        obj.worldPosition.x = hitPoint.x - dragOffsetVector.x;
        obj.worldPosition.z = hitPoint.z - dragOffsetVector.z;
    }

    /**
     * Schaltet die Webcam ein oder aus.
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
                java.util.List<ShapeDetector.DetectedShape> detectedShapes;
                if (shapeDetection && shapeDetector != null) {
                    detectedShapes = shapeDetector.detectShapes(frame);

                    long currentTime = System.currentTimeMillis();
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

    private BufferedImage matToImage(Mat m) {
        int type = (m.channels() > 1) ? BufferedImage.TYPE_3BYTE_BGR : BufferedImage.TYPE_BYTE_GRAY;
        byte[] b = new byte[m.channels() * m.cols() * m.rows()];
        m.get(0, 0, b);
        BufferedImage image = new BufferedImage(m.cols(), m.rows(), type);
        System.arraycopy(b, 0, ((DataBufferByte) image.getRaster().getDataBuffer()).getData(), 0, b.length);
        return image;
    }
}

