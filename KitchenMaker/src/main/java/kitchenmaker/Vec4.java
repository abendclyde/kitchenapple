package kitchenmaker;

/**
 * Mathematische Vektorklasse für 4-dimensionale homogene Koordinaten.
 *
 * In der 3D-Computergrafik werden Vektoren oft um eine vierte Komponente (w) erweitert,
 * um affine Transformationen (wie Translationen) und projektive Transformationen
 * (wie die perspektivische Verzerrung) durch Matrixmultiplikation darstellen zu können.
 * Diese Klasse stellt die notwendigen Operationen für solche Transformationen und
 * die anschließende perspektivische Division bereit.
 *
 * @author Niklas Puls
 */
public class Vec4 {
    public float x;
    public float y;
    public float z;
    public float w;

    /**
     * Standardkonstruktor.
     * Initialisiert einen Nullvektor (0, 0, 0, 0).
     */
    public Vec4() {
        this.x = 0;
        this.y = 0;
        this.z = 0;
        this.w = 0;
    }

    /**
     * Konstruktor zur Initialisierung mit expliziten Komponentenwerten.
     * Für 3D-Punkte wird w üblicherweise auf 1.0 gesetzt, für Richtungsvektoren auf 0.0.
     */
    public Vec4(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }

    /**
     * Copy-Konstruktor.
     * Erstellt eine tiefe Kopie des übergebenen Vektors, um den Originalzustand zu bewahren.
     */
    public Vec4(Vec4 other) {
        this.x = other.x;
        this.y = other.y;
        this.z = other.z;
        this.w = other.w;
    }

    /**
     * Aktualisiert die Komponenten des Vektors.
     * Gibt die Referenz auf das aktuelle Objekt zurück, um Methodenverkettung (Chaining) zu ermöglichen.
     */
    public Vec4 set(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
        return this;
    }

    /**
     * Multipliziert diesen Vektor mit einer 4x4-Matrix (Transformation).
     * Die Operation erfolgt mathematisch als Spaltenvektor-Multiplikation (M * v).
     * Da die Matrix im Column-Major-Format vorliegt, werden die entsprechenden
     * Matrixelemente für die Linearkombination der Vektorkomponenten herangezogen.
     * Das Ergebnis überschreibt den aktuellen Vektorinhalt.
     */
    public Vec4 multiply(Mat4 transformMatrix) {
        float[] m = transformMatrix.matrixElements;
        float resultX = x * m[0] + y * m[4] + z * m[8] + w * m[12];
        float resultY = x * m[1] + y * m[5] + z * m[9] + w * m[13];
        float resultZ = x * m[2] + y * m[6] + z * m[10] + w * m[14];
        float resultW = x * m[3] + y * m[7] + z * m[11] + w * m[15];

        this.x = resultX;
        this.y = resultY;
        this.z = resultZ;
        this.w = resultW;
        return this;
    }

    /**
     * Führt die perspektivische Division durch.
     * Hierbei werden die x, y und z Komponenten durch w geteilt, um von homogenen Koordinaten
     * (Clip Space) zu kartesischen Koordinaten (Normalized Device Coordinates) zu gelangen.
     * Eine Division durch Null wird durch einen Schwellenwert-Check verhindert.
     */
    public Vec4 divideByW() {
        if (Math.abs(w) > 0.00001f) {
            float inverseW = 1.0f / w;
            this.x *= inverseW;
            this.y *= inverseW;
            this.z *= inverseW;
            this.w = 1.0f; // Nach der Division ist w normativ 1
        }
        return this;
    }

    @Override
    public String toString() {
        return String.format("Vec4(%.2f, %.2f, %.2f, %.2f)", x, y, z, w);
    }
}