package kitchenmaker;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * Die grafische Benutzeroberfläche des KitchenMaker-Editors.
 * Diese Klasse erweitert JFrame und stellt die visuelle Schnittstelle der Anwendung bereit.
 * Sie kümmert sich um die Anordnung der Swing-Komponenten (Toolbar, Seitenleiste, OpenGL-Canvas)
 * und implementiert die Interaktionslogik für Benutzereingaben, die nicht direkt das 3D-Rendering betreffen,
 * wie das Hinzufügen von Objekten, das Bearbeiten von Eigenschaften über Dialoge und die Webcam-Steuerung.
 *
 * @author Niklas Puls
 */
public class GUI extends JFrame {

    private final KitchenApp app;
    private final List<SceneData.Object3D> objects;
    private final RenderEngine renderer;
    private final DefaultListModel<SceneData.Object3D> listModel;
    private final JList<SceneData.Object3D> objectList;
    private final com.jogamp.opengl.awt.GLJPanel gljPanel;
    private final JLabel webcamLabel;

    /**
     * Standard-Animationsmodus für neu hinzugefügte Objekte.
     */
    private SceneData.AppearanceMode currentAppearanceMode = SceneData.AppearanceMode.FALL_DOWN;
    private float animationDurationSeconds = 0.8f;

    /**
     * Initialisiert das Hauptfenster und die GUI-Komponenten.
     */
    public GUI(KitchenApp app, List<SceneData.Object3D> objects, RenderEngine renderer,
               com.jogamp.opengl.awt.GLJPanel gljPanel, DefaultListModel<SceneData.Object3D> listModel,
               JList<SceneData.Object3D> objectList, JLabel webcamLabel) {
        super("KitchenMaker von Niklas Puls");
        this.app = app;
        this.objects = objects;
        this.renderer = renderer;
        this.gljPanel = gljPanel;
        this.listModel = listModel;
        this.objectList = objectList;
        this.webcamLabel = webcamLabel;

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1400, 900);
        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(Theme.BACKGROUND);

        // Konfiguration des Custom-Renderers und der Selektionslogik für die Objektliste
        objectList.setCellRenderer(new ObjectListCellRenderer());
        objectList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        objectList.setBackground(Theme.LIST_BACKGROUND);
        objectList.setForeground(Theme.TEXT_PRIMARY);

