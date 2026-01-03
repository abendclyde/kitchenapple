package shapeviewer;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * ShapeDetector - Erkennt primitive Formen (Dreieck, Kreis, Viereck) in verschiedenen Farben.
 * Verwendet OpenCV für HSV-Farbfilterung und Konturerkennung.
 */
public class ShapeDetector {

    // Erkannte Form mit Typ, Farbe und Kontur
    public static class DetectedShape {
        public enum ShapeType { TRIANGLE, CIRCLE, RECTANGLE, UNKNOWN }
        public enum ColorType { RED, GREEN, BLUE, UNKNOWN }

        public ShapeType shapeType;
        public ColorType colorType;
        public MatOfPoint contour;
        public Point center;
        public double area;

        public DetectedShape(ShapeType shapeType, ColorType colorType, MatOfPoint contour, Point center, double area) {
            this.shapeType = shapeType;
            this.colorType = colorType;
            this.contour = contour;
            this.center = center;
            this.area = area;
        }

        public String getShapeName() {
            return switch (shapeType) {
                case TRIANGLE -> "Dreieck";
                case CIRCLE -> "Kreis";
                case RECTANGLE -> "Viereck";
                default -> "Unbekannt";
            };
        }

        public String getColorName() {
            return switch (colorType) {
                case RED -> "Rot";
                case GREEN -> "Grün";
                case BLUE -> "Blau";
                default -> "Unbekannt";
            };
        }

        public String get3DObjectName() {
            if (colorType == ColorType.RED && shapeType == ShapeType.TRIANGLE) {
                return "Pyramide";
            } else if (colorType == ColorType.GREEN && shapeType == ShapeType.CIRCLE) {
                return "Kugel";
            } else if (colorType == ColorType.BLUE && shapeType == ShapeType.RECTANGLE) {
                return "Würfel";
            }
            return null;
        }

        public Scalar getDrawColor() {
            return switch (colorType) {
                case RED -> new Scalar(0, 0, 255);    // BGR für Rot
                case GREEN -> new Scalar(0, 255, 0);  // BGR für Grün
                case BLUE -> new Scalar(255, 0, 0);   // BGR für Blau
                default -> new Scalar(255, 255, 255);
            };
        }
    }

    // HSV-Farbbereiche - Höhere Sättigung für kräftige Farben (Marker/Druck auf Papier)
    // Rot hat zwei Bereiche (um 0° und um 180°)
    private static final Scalar RED_LOW1 = new Scalar(0, 150, 100);
    private static final Scalar RED_HIGH1 = new Scalar(10, 255, 255);
    private static final Scalar RED_LOW2 = new Scalar(160, 150, 100);
    private static final Scalar RED_HIGH2 = new Scalar(180, 255, 255);

    // Grün - höhere Sättigung
    private static final Scalar GREEN_LOW = new Scalar(35, 120, 80);
    private static final Scalar GREEN_HIGH = new Scalar(85, 255, 255);

    // Blau - höhere Sättigung
    private static final Scalar BLUE_LOW = new Scalar(100, 150, 80);
    private static final Scalar BLUE_HIGH = new Scalar(130, 255, 255);

    // Minimale Fläche für erkannte Konturen (erhöht für Papier-Erkennung)
    private static final double MIN_CONTOUR_AREA = 5000;

    // Minimaler Anteil der Form am Gesamtbild (2% des Bildes)
    private static final double MIN_AREA_RATIO = 0.02;

    // Striktere Formqualität - Form muss "solide" sein (wenig Löcher/Einbuchtungen)
    private static final double MIN_SOLIDITY = 0.8;

    /**
     * Erkennt Formen im gegebenen Frame und zeichnet Umrandungen.
     * @param frame Das Eingabebild (BGR-Format)
     * @return Liste der erkannten Formen
     */
    public List<DetectedShape> detectShapes(Mat frame) {
        List<DetectedShape> detectedShapes = new ArrayList<>();

        // Berechne minimale Fläche basierend auf Bildgröße
        double frameArea = frame.rows() * frame.cols();
        double minAreaForFrame = Math.max(MIN_CONTOUR_AREA, frameArea * MIN_AREA_RATIO);

        // Zu HSV konvertieren
        Mat hsv = new Mat();
        Imgproc.cvtColor(frame, hsv, Imgproc.COLOR_BGR2HSV);

        // Formen in jeder Farbe erkennen
        detectColoredShapes(hsv, frame, DetectedShape.ColorType.RED, detectedShapes, minAreaForFrame);
        detectColoredShapes(hsv, frame, DetectedShape.ColorType.GREEN, detectedShapes, minAreaForFrame);
        detectColoredShapes(hsv, frame, DetectedShape.ColorType.BLUE, detectedShapes, minAreaForFrame);

        hsv.release();
        return detectedShapes;
    }

