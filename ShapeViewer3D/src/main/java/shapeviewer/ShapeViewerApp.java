package shapeviewer;

import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.FPSAnimator;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ShapeViewerApp extends JFrame {

    private final List<SceneData.Object3D> objects = Collections.synchronizedList(new ArrayList<>());
    private final RenderEngine renderer;
    private final GLCanvas glCanvas;

    // UI Controls
    private JSlider sldX, sldY, sldZ, sldRotY;
    private JLabel lblWebcam;
    private boolean webcamRunning = false;
    private Thread webcamThread;
    private FPSAnimator animator;

    public static void main(String[] args) {
        // OpenCV laden
        try {
            nu.pattern.OpenCV.loadLocally();
        } catch (Throwable t) {
            // Ignorieren, falls OpenCV fehlt
        }

        String os = System.getProperty("os.name").toLowerCase();
        boolean isMac = os.contains("mac");

        if (isMac) {
            System.out.println("Starte im macOS-Modus (Main Thread)...");
            new ShapeViewerApp();
        } else {
            SwingUtilities.invokeLater(() -> new ShapeViewerApp());
        }
    }

    public ShapeViewerApp() {
        // ÄNDERUNG: Neuer Titel zur Überprüfung
        super("ShapeViewer3D - V2 (Trackpad)");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLayout(new BorderLayout());

        System.out.println("[INFO] Initialisiere OpenGL...");
        renderer = new RenderEngine(objects);
        glCanvas = new GLCanvas();
        glCanvas.addGLEventListener(renderer);

        JPanel controlPanel = createControlPanel();
        add(glCanvas, BorderLayout.CENTER);
        add(controlPanel, BorderLayout.EAST);

        // Demo Objekt hinzufügen
        objects.add(SceneData.createPyramid());

        // ÄNDERUNG: Objekt sofort auswählen, damit Slider funktionieren!
        if (!objects.isEmpty()) {
            renderer.selectedObject = objects.get(0);
            updateSliders();
        }

        setupInteraction();
        setLocationRelativeTo(null);
        setVisible(true);

        animator = new FPSAnimator(glCanvas, 60);
        animator.start();
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setPreferredSize(new Dimension(250, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JButton btnImport = new JButton("OBJ Importieren");
        btnImport.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                SceneData.Object3D obj = SceneData.loadObj(fc.getSelectedFile());
                if (obj != null) {
                    objects.add(obj);
                    // Neues Objekt direkt auswählen
                    renderer.selectedObject = obj;
                    updateSliders();
                }
            }
        });

        JButton btnDelete = new JButton("Löschen");
        btnDelete.addActionListener(e -> {
            if (renderer.selectedObject != null) {
                objects.remove(renderer.selectedObject);
                renderer.selectedObject = null;
                if (!objects.isEmpty())
                    renderer.selectedObject = objects.get(0);
                updateSliders();
            }
        });

        JButton btnWebcam = new JButton("Webcam Start/Stop");
        btnWebcam.addActionListener(e -> toggleWebcam());

        lblWebcam = new JLabel("Webcam aus");
        lblWebcam.setPreferredSize(new Dimension(200, 150));
        lblWebcam.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        lblWebcam.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Slider
        sldX = new JSlider(-50, 50, 0);
        sldY = new JSlider(-50, 50, 0);
        sldRotY = new JSlider(0, 360, 0);

        javax.swing.event.ChangeListener cl = e -> {
            if (renderer.selectedObject != null) {
                renderer.selectedObject.position.x = sldX.getValue() / 10f;
                renderer.selectedObject.position.y = sldY.getValue() / 10f;
                renderer.selectedObject.rotation.y = (float) Math.toRadians(sldRotY.getValue());
            }
        };
        sldX.addChangeListener(cl);
        sldY.addChangeListener(cl);
        sldRotY.addChangeListener(cl);

        panel.add(new JLabel("Aktionen"));
        panel.add(btnImport);
        panel.add(Box.createVerticalStrut(5));
        panel.add(btnDelete);
        panel.add(Box.createVerticalStrut(15));
        panel.add(new JLabel("Position X / Y"));
        panel.add(sldX);
        panel.add(sldY);
        panel.add(new JLabel("Rotation Y"));
        panel.add(sldRotY);
        panel.add(Box.createVerticalStrut(20));
        panel.add(btnWebcam);
        panel.add(lblWebcam);

        return panel;
    }

    // Maus-Variablen
    private int lastMouseX, lastMouseY;
    private int pressedMouseX, pressedMouseY;
    private boolean isDragging = false;
    private static final int DRAG_THRESHOLD = 5; // Pixel

    private void setupInteraction() {
        glCanvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // Speichere Start-Position für Drag-Erkennung
                pressedMouseX = e.getX();
                pressedMouseY = e.getY();
                lastMouseX = e.getX();
                lastMouseY = e.getY();
                isDragging = false;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                // isDragging bleibt für mouseClicked Prüfung erhalten
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                // Nur auswählen, wenn NICHT gezogen wurde (basierend auf Distanz)
                int dx = Math.abs(e.getX() - pressedMouseX);
                int dy = Math.abs(e.getY() - pressedMouseY);
                boolean wasDragged = (dx > DRAG_THRESHOLD || dy > DRAG_THRESHOLD);

                if (!wasDragged && SwingUtilities.isLeftMouseButton(e)) {
                    if (!objects.isEmpty()) {
                        int idx = objects.indexOf(renderer.selectedObject);
                        if (idx == -1 && !objects.isEmpty())
                            idx = 0;
                        else
                            idx = (idx + 1) % objects.size();

                        if (!objects.isEmpty()) {
                            renderer.selectedObject = objects.get(idx);
                            System.out.println("Objekt ausgewählt: " + idx);
                            updateSliders();
                        }
                    }
                }
                isDragging = false;
            }
        });

        glCanvas.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                // Erst prüfen ob echtes Dragging (über Schwellwert)
                int dx = Math.abs(e.getX() - pressedMouseX);
                int dy = Math.abs(e.getY() - pressedMouseY);

                if (dx > DRAG_THRESHOLD || dy > DRAG_THRESHOLD) {
                    isDragging = true;
                }

                if (isDragging) {
                    int deltaX = e.getX() - lastMouseX;
                    int deltaY = e.getY() - lastMouseY;

                    renderer.cameraYaw += deltaX * 0.5f;
                    renderer.cameraPitch += deltaY * 0.5f;
                    renderer.cameraPitch = Math.max(-85f, Math.min(85f, renderer.cameraPitch));

                    lastMouseX = e.getX();
                    lastMouseY = e.getY();
                    glCanvas.repaint();
                }
            }
        });

        glCanvas.addMouseWheelListener(e -> {
            float scrollAmount = (float) e.getPreciseWheelRotation();
            renderer.cameraDistance += scrollAmount * 0.5f;
            renderer.cameraDistance = Math.max(1f, Math.min(50f, renderer.cameraDistance));
        });
    }

    private void updateSliders() {
        if (renderer.selectedObject != null) {
            // Slider Updates blockieren Listener temporär nicht nötig da set logic einfach
            // ist
            sldX.setValue((int) (renderer.selectedObject.position.x * 10));
            sldY.setValue((int) (renderer.selectedObject.position.y * 10));
            sldRotY.setValue((int) Math.toDegrees(renderer.selectedObject.rotation.y));
        }
    }

    private void toggleWebcam() {
        if (webcamRunning) {
            webcamRunning = false;
        } else {
            webcamRunning = true;
            webcamThread = new Thread(this::webcamLoop);
            webcamThread.start();
        }
    }

    private void webcamLoop() {
        // Stark vereinfachter Loop für Stabilität
        VideoCapture capture = new VideoCapture(0);
        if (!capture.isOpened())
            capture = new VideoCapture(1);

        if (!capture.isOpened()) {
            System.err.println("Keine Webcam gefunden.");
            webcamRunning = false;
            return;
        }

        Mat frame = new Mat();
        while (webcamRunning) {
            capture.read(frame);
            if (!frame.empty()) {
                BufferedImage img = matToImage(frame);
                SwingUtilities.invokeLater(() -> {
                    if (lblWebcam != null)
                        lblWebcam.setIcon(new ImageIcon(img));
                });
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
            }
        }
        capture.release();
    }

    private BufferedImage matToImage(Mat m) {
        int type = BufferedImage.TYPE_3BYTE_BGR;
        if (m.channels() == 1)
            type = BufferedImage.TYPE_BYTE_GRAY;
        int bufferSize = m.channels() * m.cols() * m.rows();
        byte[] b = new byte[bufferSize];
        m.get(0, 0, b);
        BufferedImage image = new BufferedImage(m.cols(), m.rows(), type);
        final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(b, 0, targetPixels, 0, b.length);
        return image;
    }
}