        // Synchronisation der Listenauswahl mit dem Renderer
        objectList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                renderer.selectedObject = objectList.getSelectedValue();
            }
        });

        // Doppelklick-Handler zum Öffnen des Bearbeitungsdialogs
        objectList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && objectList.getSelectedValue() != null) {
                    showEditDialog(objectList.getSelectedValue());
                }
            }
        });

        // Aufbau der Hauptstruktur: Toolbar oben, Viewport mittig, Sidebar links
        add(createToolBar(), BorderLayout.NORTH);
        add(gljPanel, BorderLayout.CENTER);
        add(createSidePanel(), BorderLayout.WEST);

        setLocationRelativeTo(null); // Zentriert das Fenster auf dem Bildschirm
    }

    /**
     * Konstruiert die Toolbar am oberen Rand.
     * Beinhaltet Buttons für Importieren und Hinzufügen von Objekten, Webcam-Steuerung so wie Anpassung der Objekt-Erscheinungsanimation
     */
    private JToolBar createToolBar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false); // Fixierte Toolbar
        toolbar.setBorder(new EmptyBorder(6, 10, 6, 10));
        toolbar.setBackground(Theme.PANEL);

        // Erzeugung der Aktions-Buttons
        JButton importButton = createToolbarButton("icons/import.svg", "OBJ Importieren (Ctrl+O)");
        importButton.addActionListener(e -> app.importObjFile());

        JButton addButton = createToolbarButton("icons/plus.svg", "Objekt hinzufügen");
        addButton.addActionListener(e -> showAddObjectMenu(addButton));

        JButton editButton = createToolbarButton("icons/edit.svg", "Bearbeiten (Doppelklick)");
        editButton.addActionListener(e -> {
            if (renderer.selectedObject != null) showEditDialog(renderer.selectedObject);
        });

        JButton deleteButton = createToolbarButton("icons/delete.svg", "Löschen (Delete)");
        deleteButton.addActionListener(e -> app.deleteSelectedObject());

        toolbar.add(importButton);
        toolbar.add(addButton);
        toolbar.addSeparator(new Dimension(20, 0));
        toolbar.add(editButton);
        toolbar.add(deleteButton);
        toolbar.addSeparator(new Dimension(20, 0));

        JButton webcamButton = createToolbarButton("icons/camera.svg", "Webcam Start/Stop");
        webcamButton.addActionListener(e -> app.toggleWebcam());
        toolbar.add(webcamButton);

        toolbar.addSeparator(new Dimension(20, 0));

        // UI-Elemente zur Steuerung der Animationsart
        JLabel animLabel = new JLabel("Animation:");
        animLabel.setForeground(Theme.TEXT_LABEL);
        toolbar.add(animLabel);
        toolbar.add(Box.createHorizontalStrut(5));

        JComboBox<SceneData.AppearanceMode> animModeCombo = new JComboBox<>(SceneData.AppearanceMode.values());
        animModeCombo.setSelectedItem(currentAppearanceMode);
        animModeCombo.setMaximumSize(new Dimension(150, 30));
        animModeCombo.addActionListener(e -> currentAppearanceMode = (SceneData.AppearanceMode) animModeCombo.getSelectedItem());
        toolbar.add(animModeCombo);

        toolbar.add(Box.createHorizontalStrut(15));

        // Slider zur Steuerung der Animationsdauer
        JLabel durationLabel = new JLabel("Dauer:");
        durationLabel.setForeground(Theme.TEXT_LABEL);
        toolbar.add(durationLabel);
        toolbar.add(Box.createHorizontalStrut(5));

        JSlider durationSlider = new JSlider(0, 30, (int) (animationDurationSeconds * 10));
        durationSlider.setMaximumSize(new Dimension(100, 30));
        durationSlider.setToolTipText("Animationsdauer in Sekunden");

        JLabel durationValueLabel = new JLabel(String.format("%.1fs", animationDurationSeconds));
        durationValueLabel.setForeground(Theme.TEXT_LABEL);
        durationValueLabel.setPreferredSize(new Dimension(35, 20));

        durationSlider.addChangeListener(e -> {
            animationDurationSeconds = durationSlider.getValue() / 10.0f;
            durationValueLabel.setText(String.format("%.1fs", animationDurationSeconds));
        });
        toolbar.add(durationSlider);
        toolbar.add(Box.createHorizontalStrut(5));
        toolbar.add(durationValueLabel);

        // Titel
        toolbar.add(Box.createHorizontalGlue()); // Rechtsbündiger Titel
        JLabel titleLabel = new JLabel("KitchenMaker");
        titleLabel.setFont(Theme.TITLE);
        titleLabel.setForeground(Theme.TEXT_MUTED);
        toolbar.add(titleLabel);

        return toolbar;
    }

    /**
     * Erzeugt und zeigt ein Popup-Menü zur Auswahl vordefinierter Objekte an.
     * Wird aufgerufen, wenn der Button zum Hinzufügen betätigt wird.
     */
    private void showAddObjectMenu(JButton source) {
        JPopupMenu menu = new JPopupMenu();

        // Definition der verfügbaren Objekte: {Interner Typ, Anzeigename, Icon-Pfad}
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
            menu.add(createMenuItem(item[1], item[2], () -> app.addObjectByType(item[0]))); // Wenn geklickt wird, dann Objekt hinzufügen
        }

        menu.show(source, 0, source.getHeight());
    }

    /**
     * Hilfsmethode zur Erstellung eines Menüeintrags mit Icon.
     */
    private JMenuItem createMenuItem(String text, String iconPath, Runnable action) {
        JMenuItem item = new JMenuItem(text, new FlatSVGIcon(iconPath, 16, 16));
        item.addActionListener(e -> action.run());
        return item;
    }

    /**
     * Erstellt einen Toolbar-Button
     */
    private JButton createToolbarButton(String iconPath, String tooltip) {
        JButton button = new JButton(new FlatSVGIcon(iconPath, 20, 20));
        button.setToolTipText(tooltip);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(Theme.TOOLBAR_BUTTON);

        // MouseListener für visuelles Feedback beim Hovern
        button.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                button.setContentAreaFilled(true);
                button.setBackground(Theme.HOVER);
            }

            public void mouseExited(MouseEvent e) {
                button.setContentAreaFilled(false);
            }
        });

        return button;
    }

    /**
     * Erstellt die Sidebar.
     * Enthält die scrollbare Liste der Szenenobjekte sowie das Webcam-Vorschaufenster.
     */
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

        // Webcam-Bereich
        JPanel webcamPanel = new JPanel(new BorderLayout(0, 5));
        webcamPanel.setOpaque(false);

        JLabel webcamTitleLabel = new JLabel("Webcam & Formerkennung");
        webcamTitleLabel.setFont(Theme.LABEL);
        webcamTitleLabel.setForeground(Theme.TEXT_LABEL);

        JCheckBox shapeDetectionCheckBox = new JCheckBox("Formerkennung aktiv", app.isShapeDetection());
        shapeDetectionCheckBox.setFont(Theme.LABEL_SMALL);
        shapeDetectionCheckBox.setForeground(Theme.TEXT_LABEL);
        shapeDetectionCheckBox.setOpaque(false);
        shapeDetectionCheckBox.addActionListener(e -> app.setShapeDetection(shapeDetectionCheckBox.isSelected()));

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

    /**
     * Öffnet einen Dialog zum Bearbeiten der Eigenschaften des ausgewählten Objekts.
     * Ermöglicht die Änderung von Name, Position, Rotation und Farbe.
     * Bei Abbruch werden die Änderungen zurückgesetzt.
     */
    public void showEditDialog(SceneData.Object3D obj) {
        JDialog dialog = new JDialog(this, "Bearbeiten: " + obj.name, true);
        dialog.setSize(400, 320);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        // Sichern des ursprünglichen Zustands für Undo-Funktionalität bei Abbruch
        String originalName = obj.name;
        Vec3 originalPosition = new Vec3(obj.worldPosition);
        float originalRotationY = obj.rotationAngles.y;
        Vec3 originalColor = new Vec3(obj.color);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Namensfeld
        JPanel namePanel = createLabeledField("Name:");
        JTextField nameField = new JTextField(obj.name);
        nameField.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
                obj.name = nameField.getText();
                objectList.repaint(); // Aktualisiert die Liste sofort
            }
        });
        namePanel.add(nameField);
        content.add(namePanel);
        content.add(Box.createVerticalStrut(15));

        // Slider für Position und Rotation
        content.add(createSlider("Position X:", -100, 100, (int) (obj.worldPosition.x * 10),
                v -> {
                    obj.worldPosition.x = v / 10f;
                    gljPanel.repaint();
                }, "%.1f", 10f));
        content.add(createSlider("Position Y:", -100, 100, (int) (obj.worldPosition.y * 10),
                v -> {
                    obj.worldPosition.y = v / 10f;
                    gljPanel.repaint();
                }, "%.1f", 10f));
        content.add(createSlider("Position Z:", -100, 100, (int) (obj.worldPosition.z * 10),
                v -> {
                    obj.worldPosition.z = v / 10f;
                    gljPanel.repaint();
                }, "%.1f", 10f));
        content.add(createSlider("Rotation Y:", 0, 360, (int) Math.toDegrees(obj.rotationAngles.y),
                v -> {
                    obj.rotationAngles.y = (float) Math.toRadians(v);
                    gljPanel.repaint();
                }, "%d°", 1f));

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
                gljPanel.repaint();
            }
        });
        colorPanel.add(colorButton);
        content.add(colorPanel);

        // Dialog-Buttons (OK / Abbrechen)
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelButton = new JButton("Abbrechen");
        cancelButton.addActionListener(e -> {
            // Wiederherstellung des Ursprungszustands
            obj.name = originalName;
            obj.worldPosition.set(originalPosition);
            obj.rotationAngles.y = originalRotationY;
            obj.color.set(originalColor);
            objectList.repaint();
            gljPanel.repaint();
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

    /**
     * Erstellt ein Panel mit einem JSlider, Label und numerischer Anzeige.
     * Gleichzeitig auch Hilfsmethode für Edit-Dialog.
     */
    private JPanel createSlider(String label, int min, int max, int value,
                                java.util.function.IntConsumer onChange, String format, float divisor) {
        JPanel panel = createLabeledField(label);
        JSlider slider = new JSlider(min, max, value);

        // Berechnung des Anzeigewertes
        Object initialValue = divisor == 1f ? value : (Object) (value / divisor);
        JLabel valueLabel = new JLabel(String.format(format, initialValue));
        valueLabel.setPreferredSize(Theme.VALUE_LABEL);

        slider.addChangeListener(e -> {
            onChange.accept(slider.getValue());
            Object displayValue = divisor == 1f ? slider.getValue() : (Object) (slider.getValue() / divisor);
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

    public SceneData.AppearanceMode getCurrentAppearanceMode() {
        return currentAppearanceMode;
    }

    public float getAnimationDurationSeconds() {
        return animationDurationSeconds;
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

    /**
     * Darstellung eines farbigen Vierecks neben dem Objektnamen in der Liste.
     */
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
            // Aktivierung von Antialiasing für glatte Kanten
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillRoundRect(x, y, width, height, 4, 4);
            g2.setColor(Theme.ICON_BORDER_LIGHT);
            g2.drawRoundRect(x, y, width - 1, height - 1, 4, 4);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return width;
        }

        @Override
        public int getIconHeight() {
            return height;
        }
    }
}