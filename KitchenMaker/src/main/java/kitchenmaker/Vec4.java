package kitchenmaker;

/**
 * 4D-Vektor-Klasse für homogene Koordinaten in der 3D-Grafik.
 * Die w-Komponente wird für perspektivische Transformationen verwendet.
 */
public class Vec4 {
    public float x;
    public float y;
    public float z;
    public float w;

    /**
     * Erstellt einen Nullvektor (0, 0, 0, 0).
     */
    public Vec4() {
        this.x = 0;
        this.y = 0;
        this.z = 0;
        this.w = 0;
    }

    /**
     * Erstellt einen Vektor mit den angegebenen Komponenten.
     */
    public Vec4(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }

    /**
     * Erstellt eine Kopie des angegebenen Vektors.
     */
    public Vec4(Vec4 other) {
        this.x = other.x;
        this.y = other.y;
        this.z = other.z;
        this.w = other.w;
    }

    /**
     * Setzt die Komponenten dieses Vektors.
     * @return dieser Vektor für Methoden-Verkettung
     */
    public Vec4 set(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
        return this;
    }

    /**
     * Multipliziert diesen Vektor mit einer Matrix (Vektor * Matrix Multiplikation).
     * @param transformMatrix die Matrix für die Transformation
     * @return dieser Vektor für Methoden-Verkettung
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
     * Führt die perspektivische Division durch (teilt x, y, z durch w).
     * Dies konvertiert homogene Koordinaten zurück in kartesische Koordinaten.
     * @return dieser Vektor für Methoden-Verkettung
     */
    public Vec4 divideByW() {
        if (Math.abs(w) > 0.00001f) {
            float inverseW = 1.0f / w;
            this.x *= inverseW;
            this.y *= inverseW;
            this.z *= inverseW;
            this.w = 1.0f;
        }
        return this;
    }

    @Override
    public String toString() {
        return String.format("Vec4(%.2f, %.2f, %.2f, %.2f)", x, y, z, w);
    }
}

