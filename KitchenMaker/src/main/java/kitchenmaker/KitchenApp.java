package kitchenmaker;

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

public class KitchenApp extends JFrame {

    private static final int DRAG_THRESHOLD = 5;
    private static final long SHAPE_DETECTION_COOLDOWN = 3000;
    private static boolean opencvAvailable = false;

    private final List<SceneData.Object3D> objects = Collections.synchronizedList(new ArrayList<>());
    private final RenderEngine renderer;
    private final GLJPanel glCanvas;
    private final DefaultListModel<SceneData.Object3D> listModel;
    private final JList<SceneData.Object3D> objectList;
    private final Vector3f dragOffset = new Vector3f();

    private JLabel webcamLabel;
    private boolean webcamRunning = false;
    private ShapeDetector shapeDetector;
    private boolean shapeDetectionEnabled = true;
    private long lastShapeDetectionTime = 0;
    private volatile boolean dialogOpen = false;

    private boolean isDraggingObject = false;
    private boolean isDragging = false;
    private int lastMouseX, lastMouseY, pressedMouseX, pressedMouseY;
    private float dragPlaneY = 0;

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

    public KitchenApp() {
        super("KitchenMaker von Niklas Puls");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1400, 900);
        setLayout(new BorderLayout(0, 0));

        getContentPane().setBackground(Theme.BACKGROUND);

        if (opencvAvailable) {
            shapeDetector = new ShapeDetector();
        }

        renderer = new RenderEngine(objects);

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
        objectList.setBackground(Theme.LIST_BACKGROUND);
        objectList.setForeground(Theme.TEXT_PRIMARY);
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


        setupInteraction();
        setLocationRelativeTo(null);
        setVisible(true);

