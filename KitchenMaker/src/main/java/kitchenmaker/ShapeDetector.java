package kitchenmaker;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import java.util.ArrayList;
import java.util.List;

public class ShapeDetector {

    public static class DetectedShape {
        public enum ShapeType { TRIANGLE, CIRCLE, RECTANGLE, UNKNOWN }
        public enum ColorType { RED, GREEN, BLUE, UNKNOWN }

        public final ShapeType shapeType;
        public final ColorType colorType;
        public final MatOfPoint contour;
        public final Point center;
        public final double area;

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
            if (colorType == ColorType.RED && shapeType == ShapeType.TRIANGLE) return "Pyramide";
            if (colorType == ColorType.GREEN && shapeType == ShapeType.CIRCLE) return "Kugel";
            if (colorType == ColorType.BLUE && shapeType == ShapeType.RECTANGLE) return "Würfel";
            return null;
        }

        public Scalar getDrawColor() {
            return switch (colorType) {
                case RED -> new Scalar(0, 0, 255);
                case GREEN -> new Scalar(0, 255, 0);
                case BLUE -> new Scalar(255, 0, 0);
                default -> new Scalar(255, 255, 255);
            };
        }
    }

    // HSV Farbbereiche
    private static final Scalar RED_LOW1 = new Scalar(0, 150, 100), RED_HIGH1 = new Scalar(10, 255, 255);
    private static final Scalar RED_LOW2 = new Scalar(160, 150, 100), RED_HIGH2 = new Scalar(180, 255, 255);
    private static final Scalar GREEN_LOW = new Scalar(35, 120, 80), GREEN_HIGH = new Scalar(85, 255, 255);
    private static final Scalar BLUE_LOW = new Scalar(100, 150, 80), BLUE_HIGH = new Scalar(130, 255, 255);

    private static final double MIN_CONTOUR_AREA = 5000;
    private static final double MIN_AREA_RATIO = 0.02;
    private static final double MIN_SOLIDITY = 0.8;

    public List<DetectedShape> detectShapes(Mat frame) {
        List<DetectedShape> detectedShapes = new ArrayList<>();
        double minAreaForFrame = Math.max(MIN_CONTOUR_AREA, frame.rows() * frame.cols() * MIN_AREA_RATIO);

        Mat hsv = new Mat();
        Imgproc.cvtColor(frame, hsv, Imgproc.COLOR_BGR2HSV);

        for (DetectedShape.ColorType color : new DetectedShape.ColorType[]{
                DetectedShape.ColorType.RED, DetectedShape.ColorType.GREEN, DetectedShape.ColorType.BLUE}) {
            detectColoredShapes(hsv, frame, color, detectedShapes, minAreaForFrame);
        }

        hsv.release();
        return detectedShapes;
    }

    private void detectColoredShapes(Mat hsv, Mat frame, DetectedShape.ColorType colorType,
                                      List<DetectedShape> detectedShapes, double minAreaForFrame) {
        Mat mask = new Mat();

        switch (colorType) {
            case RED -> {
                Mat mask1 = new Mat(), mask2 = new Mat();
                Core.inRange(hsv, RED_LOW1, RED_HIGH1, mask1);
                Core.inRange(hsv, RED_LOW2, RED_HIGH2, mask2);
                Core.add(mask1, mask2, mask);
                mask1.release(); mask2.release();
            }
            case GREEN -> Core.inRange(hsv, GREEN_LOW, GREEN_HIGH, mask);
            case BLUE -> Core.inRange(hsv, BLUE_LOW, BLUE_HIGH, mask);
            default -> { mask.release(); return; }
        }

        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(7, 7));
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel);
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel);
        kernel.release();

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        hierarchy.release();

        contours.sort((c1, c2) -> Double.compare(Imgproc.contourArea(c2), Imgproc.contourArea(c1)));

        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            if (area < minAreaForFrame) continue;

            MatOfInt hullIndices = new MatOfInt();
            Imgproc.convexHull(contour, hullIndices);
            Point[] contourPoints = contour.toArray();
            int[] hullIndexArray = hullIndices.toArray();
            if (hullIndexArray.length < 3) { hullIndices.release(); continue; }

            Point[] hullPoints = new Point[hullIndexArray.length];
            for (int i = 0; i < hullIndexArray.length; i++) hullPoints[i] = contourPoints[hullIndexArray[i]];
            MatOfPoint hull = new MatOfPoint(hullPoints);
            double solidity = Imgproc.contourArea(hull) > 0 ? area / Imgproc.contourArea(hull) : 0;
            hull.release(); hullIndices.release();

            if (solidity < MIN_SOLIDITY) continue;

            MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
            double perimeter = Imgproc.arcLength(contour2f, true);
            MatOfPoint2f approx = new MatOfPoint2f();
            Imgproc.approxPolyDP(contour2f, approx, 0.04 * perimeter, true);

            DetectedShape.ShapeType shapeType = classifyShape((int) approx.total(), contour, area);
            contour2f.release(); approx.release();

            if (shapeType == DetectedShape.ShapeType.UNKNOWN) continue;

            Moments moments = Imgproc.moments(contour);
            if (moments.get_m00() == 0) continue;
            Point center = new Point(moments.get_m10() / moments.get_m00(), moments.get_m01() / moments.get_m00());

            detectedShapes.add(new DetectedShape(shapeType, colorType, contour, center, area));
            drawShapeOutline(frame, new DetectedShape(shapeType, colorType, contour, center, area));
            break; // Nur eine Form pro Farbe
        }

        mask.release();
    }

    private DetectedShape.ShapeType classifyShape(int vertices, MatOfPoint contour, double area) {
        if (vertices == 3) {
            return DetectedShape.ShapeType.TRIANGLE;
        } else if (vertices == 4) {
            Rect boundingRect = Imgproc.boundingRect(contour);
            double aspectRatio = (double) boundingRect.width / boundingRect.height;
            double fillRatio = area / (boundingRect.width * boundingRect.height);
            if (fillRatio > 0.75 && aspectRatio >= 0.5 && aspectRatio <= 2.0) {
                return DetectedShape.ShapeType.RECTANGLE;
            }
        } else if (vertices > 6) {
            MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
            double perimeter = Imgproc.arcLength(contour2f, true);
            contour2f.release();
            double circularity = (4 * Math.PI * area) / (perimeter * perimeter);
            if (circularity > 0.75) return DetectedShape.ShapeType.CIRCLE;
        }
        return DetectedShape.ShapeType.UNKNOWN;
    }

    private void drawShapeOutline(Mat frame, DetectedShape shape) {
        Scalar color = shape.getDrawColor();

        Imgproc.drawContours(frame, List.of(shape.contour), 0, color, 3);

        Point textPos = new Point(shape.center.x - 40, shape.center.y - 10);
        Imgproc.putText(frame, shape.getColorName() + " " + shape.getShapeName(), textPos,
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, color, 2);

        String object3D = shape.get3DObjectName();
        if (object3D != null) {
            Imgproc.putText(frame, "-> " + object3D, new Point(shape.center.x - 30, shape.center.y + 15),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(255, 255, 255), 2);
        }

        Imgproc.rectangle(frame, Imgproc.boundingRect(shape.contour), color, 2);
    }

    public static boolean isValidMapping(DetectedShape shape) {
        return shape.get3DObjectName() != null;
    }
}
