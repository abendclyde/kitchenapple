package shapeviewer;

import com.jogamp.opengl.awt.GLCanvas;
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

public class ShapeViewerApp extends JFrame {

    private final List<SceneData.Object3D> objects = Collections.synchronizedList(new ArrayList<>());
    private final RenderEngine renderer;
    private final GLCanvas glCanvas;

    // UI Controls
    private JSlider sldX, sldY, sldRotY;
    private JLabel lblWebcam;
    private boolean webcamRunning = false;
    private Thread webcamThread;

    public static void main(String[] args) {
        try { nu.pattern.OpenCV.loadLocally(); } catch (Throwable t) {}

        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            new ShapeViewerApp();
        } else {
            SwingUtilities.invokeLater(ShapeViewerApp::new);
        }
    }

    public ShapeViewerApp() {
        super("ShapeViewer3D - Cleaned");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLayout(new BorderLayout());

        System.out.println("[INFO] Initialisiere OpenGL...");
        renderer = new RenderEngine(objects);
        glCanvas = new GLCanvas();
        glCanvas.addGLEventListener(renderer);

        add(glCanvas, BorderLayout.CENTER);
        add(createControlPanel(), BorderLayout.EAST);

        objects.add(SceneData.createPyramid());
        if (!objects.isEmpty()) {
            renderer.selectedObject = objects.get(0);
            updateSliders();
        }

        setupInteraction();
        setLocationRelativeTo(null);
        setVisible(true);

        new FPSAnimator(glCanvas, 60).start();
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
                    renderer.selectedObject = obj;
                    updateSliders();
                }
            }
        });

        JButton btnDelete = new JButton("Löschen");
        btnDelete.addActionListener(e -> {
            if (renderer.selectedObject != null) {
                objects.remove(renderer.selectedObject);
                renderer.selectedObject = objects.isEmpty() ? null : objects.get(0);
                updateSliders();
            }
        });

        JButton btnWebcam = new JButton("Webcam Start/Stop");
        btnWebcam.addActionListener(e -> toggleWebcam());

        lblWebcam = new JLabel("Webcam aus");
        lblWebcam.setPreferredSize(new Dimension(200, 150));
        lblWebcam.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        lblWebcam.setAlignmentX(Component.CENTER_ALIGNMENT);

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
        sldX.addChangeListener(cl); sldY.addChangeListener(cl); sldRotY.addChangeListener(cl);

        addComponent(panel, new JLabel("Aktionen"), btnImport, Box.createVerticalStrut(5), btnDelete,
                Box.createVerticalStrut(15), new JLabel("Position X / Y"), sldX, sldY,
                new JLabel("Rotation Y"), sldRotY, Box.createVerticalStrut(20), btnWebcam, lblWebcam);

        return panel;
    }

    private void addComponent(JPanel p, Component... comps) {
        for(Component c : comps) p.add(c);
    }

    private int lastMouseX, lastMouseY, pressedMouseX, pressedMouseY;
    private boolean isDragging = false;
    private static final int DRAG_THRESHOLD = 5;

    private void setupInteraction() {
        glCanvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                pressedMouseX = lastMouseX = e.getX();
                pressedMouseY = lastMouseY = e.getY();
                isDragging = false;
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                int dx = Math.abs(e.getX() - pressedMouseX);
                int dy = Math.abs(e.getY() - pressedMouseY);
                if ((dx <= DRAG_THRESHOLD && dy <= DRAG_THRESHOLD) && SwingUtilities.isLeftMouseButton(e) && !objects.isEmpty()) {
                    int idx = objects.indexOf(renderer.selectedObject);
                    // Nächstes Objekt oder erstes, falls keins gefunden
                    renderer.selectedObject = objects.get((idx + 1) % objects.size());
                    System.out.println("Objekt ausgewählt: " + renderer.selectedObject.name);
                    updateSliders();
                }
                isDragging = false;
            }
        });

        glCanvas.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (Math.abs(e.getX() - pressedMouseX) > DRAG_THRESHOLD || Math.abs(e.getY() - pressedMouseY) > DRAG_THRESHOLD)
                    isDragging = true;

                if (isDragging) {
                    renderer.cameraYaw += (e.getX() - lastMouseX) * 0.5f;
                    renderer.cameraPitch = Math.max(-85f, Math.min(85f, renderer.cameraPitch + (e.getY() - lastMouseY) * 0.5f));
                    lastMouseX = e.getX(); lastMouseY = e.getY();
                    glCanvas.repaint();
                }
            }
        });

        glCanvas.addMouseWheelListener(e -> {
            renderer.cameraDistance = Math.max(1f, Math.min(50f, renderer.cameraDistance + (float)e.getPreciseWheelRotation() * 0.5f));
        });
    }

    private void updateSliders() {
        if (renderer.selectedObject != null) {
            sldX.setValue((int) (renderer.selectedObject.position.x * 10));
            sldY.setValue((int) (renderer.selectedObject.position.y * 10));
            sldRotY.setValue((int) Math.toDegrees(renderer.selectedObject.rotation.y));
        }
    }

    private void toggleWebcam() {
        if (webcamRunning) webcamRunning = false;
        else {
            webcamRunning = true;
            webcamThread = new Thread(this::webcamLoop);
            webcamThread.start();
        }
    }

    private void webcamLoop() {
        VideoCapture capture = new VideoCapture(0);
        if (!capture.isOpened()) capture = new VideoCapture(1);

        if (!capture.isOpened()) {
            System.err.println("Keine Webcam gefunden.");
            webcamRunning = false; return;
        }

        Mat frame = new Mat();
        while (webcamRunning) {
            if (capture.read(frame) && !frame.empty()) {
                BufferedImage img = matToImage(frame);
                SwingUtilities.invokeLater(() -> lblWebcam.setIcon(new ImageIcon(img)));
            }
            try { Thread.sleep(50); } catch (InterruptedException e) {}
        }
        capture.release();
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