        new FPSAnimator(glCanvas, 60).start();
    }

    private JToolBar createToolBar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBorder(new EmptyBorder(6, 10, 6, 10));
        toolbar.setBackground(Theme.PANEL);

        JButton importButton = createToolbarButton("icons/import.svg", "OBJ Importieren (Ctrl+O)");
        importButton.addActionListener(e -> importObjFile());

        JButton addButton = createToolbarButton("icons/plus.svg", "Objekt hinzufügen");
        addButton.addActionListener(e -> showAddObjectMenu(addButton));

        JButton editButton = createToolbarButton("icons/edit.svg", "Bearbeiten (Doppelklick)");
        editButton.addActionListener(e -> {
            if (renderer.selectedObject != null) showEditDialog(renderer.selectedObject);
        });

        JButton deleteButton = createToolbarButton("icons/delete.svg", "Löschen (Delete)");
        deleteButton.addActionListener(e -> deleteSelectedObject());

        toolbar.add(importButton);
        toolbar.add(addButton);
        toolbar.addSeparator(new Dimension(20, 0));
        toolbar.add(editButton);
        toolbar.add(deleteButton);
        toolbar.addSeparator(new Dimension(20, 0));

        JButton webcamButton = createToolbarButton("icons/camera.svg", "Webcam Start/Stop");
        webcamButton.addActionListener(e -> toggleWebcam());
        toolbar.add(webcamButton);

        toolbar.add(Box.createHorizontalGlue());

        JLabel titleLabel = new JLabel("KitchenMaker");
        titleLabel.setFont(Theme.TITLE);
        titleLabel.setForeground(Theme.TEXT_MUTED);
        toolbar.add(titleLabel);

        return toolbar;
    }

    private void showAddObjectMenu(JButton source) {
        JPopupMenu menu = new JPopupMenu();

        // Küchenobjekte: {Typ, Anzeigename, Icon}
        String[][] items = {
            {"Fridge", "Kühlschrank", "icons/fridge.svg"},
            {"Microwave", "Mikrowelle", "icons/microwave.svg"},
            {"Oven", "Backofen", "icons/oven.svg"},
            {"Counter", "Theke", "icons/counter.svg"},
            {"Counter Inner Corner", "Theke Innenecke", "icons/counter_corner_inner.svg"},
            {"Counter Outer Corner", "Theke Außenecke", "icons/counter_corner_outer.svg"},
            {"Sink", "Waschbecken", "icons/sink.svg"}
        };

        for (String[] item : items) {
            menu.add(createMenuItem(item[1], item[2], () -> addObjectByType(item[0])));
        }

        menu.show(source, 0, source.getHeight());
    }

    private void addObjectByType(String type) {
        SceneData.Object3D obj = SceneData.createByType(type);
        if (obj != null) {
            obj.name = obj.name + " " + (objects.size() + 1);
            addObject(obj);
        }
    }

    private JMenuItem createMenuItem(String text, String iconPath, Runnable action) {
        JMenuItem item = new JMenuItem(text, new FlatSVGIcon(iconPath, 16, 16));
        item.addActionListener(e -> action.run());
        return item;
    }

    private JButton createToolbarButton(String iconPath, String tooltip) {
        JButton button = new JButton(new FlatSVGIcon(iconPath, 20, 20));
        button.setToolTipText(tooltip);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(Theme.TOOLBAR_BUTTON);
        button.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { button.setContentAreaFilled(true); button.setBackground(Theme.HOVER); }
            public void mouseExited(MouseEvent e) { button.setContentAreaFilled(false); }
        });

        return button;
    }

    private JPanel createSidePanel() {
        JPanel sidePanel = new JPanel(new BorderLayout(0, 10));
        sidePanel.setPreferredSize(Theme.SIDE_PANEL);
        sidePanel.setBackground(Theme.PANEL);
        sidePanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel objectsLabel = new JLabel("Objekte");
        objectsLabel.setFont(Theme.LABEL);
        objectsLabel.setForeground(Theme.TEXT_LABEL);
        objectsLabel.setBorder(new EmptyBorder(0, 0, 5, 0));

        JScrollPane scrollPane = new JScrollPane(objectList);
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        scrollPane.setBackground(Theme.LIST_BACKGROUND);

        JPanel listPanel = new JPanel(new BorderLayout());
        listPanel.setOpaque(false);
        listPanel.add(objectsLabel, BorderLayout.NORTH);
        listPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel webcamPanel = new JPanel(new BorderLayout(0, 5));
        webcamPanel.setOpaque(false);

        JLabel webcamTitleLabel = new JLabel("Webcam & Formerkennung");
        webcamTitleLabel.setFont(Theme.LABEL);
        webcamTitleLabel.setForeground(Theme.TEXT_LABEL);

        webcamLabel = new JLabel("Aus", SwingConstants.CENTER);
        webcamLabel.setPreferredSize(Theme.WEBCAM_PREVIEW);
        webcamLabel.setBackground(Theme.BACKGROUND_DARK);
        webcamLabel.setForeground(Theme.TEXT_DISABLED);
        webcamLabel.setOpaque(true);
        webcamLabel.setBorder(BorderFactory.createLineBorder(Theme.BORDER));

        JCheckBox shapeDetectionCheckBox = new JCheckBox("Formerkennung aktiv", shapeDetectionEnabled);
        shapeDetectionCheckBox.setFont(Theme.LABEL_SMALL);
        shapeDetectionCheckBox.setForeground(Theme.TEXT_LABEL);
        shapeDetectionCheckBox.setOpaque(false);
        shapeDetectionCheckBox.addActionListener(e -> shapeDetectionEnabled = shapeDetectionCheckBox.isSelected());

        JPanel webcamHeaderPanel = new JPanel(new BorderLayout());
        webcamHeaderPanel.setOpaque(false);
        webcamHeaderPanel.add(webcamTitleLabel, BorderLayout.NORTH);
        webcamHeaderPanel.add(shapeDetectionCheckBox, BorderLayout.SOUTH);

        webcamPanel.add(webcamHeaderPanel, BorderLayout.NORTH);
        webcamPanel.add(webcamLabel, BorderLayout.CENTER);

        sidePanel.add(listPanel, BorderLayout.CENTER);
        sidePanel.add(webcamPanel, BorderLayout.SOUTH);

        return sidePanel;
    }

    private void showEditDialog(SceneData.Object3D obj) {
        JDialog dialog = new JDialog(this, "Bearbeiten: " + obj.name, true);
        dialog.setSize(400, 320);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        // Original-Werte speichern für Abbrechen-Funktion
        String originalName = obj.name;
        Vector3f originalPos = new Vector3f(obj.position);
        float originalRotY = obj.rotation.y;
        Vector3f originalColor = new Vector3f(obj.color);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Name-Feld
        JPanel namePanel = createLabeledField("Name:");
        JTextField nameField = new JTextField(obj.name);
        nameField.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) { obj.name = nameField.getText(); objectList.repaint(); }
        });
        namePanel.add(nameField);
        content.add(namePanel);
        content.add(Box.createVerticalStrut(15));

        // Position-Slider
        content.add(createSlider("Position X:", -100, 100, (int)(obj.position.x * 10),
            v -> { obj.position.x = v / 10f; glCanvas.repaint(); }, "%.1f", 10f));
        content.add(createSlider("Position Y:", -100, 100, (int)(obj.position.y * 10),
            v -> { obj.position.y = v / 10f; glCanvas.repaint(); }, "%.1f", 10f));
        content.add(createSlider("Position Z:", -100, 100, (int)(obj.position.z * 10),
            v -> { obj.position.z = v / 10f; glCanvas.repaint(); }, "%.1f", 10f));
        content.add(createSlider("Rotation Y:", 0, 360, (int)Math.toDegrees(obj.rotation.y),
            v -> { obj.rotation.y = (float)Math.toRadians(v); glCanvas.repaint(); }, "%d°", 1f));

        content.add(Box.createVerticalStrut(10));

        // Farbauswahl
        JPanel colorPanel = createLabeledField("Farbe:");
        JButton colorButton = new JButton("  ");
        colorButton.setBackground(new Color(obj.color.x, obj.color.y, obj.color.z));
        colorButton.setPreferredSize(Theme.COLOR_BUTTON);
        colorButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.ICON_BORDER, 1),
            BorderFactory.createEmptyBorder(2, 2, 2, 2)));
        colorButton.setFocusPainted(false);
        colorButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        colorButton.addActionListener(e -> {
            Color newColor = JColorChooser.showDialog(dialog, "Farbe wählen", colorButton.getBackground());
            if (newColor != null) {
                colorButton.setBackground(newColor);
                obj.color.set(newColor.getRed() / 255f, newColor.getGreen() / 255f, newColor.getBlue() / 255f);
                objectList.repaint();
                glCanvas.repaint();
            }
        });
        colorPanel.add(colorButton);
        content.add(colorPanel);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelButton = new JButton("Abbrechen");
        cancelButton.addActionListener(e -> {
            obj.name = originalName;
            obj.position.set(originalPos);
            obj.rotation.y = originalRotY;
            obj.color.set(originalColor);
            objectList.repaint();
            glCanvas.repaint();
            dialog.dispose();
        });
        JButton applyButton = new JButton("OK");
        applyButton.addActionListener(e -> dialog.dispose());
        buttonPanel.add(applyButton);
        buttonPanel.add(cancelButton);

        dialog.add(content, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private JPanel createSlider(String label, int min, int max, int value,
                                 java.util.function.IntConsumer onChange, String format, float divisor) {
        JPanel panel = createLabeledField(label);
        JSlider slider = new JSlider(min, max, value);
        Object initialValue = divisor == 1f ? value : (Object)(value / divisor);
        JLabel valueLabel = new JLabel(String.format(format, initialValue));
        valueLabel.setPreferredSize(Theme.VALUE_LABEL);
        slider.addChangeListener(e -> {
            onChange.accept(slider.getValue());
            Object displayValue = divisor == 1f ? slider.getValue() : (Object)(slider.getValue() / divisor);
            valueLabel.setText(String.format(format, displayValue));
        });
        panel.add(slider);
        panel.add(valueLabel);
        return panel;
    }

    private JPanel createLabeledField(String label) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        JLabel jLabel = new JLabel(label);
        jLabel.setPreferredSize(Theme.LABEL_FIELD);
        panel.add(jLabel);
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
        fc.setCurrentDirectory(new java.io.File("."));
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
                        renderer.selectedObject = clicked;
                        objectList.setSelectedValue(clicked, true);

                        dragPlaneY = clicked.position.y;
                        Vector3f hitPoint = screenToGroundPlane(e.getX(), e.getY(), dragPlaneY);
                        dragOffset.set(hitPoint).sub(clicked.position);
                    } else {
                        // Ins Nichts geklickt - Objekt-Selektion aufheben für Kamera-Rotation
                        renderer.selectedObject = null;
                        objectList.clearSelection();
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                isDraggingObject = false;
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
        if (!capture.isOpened()) capture = new VideoCapture(1);

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
                if (shapeDetectionEnabled && shapeDetector != null) {
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
            g2.setColor(Theme.ICON_BORDER_LIGHT);
            g2.drawRoundRect(x, y, width - 1, height - 1, 4, 4);
            g2.dispose();
        }

        @Override
        public int getIconWidth() { return width; }

        @Override
        public int getIconHeight() { return height; }
    }
}