    private void detectColoredShapes(Mat hsv, Mat frame, DetectedShape.ColorType colorType,
                                      List<DetectedShape> detectedShapes, double minAreaForFrame) {
        Mat mask = new Mat();

        // Farbmaske erstellen
        switch (colorType) {
            case RED -> {
                Mat mask1 = new Mat();
                Mat mask2 = new Mat();
                Core.inRange(hsv, RED_LOW1, RED_HIGH1, mask1);
                Core.inRange(hsv, RED_LOW2, RED_HIGH2, mask2);
                Core.add(mask1, mask2, mask);
                mask1.release();
                mask2.release();
            }
            case GREEN -> Core.inRange(hsv, GREEN_LOW, GREEN_HIGH, mask);
            case BLUE -> Core.inRange(hsv, BLUE_LOW, BLUE_HIGH, mask);
            default -> { return; }
        }

        // Stärkere morphologische Operationen zum Bereinigen von Rauschen
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(7, 7));
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel);
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel);
        kernel.release();

        // Konturen finden
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        hierarchy.release();

        // Nach Fläche sortieren (größte zuerst) und nur die größte Form pro Farbe verarbeiten
        contours.sort((c1, c2) -> Double.compare(Imgproc.contourArea(c2), Imgproc.contourArea(c1)));

        int shapesFound = 0;
        for (MatOfPoint contour : contours) {
            if (shapesFound >= 1) break; // Nur eine Form pro Farbe

            double area = Imgproc.contourArea(contour);

            // Fläche muss groß genug sein
            if (area < minAreaForFrame) continue;

            // Soliditätsprüfung: Verhältnis von Konturfläche zu konvexer Hüllfläche
            MatOfInt hullIndices = new MatOfInt();
            Imgproc.convexHull(contour, hullIndices);

            // Konvexe Hülle als Punkte erstellen
            Point[] contourPoints = contour.toArray();
            int[] hullIndexArray = hullIndices.toArray();
            if (hullIndexArray.length < 3) {
                hullIndices.release();
                continue;
            }
            Point[] hullPoints = new Point[hullIndexArray.length];
            for (int i = 0; i < hullIndexArray.length; i++) {
                hullPoints[i] = contourPoints[hullIndexArray[i]];
            }
            MatOfPoint hull = new MatOfPoint(hullPoints);
            double hullArea = Imgproc.contourArea(hull);
            double solidity = (hullArea > 0) ? area / hullArea : 0;
            hull.release();
            hullIndices.release();

            if (solidity < MIN_SOLIDITY) continue; // Form ist nicht solide genug

            // Polygon-Approximation
            MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
            double perimeter = Imgproc.arcLength(contour2f, true);
            MatOfPoint2f approx = new MatOfPoint2f();
            Imgproc.approxPolyDP(contour2f, approx, 0.04 * perimeter, true);

            int vertices = (int) approx.total();
            DetectedShape.ShapeType shapeType = classifyShape(vertices, contour, area);

            // Nur bekannte Formen akzeptieren (keine UNKNOWN)
            if (shapeType == DetectedShape.ShapeType.UNKNOWN) {
                contour2f.release();
                approx.release();
                continue;
            }

            // Mittelpunkt berechnen
            Moments moments = Imgproc.moments(contour);
            if (moments.get_m00() == 0) {
                contour2f.release();
                approx.release();
                continue;
            }
            Point center = new Point(moments.get_m10() / moments.get_m00(), moments.get_m01() / moments.get_m00());

            DetectedShape shape = new DetectedShape(shapeType, colorType, contour, center, area);
            detectedShapes.add(shape);
            shapesFound++;

            // Umrandung zeichnen
            drawShapeOutline(frame, shape);

            contour2f.release();
            approx.release();
        }

        mask.release();
    }

    private DetectedShape.ShapeType classifyShape(int vertices, MatOfPoint contour, double area) {
        if (vertices == 3) {
            // Prüfen ob es ein gültiges Dreieck ist (alle Winkel sollten vernünftig sein)
            return DetectedShape.ShapeType.TRIANGLE;
        } else if (vertices == 4) {
            // Prüfen ob es ein Rechteck/Quadrat ist
            Rect boundingRect = Imgproc.boundingRect(contour);
            double aspectRatio = (double) boundingRect.width / boundingRect.height;

            // Prüfen wie gut die Kontur das Bounding-Rect ausfüllt
            double rectArea = boundingRect.width * boundingRect.height;
            double fillRatio = area / rectArea;

            // Muss das Rechteck gut ausfüllen (>75%) und annähernd quadratisch sein
            if (fillRatio > 0.75 && aspectRatio >= 0.5 && aspectRatio <= 2.0) {
                return DetectedShape.ShapeType.RECTANGLE;
            }
            return DetectedShape.ShapeType.UNKNOWN;
        } else if (vertices > 6) {
            // Kreisförmigkeit prüfen mit Circularity = 4π * Area / Perimeter²
            MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
            double perimeter = Imgproc.arcLength(contour2f, true);
            contour2f.release();

            double circularity = (4 * Math.PI * area) / (perimeter * perimeter);
            // Striktere Kreiserkennung (>0.75)
            if (circularity > 0.75) {
                return DetectedShape.ShapeType.CIRCLE;
            }
        }
        return DetectedShape.ShapeType.UNKNOWN;
    }

    private void drawShapeOutline(Mat frame, DetectedShape shape) {
        Scalar color = shape.getDrawColor();

        // Kontur zeichnen
        List<MatOfPoint> contourList = new ArrayList<>();
        contourList.add(shape.contour);
        Imgproc.drawContours(frame, contourList, 0, color, 3);

        // Beschriftung
        String label = shape.getColorName() + " " + shape.getShapeName();
        String object3D = shape.get3DObjectName();

        Point textPos = new Point(shape.center.x - 40, shape.center.y - 10);
        Imgproc.putText(frame, label, textPos, Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, color, 2);

        if (object3D != null) {
            Point objPos = new Point(shape.center.x - 30, shape.center.y + 15);
            Imgproc.putText(frame, "-> " + object3D, objPos, Imgproc.FONT_HERSHEY_SIMPLEX, 0.5,
                           new Scalar(255, 255, 255), 2);
        }

        // Bounding Box zeichnen
        Rect boundingRect = Imgproc.boundingRect(shape.contour);
        Imgproc.rectangle(frame, boundingRect, color, 2);
    }

    /**
     * Prüft ob eine erkannte Form einem gültigen 3D-Objekt entspricht.
     */
    public static boolean isValidMapping(DetectedShape shape) {
        return shape.get3DObjectName() != null;
    }
}

