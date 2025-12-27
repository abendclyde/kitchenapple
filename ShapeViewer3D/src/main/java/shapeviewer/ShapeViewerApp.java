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
            t.printStackTrace();
        }

        String os = System.getProperty("os.name").toLowerCase();
        boolean isMac = os.contains("mac");

        if (isMac) {
            // macOS Spezial-Behandlung (wegen -XstartOnFirstThread)
            System.out.println("Starte im macOS-Modus (Main Thread)...");
            new ShapeViewerApp();
        } else {
            // Windows / Linux Standard (Swing Thread)
            System.out.println("Starte im Standard-Modus (Event Dispatch Thread)...");
            SwingUtilities.invokeLater(() -> new ShapeViewerApp());
        }
    }

    public ShapeViewerApp() {
        super("ShapeViewer3D - Apple Silicon Edition");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLayout(new BorderLayout());

        System.out.println("[INFO] Initialisiere OpenGL...");
        // 1. OpenGL Setup
        renderer = new RenderEngine(objects);
        glCanvas = new GLCanvas();
        glCanvas.addGLEventListener(renderer);

        // 2. GUI Setup
        JPanel controlPanel = createControlPanel();
        add(glCanvas, BorderLayout.CENTER);
        add(controlPanel, BorderLayout.EAST);

        // Demo Objekt hinzufügen
        objects.add(SceneData.createPyramid());

        // 3. Interaktion
        setupInteraction();

        // Fenster zentrieren
        setLocationRelativeTo(null);

        System.out.println("[INFO] Mache Fenster sichtbar...");
        // WICHTIG: Erst sichtbar machen...
        setVisible(true);

        System.out.println("[INFO] Starte Animator...");
        // ... DANN den Animator starten! Sonst Deadlock auf Mac.
        animator = new FPSAnimator(glCanvas, 60);
        animator.start();
        System.out.println("[INFO] Animator läuft!");
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setPreferredSize(new Dimension(250, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        JButton btnImport = new JButton("OBJ Importieren");
        btnImport.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            if(fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                SceneData.Object3D obj = SceneData.loadObj(fc.getSelectedFile());
                if(obj != null) objects.add(obj);
            }
        });

        JButton btnDelete = new JButton("Löschen");
        btnDelete.addActionListener(e -> {
            if(renderer.selectedObject != null) {
                objects.remove(renderer.selectedObject);
                renderer.selectedObject = null;
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
            if(renderer.selectedObject != null) {
                renderer.selectedObject.position.x = sldX.getValue() / 10f;
                renderer.selectedObject.position.y = sldY.getValue() / 10f;
                renderer.selectedObject.rotation.y = (float)Math.toRadians(sldRotY.getValue());
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

    // Für Maus-Drag-Tracking
    private int lastMouseX, lastMouseY;
    private boolean isDragging = false;

    private void setupInteraction() {
        glCanvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // Linksklick: Objekt auswählen
                if (SwingUtilities.isLeftMouseButton(e)) {
                    if(!objects.isEmpty()) {
                        int idx = objects.indexOf(renderer.selectedObject);
                        if (idx == -1 && !objects.isEmpty()) idx = 0;
                        else idx = (idx + 1) % objects.size();

                        if (!objects.isEmpty()) {
                            renderer.selectedObject = objects.get(idx);
                            updateSliders();
                        }
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                // Rechtsklick-Drag starten (2-Finger auf Trackpad)
                if (SwingUtilities.isRightMouseButton(e)) {
                    isDragging = true;
                    lastMouseX = e.getX();
                    lastMouseY = e.getY();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    isDragging = false;
                }
            }
        });

        glCanvas.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (isDragging && SwingUtilities.isRightMouseButton(e)) {
                    int deltaX = e.getX() - lastMouseX;
                    int deltaY = e.getY() - lastMouseY;

                    // Yaw (horizontal) und Pitch (vertikal) anpassen
                    renderer.cameraYaw += deltaX * 0.5f;
                    renderer.cameraPitch += deltaY * 0.5f;

                    // Pitch begrenzen auf -85 bis 85 Grad (kein Überkippen)
                    renderer.cameraPitch = Math.max(-85f, Math.min(85f, renderer.cameraPitch));

                    lastMouseX = e.getX();
                    lastMouseY = e.getY();
                }
            }
        });

        // Mausrad für Zoom
        glCanvas.addMouseWheelListener(e -> {
            float scrollAmount = (float) e.getPreciseWheelRotation();
            renderer.cameraDistance += scrollAmount * 0.5f;
            // Distanz begrenzen
            renderer.cameraDistance = Math.max(1f, Math.min(50f, renderer.cameraDistance));
        });
    }

    private void updateSliders() {
        if(renderer.selectedObject != null) {
            sldX.setValue((int)(renderer.selectedObject.position.x * 10));
            sldY.setValue((int)(renderer.selectedObject.position.y * 10));
            sldRotY.setValue((int)Math.toDegrees(renderer.selectedObject.rotation.y));
        }
    }

    private void toggleWebcam() {
        if(webcamRunning) {
            webcamRunning = false;
        } else {
            webcamRunning = true;
            webcamThread = new Thread(this::webcamLoop);
            webcamThread.start();
        }
    }

    private void webcamLoop() {
        VideoCapture capture = new VideoCapture(0);
        if(!capture.isOpened()) {
            // Fallback: Versuch Index 1 (falls Index 0 blockiert ist)
            capture = new VideoCapture(1);
            if(!capture.isOpened()) {
                System.err.println("Konnte Webcam nicht öffnen.");
                return;
            }
        }

        Mat frame = new Mat();
        Mat gray = new Mat();
        Mat edges = new Mat();
        long lastAdd = 0;

        while(webcamRunning) {
            capture.read(frame);
            if(!frame.empty()) {
                Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);
                Imgproc.GaussianBlur(gray, gray, new Size(5,5), 0);
                Imgproc.Canny(gray, edges, 50, 150);

                List<MatOfPoint> contours = new ArrayList<>();
                Imgproc.findContours(edges, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

                for(MatOfPoint c : contours) {
                    double area = Imgproc.contourArea(c);
                    if(area > 3000) {
                        MatOfPoint2f c2f = new MatOfPoint2f(c.toArray());
                        double peri = Imgproc.arcLength(c2f, true);
                        MatOfPoint2f approx = new MatOfPoint2f();
                        Imgproc.approxPolyDP(c2f, approx, 0.04 * peri, true);

                        if(System.currentTimeMillis() - lastAdd > 2000) {
                            if(approx.total() == 3) {
                                addObjectThreadSafe("Triangle"); lastAdd = System.currentTimeMillis();
                            } else if (approx.total() == 4) {
                                addObjectThreadSafe("Square"); lastAdd = System.currentTimeMillis();
                            }
                        }
                        Imgproc.drawContours(frame, Collections.singletonList(c), -1, new Scalar(0,255,0), 2);
                    }
                }

                BufferedImage img = matToImage(frame);
                SwingUtilities.invokeLater(() -> {
                    if(lblWebcam != null && img != null) lblWebcam.setIcon(new ImageIcon(img));
                });
            }
            try { Thread.sleep(30); } catch (InterruptedException e) {}
        }
        capture.release();
    }

    private void addObjectThreadSafe(String type) {
        SceneData.Object3D newObj = SceneData.createPyramid();
        newObj.name = type + "_" + System.currentTimeMillis();
        newObj.position.x = (float)(Math.random()*4 - 2);
        objects.add(newObj);
        System.out.println("Objekt erkannt: " + type);
    }

    private BufferedImage matToImage(Mat m) {
        int type = BufferedImage.TYPE_3BYTE_BGR;
        if (m.channels() == 1) type = BufferedImage.TYPE_BYTE_GRAY;
        int bufferSize = m.channels()*m.cols()*m.rows();
        byte[] b = new byte[bufferSize];
        m.get(0,0,b);
        BufferedImage image = new BufferedImage(m.cols(),m.rows(), type);
        final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(b, 0, targetPixels, 0, b.length);
        return image;
    }
}