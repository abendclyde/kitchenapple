package shapeviewer;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLJPanel;
import com.jogamp.opengl.util.FPSAnimator;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ShapeViewerApp extends JFrame {

    private final List<SceneData.Object3D> objects = Collections.synchronizedList(new ArrayList<>());
    private final RenderEngine renderer;
    private final GLJPanel glCanvas;
    private final DefaultListModel<SceneData.Object3D> listModel;
    private final JList<SceneData.Object3D> objectList;

    // Webcam
    private JLabel lblWebcam;
    private boolean webcamRunning = false;
    private Thread webcamThread;
    private static boolean opencvAvailable = false;

    // Formerkennung
    private ShapeDetector shapeDetector;
    private boolean shapeDetectionEnabled = true;
    private long lastShapeDetectionTime = 0;
    private static final long SHAPE_DETECTION_COOLDOWN = 3000; // 3 Sekunden Cooldown
    private volatile boolean dialogOpen = false;

    // Drag & Drop für Objekte
    private SceneData.Object3D draggedObject = null;
    private boolean isDraggingObject = false;
    private int lastMouseX, lastMouseY, pressedMouseX, pressedMouseY;
    private boolean isDragging = false;
    private static final int DRAG_THRESHOLD = 5;
    private Vector3f dragOffset = new Vector3f();
    private float dragPlaneY = 0;

    public static void main(String[] args) {

        // FlatLaf Dark Theme aktivieren
        FlatDarkLaf.setup();
        UIManager.put("Button.arc", 8);
        UIManager.put("Component.arc", 8);
        UIManager.put("TextComponent.arc", 8);

        try {
            nu.pattern.OpenCV.loadLocally();
            opencvAvailable = true;
            System.out.println("[INFO] OpenCV erfolgreich geladen.");
        } catch (Throwable t) {
            opencvAvailable = false;
            System.err.println("[WARN] OpenCV konnte nicht geladen werden: " + t.getMessage());
        }

        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            new ShapeViewerApp();
        } else {
            SwingUtilities.invokeLater(ShapeViewerApp::new);
        }
    }

    public ShapeViewerApp() {
        super("ShapeViewer3D");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1400, 900);
        setLayout(new BorderLayout(0, 0));

        getContentPane().setBackground(new Color(30, 30, 30));

        // ShapeDetector initialisieren
        if (opencvAvailable) {
            shapeDetector = new ShapeDetector();
        }

        System.out.println("[INFO] Initialisiere OpenGL...");
        renderer = new RenderEngine(objects);

        // Explizite GLCapabilities-Konfiguration für bessere Kompatibilität
        GLProfile glProfile = GLProfile.getDefault();
        GLCapabilities glCapabilities = new GLCapabilities(glProfile);
        glCapabilities.setDoubleBuffered(true);
        glCapabilities.setHardwareAccelerated(true);

        glCanvas = new GLJPanel(glCapabilities);
        glCanvas.addGLEventListener(renderer);

        listModel = new DefaultListModel<>();
        objectList = new JList<>(listModel);
        objectList.setCellRenderer(new ObjectListCellRenderer());
        objectList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        objectList.setBackground(new Color(40, 40, 45));
        objectList.setForeground(Color.WHITE);
        objectList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                renderer.selectedObject = objectList.getSelectedValue();
            }
        });
        objectList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && objectList.getSelectedValue() != null) {
                    showEditDialog(objectList.getSelectedValue());
                }
            }
        });

        add(createToolBar(), BorderLayout.NORTH);
        add(glCanvas, BorderLayout.CENTER);
        add(createSidePanel(), BorderLayout.WEST);

        SceneData.Object3D pyramid = SceneData.createPyramid();
        addObject(pyramid);
        objectList.setSelectedIndex(0);

        setupInteraction();
        setLocationRelativeTo(null);
        setVisible(true);

        new FPSAnimator(glCanvas, 60).start();
    }

    private JToolBar createToolBar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBorder(new EmptyBorder(6, 10, 6, 10));
        toolbar.setBackground(new Color(35, 35, 40));

        JButton btnImport = createToolbarButton("icons/import.svg", "OBJ Importieren (Ctrl+O)");
        btnImport.addActionListener(e -> importObjFile());

        JButton btnAdd = createToolbarButton("icons/plus.svg", "Objekt hinzufügen");
        btnAdd.addActionListener(e -> showAddObjectMenu(btnAdd));

        JButton btnEdit = createToolbarButton("icons/edit.svg", "Bearbeiten (Doppelklick)");
        btnEdit.addActionListener(e -> {
            if (renderer.selectedObject != null) {
                showEditDialog(renderer.selectedObject);
            }
        });

        JButton btnDelete = createToolbarButton("icons/delete.svg", "Löschen (Delete)");
        btnDelete.addActionListener(e -> deleteSelectedObject());

        toolbar.add(btnImport);
        toolbar.add(btnAdd);
        toolbar.addSeparator(new Dimension(20, 0));
        toolbar.add(btnEdit);
        toolbar.add(btnDelete);
        toolbar.addSeparator(new Dimension(20, 0));

        JButton btnWebcam = createToolbarButton("icons/camera.svg", "Webcam Start/Stop");
        btnWebcam.addActionListener(e -> toggleWebcam());
        toolbar.add(btnWebcam);

        toolbar.add(Box.createHorizontalGlue());

        JLabel titleLabel = new JLabel("ShapeViewer3D");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        titleLabel.setForeground(new Color(180, 180, 180));
        toolbar.add(titleLabel);

        return toolbar;
    }

    private void showAddObjectMenu(JButton source) {
        JPopupMenu menu = new JPopupMenu();

        // Pyramide
        JMenuItem pyramidItem = createMenuItem("Pyramide", "icons/pyramid.svg", () -> {
            SceneData.Object3D obj = SceneData.createPyramid();
            obj.name = "Pyramid " + (objects.size() + 1);
            addObject(obj);
        });
        menu.add(pyramidItem);

        // Würfel
        JMenuItem cubeItem = createMenuItem("Würfel", "icons/cube.svg", () -> {
            SceneData.Object3D obj = SceneData.createCube();
            obj.name = "Cube " + (objects.size() + 1);
            addObject(obj);
        });
        menu.add(cubeItem);

        // Kugel
        JMenuItem sphereItem = createMenuItem("Kugel", "icons/sphere.svg", () -> {
            SceneData.Object3D obj = SceneData.createSphere(24, 16);
            obj.name = "Sphere " + (objects.size() + 1);
            addObject(obj);
        });
        menu.add(sphereItem);

        // Zylinder
        JMenuItem cylinderItem = createMenuItem("Zylinder", "icons/cylinder.svg", () -> {
            SceneData.Object3D obj = SceneData.createCylinder(24);
            obj.name = "Cylinder " + (objects.size() + 1);
            addObject(obj);
        });
        menu.add(cylinderItem);

        // Torus
        JMenuItem torusItem = createMenuItem("Torus", "icons/torus.svg", () -> {
            SceneData.Object3D obj = SceneData.createTorus(24, 12);
            obj.name = "Torus " + (objects.size() + 1);
            addObject(obj);
        });
        menu.add(torusItem);

        menu.addSeparator();

        // Ebene
        JMenuItem planeItem = createMenuItem("Ebene", "icons/plane.svg", () -> {
            SceneData.Object3D obj = SceneData.createPlane();
            obj.name = "Plane " + (objects.size() + 1);
            addObject(obj);
        });
        menu.add(planeItem);

        menu.show(source, 0, source.getHeight());
    }

    private JMenuItem createMenuItem(String text, String iconPath, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        try {
            FlatSVGIcon icon = new FlatSVGIcon(iconPath, 16, 16);
            item.setIcon(icon);
        } catch (Exception ignored) {}
        item.addActionListener(e -> action.run());
        return item;
    }

    private JButton createToolbarButton(String iconPath, String tooltip) {
        JButton btn = new JButton();
        try {
            FlatSVGIcon icon = new FlatSVGIcon(iconPath, 20, 20);
            btn.setIcon(icon);
        } catch (Exception e) {
            btn.setText("?");
        }
        btn.setToolTipText(tooltip);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(36, 36));

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

    private JPanel createSidePanel() {
        JPanel sidePanel = new JPanel(new BorderLayout(0, 10));
        sidePanel.setPreferredSize(new Dimension(280, 0));
        sidePanel.setBackground(new Color(35, 35, 40));
        sidePanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel lblObjects = new JLabel("Objekte");
        lblObjects.setFont(new Font("SansSerif", Font.BOLD, 12));
        lblObjects.setForeground(new Color(150, 150, 150));
        lblObjects.setBorder(new EmptyBorder(0, 0, 5, 0));

        JScrollPane scrollPane = new JScrollPane(objectList);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 65)));
        scrollPane.setBackground(new Color(40, 40, 45));

        JPanel listPanel = new JPanel(new BorderLayout());
        listPanel.setOpaque(false);
        listPanel.add(lblObjects, BorderLayout.NORTH);
        listPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel webcamPanel = new JPanel(new BorderLayout(0, 5));
        webcamPanel.setOpaque(false);

        JLabel lblWebcamTitle = new JLabel("Webcam & Formerkennung");
        lblWebcamTitle.setFont(new Font("SansSerif", Font.BOLD, 12));
        lblWebcamTitle.setForeground(new Color(150, 150, 150));

        lblWebcam = new JLabel("Aus", SwingConstants.CENTER);
        lblWebcam.setPreferredSize(new Dimension(260, 195));
        lblWebcam.setBackground(new Color(25, 25, 30));
        lblWebcam.setForeground(new Color(100, 100, 100));
        lblWebcam.setOpaque(true);
        lblWebcam.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 65)));

        // Toggle-Button für Formerkennung
        JCheckBox chkShapeDetection = new JCheckBox("Formerkennung aktiv", shapeDetectionEnabled);
        chkShapeDetection.setFont(new Font("SansSerif", Font.PLAIN, 11));
        chkShapeDetection.setForeground(new Color(150, 150, 150));
        chkShapeDetection.setOpaque(false);
        chkShapeDetection.addActionListener(e -> shapeDetectionEnabled = chkShapeDetection.isSelected());

        JPanel webcamHeaderPanel = new JPanel(new BorderLayout());
        webcamHeaderPanel.setOpaque(false);
        webcamHeaderPanel.add(lblWebcamTitle, BorderLayout.NORTH);
        webcamHeaderPanel.add(chkShapeDetection, BorderLayout.SOUTH);

        webcamPanel.add(webcamHeaderPanel, BorderLayout.NORTH);
        webcamPanel.add(lblWebcam, BorderLayout.CENTER);

        sidePanel.add(listPanel, BorderLayout.CENTER);
        sidePanel.add(webcamPanel, BorderLayout.SOUTH);

        return sidePanel;
    }

    private void showEditDialog(SceneData.Object3D obj) {
        JDialog dialog = new JDialog(this, "Bearbeiten: " + obj.name, true);
        dialog.setSize(350, 320);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(20, 20, 20, 20));

        JPanel namePanel = createLabeledField("Name:");
        JTextField txtName = new JTextField(obj.name);
        namePanel.add(txtName);
        content.add(namePanel);
        content.add(Box.createVerticalStrut(15));

        JPanel posXPanel = createLabeledField("Position X:");
        JSlider sldX = new JSlider(-50, 50, (int)(obj.position.x * 10));
        JLabel lblX = new JLabel(String.format("%.1f", obj.position.x));
        lblX.setPreferredSize(new Dimension(40, 20));
        sldX.addChangeListener(e -> lblX.setText(String.format("%.1f", sldX.getValue() / 10f)));
        posXPanel.add(sldX);
        posXPanel.add(lblX);
        content.add(posXPanel);

        JPanel posYPanel = createLabeledField("Position Y:");
        JSlider sldY = new JSlider(-50, 50, (int)(obj.position.y * 10));
        JLabel lblY = new JLabel(String.format("%.1f", obj.position.y));
        lblY.setPreferredSize(new Dimension(40, 20));
        sldY.addChangeListener(e -> lblY.setText(String.format("%.1f", sldY.getValue() / 10f)));
        posYPanel.add(sldY);
        posYPanel.add(lblY);
        content.add(posYPanel);

        JPanel rotPanel = createLabeledField("Rotation Y:");
        JSlider sldRotY = new JSlider(0, 360, (int)Math.toDegrees(obj.rotation.y));
        JLabel lblRot = new JLabel(sldRotY.getValue() + "°");
        lblRot.setPreferredSize(new Dimension(40, 20));
        sldRotY.addChangeListener(e -> lblRot.setText(sldRotY.getValue() + "°"));
        rotPanel.add(sldRotY);
        rotPanel.add(lblRot);
        content.add(rotPanel);

        content.add(Box.createVerticalStrut(10));

        JPanel colorPanel = createLabeledField("Farbe:");
        JButton btnColor = new JButton("  ");
        Color initialColor = new Color(obj.color.x, obj.color.y, obj.color.z);
        btnColor.setBackground(initialColor);
        btnColor.setPreferredSize(new Dimension(80, 28));
        btnColor.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(80, 80, 85), 1),
            BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));
        btnColor.setFocusPainted(false);
        btnColor.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnColor.addActionListener(e -> {
            Color newColor = JColorChooser.showDialog(dialog, "Farbe wählen", btnColor.getBackground());
            if (newColor != null) {
                btnColor.setBackground(newColor);
            }
        });
        colorPanel.add(btnColor);
        content.add(colorPanel);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnCancel = new JButton("Abbrechen");
        btnCancel.addActionListener(e -> dialog.dispose());

        JButton btnApply = new JButton("Anwenden");
        btnApply.addActionListener(e -> {
            obj.name = txtName.getText();
            obj.position.x = sldX.getValue() / 10f;
            obj.position.y = sldY.getValue() / 10f;
            obj.rotation.y = (float) Math.toRadians(sldRotY.getValue());
            Color c = btnColor.getBackground();
            obj.color.set(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f);
            objectList.repaint();
            dialog.dispose();
        });

        buttonPanel.add(btnCancel);
        buttonPanel.add(btnApply);

        dialog.add(content, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private JPanel createLabeledField(String label) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        JLabel lbl = new JLabel(label);
        lbl.setPreferredSize(new Dimension(80, 20));
        panel.add(lbl);
        return panel;
    }

    private void addObject(SceneData.Object3D obj) {
        objects.add(obj);
        listModel.addElement(obj);
        objectList.setSelectedValue(obj, true);
        renderer.selectedObject = obj;
    }

    private void deleteSelectedObject() {
        if (renderer.selectedObject != null) {
            int idx = objects.indexOf(renderer.selectedObject);
            objects.remove(renderer.selectedObject);
            listModel.removeElement(renderer.selectedObject);
            renderer.selectedObject = objects.isEmpty() ? null : objects.get(Math.max(0, idx - 1));
            if (renderer.selectedObject != null) {
                objectList.setSelectedValue(renderer.selectedObject, true);
            }
        }
    }

    private void importObjFile() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("OBJ Files", "obj"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            SceneData.Object3D obj = SceneData.loadObj(fc.getSelectedFile());
            if (obj != null) {
                addObject(obj);
            }
        }
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

        glCanvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                pressedMouseX = lastMouseX = e.getX();
                pressedMouseY = lastMouseY = e.getY();
                isDragging = false;
                isDraggingObject = false;

                if (SwingUtilities.isLeftMouseButton(e)) {
                    SceneData.Object3D clicked = pickObject(e.getX(), e.getY());
                    if (clicked != null) {
                        draggedObject = clicked;
                        renderer.selectedObject = clicked;
                        objectList.setSelectedValue(clicked, true);

                        // Initialisiere Drag-Offset für Ray-basiertes Dragging
                        dragPlaneY = clicked.position.y;
                        Vector3f hitPoint = screenToGroundPlane(e.getX(), e.getY(), dragPlaneY);
                        dragOffset.set(hitPoint).sub(clicked.position);
                    } else {
                        draggedObject = null;
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                isDraggingObject = false;
                draggedObject = null;
            }
        });

        glCanvas.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                int dx = e.getX() - lastMouseX;
                int dy = e.getY() - lastMouseY;

                if (Math.abs(e.getX() - pressedMouseX) > DRAG_THRESHOLD ||
                    Math.abs(e.getY() - pressedMouseY) > DRAG_THRESHOLD) {
                    isDragging = true;
                    if (draggedObject != null) {
                        isDraggingObject = true;
                    }
                }

                if (isDragging) {
                    if (isDraggingObject && draggedObject != null) {
                        moveObjectOnGround(draggedObject, e.getX(), e.getY());
                    } else {
                        renderer.cameraYaw -= dx * 0.5f;
                        renderer.cameraPitch = Math.max(-85f, Math.min(85f, renderer.cameraPitch + dy * 0.5f));
                    }
                    glCanvas.repaint();
                }

                lastMouseX = e.getX();
                lastMouseY = e.getY();
            }
        });

        glCanvas.addMouseWheelListener(e ->
            renderer.cameraDistance = Math.max(1f, Math.min(50f,
                renderer.cameraDistance + (float)e.getPreciseWheelRotation() * 0.5f))
        );
    }

    private record Ray(Vector3f origin, Vector3f direction) {}

    private Ray createRayFromMouse(int mouseX, int mouseY) {
        int w = glCanvas.getWidth();
        int h = glCanvas.getHeight();
        float aspect = (float) w / h;

        float ndcX = (2.0f * mouseX) / w - 1.0f;
        float ndcY = 1.0f - (2.0f * mouseY) / h;

        float pitchRad = (float) Math.toRadians(renderer.cameraPitch);
        float yawRad = (float) Math.toRadians(renderer.cameraYaw);
        float camX = renderer.cameraDistance * (float)(Math.cos(pitchRad) * Math.sin(yawRad));
        float camY = renderer.cameraDistance * (float) Math.sin(pitchRad);
        float camZ = renderer.cameraDistance * (float)(Math.cos(pitchRad) * Math.cos(yawRad));
        Vector3f camPos = new Vector3f(camX, camY, camZ).add(renderer.camTarget);

        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(renderer.fov), aspect, 0.1f, 100f);
        Matrix4f view = new Matrix4f().lookAt(camPos, renderer.camTarget, new Vector3f(0, 1, 0));
        Matrix4f invVP = new Matrix4f(proj).mul(view).invert();

        Vector4f rayNear = new Vector4f(ndcX, ndcY, -1, 1).mul(invVP);
        rayNear.div(rayNear.w);
        Vector4f rayFar = new Vector4f(ndcX, ndcY, 1, 1).mul(invVP);
        rayFar.div(rayFar.w);

        Vector3f rayOrigin = new Vector3f(rayNear.x, rayNear.y, rayNear.z);
        Vector3f rayDir = new Vector3f(rayFar.x - rayNear.x, rayFar.y - rayNear.y, rayFar.z - rayNear.z).normalize();

        return new Ray(rayOrigin, rayDir);
    }

    private Vector3f screenToGroundPlane(int mouseX, int mouseY, float planeY) {
        Ray ray = createRayFromMouse(mouseX, mouseY);
        float t = (planeY - ray.origin.y) / ray.direction.y;
        return new Vector3f(ray.origin).add(new Vector3f(ray.direction).mul(t));
    }

    private SceneData.Object3D pickObject(int mouseX, int mouseY) {
        Ray ray = createRayFromMouse(mouseX, mouseY);

        SceneData.Object3D closest = null;
        float closestDist = Float.MAX_VALUE;

        synchronized (objects) {
            for (SceneData.Object3D obj : objects) {
                float t = intersectAABB(ray.origin, ray.direction, obj);
                if (t > 0 && t < closestDist) {
                    closestDist = t;
                    closest = obj;
                }
            }
        }

        return closest;
    }

    private float intersectAABB(Vector3f origin, Vector3f dir, SceneData.Object3D obj) {
        Vector3f min = new Vector3f(obj.min).mul(obj.scale).add(obj.position);
        Vector3f max = new Vector3f(obj.max).mul(obj.scale).add(obj.position);

        float padding = 0.2f;
        min.sub(padding, padding, padding);
        max.add(padding, padding, padding);

        float tmin = (min.x - origin.x) / dir.x;
        float tmax = (max.x - origin.x) / dir.x;
        if (tmin > tmax) { float tmp = tmin; tmin = tmax; tmax = tmp; }

        float tymin = (min.y - origin.y) / dir.y;
        float tymax = (max.y - origin.y) / dir.y;
        if (tymin > tymax) { float tmp = tymin; tymin = tymax; tymax = tmp; }

        if (tmin > tymax || tymin > tmax) return -1;
        if (tymin > tmin) tmin = tymin;
        if (tymax < tmax) tmax = tymax;

        float tzmin = (min.z - origin.z) / dir.z;
        float tzmax = (max.z - origin.z) / dir.z;
        if (tzmin > tzmax) { float tmp = tzmin; tzmin = tzmax; tzmax = tmp; }

        if (tmin > tzmax || tzmin > tmax) return -1;
        if (tzmin > tmin) tmin = tzmin;

        return tmin;
    }

    private void moveObjectOnGround(SceneData.Object3D obj, int mouseX, int mouseY) {
        Vector3f hitPoint = screenToGroundPlane(mouseX, mouseY, dragPlaneY);
        obj.position.x = hitPoint.x - dragOffset.x;
        obj.position.z = hitPoint.z - dragOffset.z;
    }

    private void toggleWebcam() {
        if (!opencvAvailable) {
            lblWebcam.setText("OpenCV fehlt");
            lblWebcam.setForeground(new Color(200, 80, 80));
            JOptionPane.showMessageDialog(this,
                "OpenCV konnte nicht geladen werden.\nWebcam-Funktion ist nicht verfügbar.",
                "OpenCV Fehler", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (webcamRunning) {
            webcamRunning = false;
            lblWebcam.setIcon(null);
            lblWebcam.setText("Aus");
            lblWebcam.setForeground(new Color(100, 100, 100));
        } else {
            webcamRunning = true;
            lblWebcam.setText("Starte...");
            lblWebcam.setForeground(new Color(100, 180, 100));
            webcamThread = new Thread(this::webcamLoop);
            webcamThread.start();
        }
    }

    private void webcamLoop() {
        VideoCapture capture = new VideoCapture(0);
        if (!capture.isOpened()) capture = new VideoCapture(1);

        if (!capture.isOpened()) {
            System.err.println("Keine Webcam gefunden.");
            SwingUtilities.invokeLater(() -> {
                lblWebcam.setText("Keine Webcam");
                lblWebcam.setForeground(new Color(200, 80, 80));
            });
            webcamRunning = false;
            return;
        }

        Mat frame = new Mat();
        while (webcamRunning) {
            if (capture.read(frame) && !frame.empty()) {
                // Formerkennung durchführen wenn aktiviert
                java.util.List<ShapeDetector.DetectedShape> detectedShapes = new java.util.ArrayList<>();
                if (shapeDetectionEnabled && shapeDetector != null) {
                    detectedShapes = shapeDetector.detectShapes(frame);

                    // Dialog anzeigen für gültige Mappings (mit Cooldown)
                    long currentTime = System.currentTimeMillis();
                    if (!dialogOpen && currentTime - lastShapeDetectionTime > SHAPE_DETECTION_COOLDOWN) {
                        for (ShapeDetector.DetectedShape shape : detectedShapes) {
                            if (ShapeDetector.isValidMapping(shape)) {
                                lastShapeDetectionTime = currentTime;
                                showAddShapeDialog(shape);
                                break; // Nur ein Dialog pro Durchgang
                            }
                        }
                    }
                }

                BufferedImage img = matToImage(frame);
                Image scaled = img.getScaledInstance(260, 195, Image.SCALE_FAST);
                SwingUtilities.invokeLater(() -> {
                    lblWebcam.setText(null);
                    lblWebcam.setIcon(new ImageIcon(scaled));
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
                this,
                shapeName + " erkannt!\n\nMöchten Sie eine " + objectName + " zur Szene hinzufügen?",
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

    private static class ObjectListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                       boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof SceneData.Object3D obj) {
                setText(obj.name);
                setBorder(new EmptyBorder(8, 10, 8, 10));

                Color objColor = new Color(obj.color.x, obj.color.y, obj.color.z);
                setIcon(new ColorIcon(objColor, 12, 12));

                if (isSelected) {
                    setBackground(new Color(70, 130, 180));
                    setForeground(Color.WHITE);
                } else {
                    setBackground(new Color(40, 40, 45));
                    setForeground(new Color(220, 220, 220));
                }
            }
            return this;
        }
    }

    private static class ColorIcon implements Icon {
        private final Color color;
        private final int width, height;

        public ColorIcon(Color color, int width, int height) {
            this.color = color;
            this.width = width;
            this.height = height;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillRoundRect(x, y, width, height, 4, 4);
            g2.setColor(new Color(100, 100, 100));
            g2.drawRoundRect(x, y, width - 1, height - 1, 4, 4);
            g2.dispose();
        }

        @Override
        public int getIconWidth() { return width; }

        @Override
        public int getIconHeight() { return height; }
    }
}

