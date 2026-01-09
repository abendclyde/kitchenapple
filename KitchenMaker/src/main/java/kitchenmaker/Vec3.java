package kitchenmaker;

/**
 * 3D-Vektor-Klasse für Position, Rotation und Richtung.
 * Verwendet mutable Felder für Performance bei 3D-Rendering.
 * Es kann mehrmals Vec3 genutzt werden wegen Constructor-Overloading.
 *
 * @author Niklas Puls
 */
public class Vec3 {
    public float x;
    public float y;
    public float z;

    /**
     * Erstellt einen Nullvektor (0, 0, 0).
     */
    public Vec3() {
        this.x = 0;
        this.y = 0;
        this.z = 0;
    }

    /**
     * Erstellt einen Vektor mit den angegebenen Komponenten.
     */
    public Vec3(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Erstellt eine Kopie des angegebenen Vektors.
     */
    public Vec3(Vec3 other) {
        this.x = other.x;
        this.y = other.y;
        this.z = other.z;
    }

    /**
     * Setzt die Komponenten dieses Vektors.
     * @return dieser Vektor für Methoden-Verkettung
     */
    public Vec3 set(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    /**
     * Kopiert die Werte des anderen Vektors in diesen Vektor.
     * @return dieser Vektor für Methoden-Verkettung
     */
    public Vec3 set(Vec3 other) {
        this.x = other.x;
        this.y = other.y;
        this.z = other.z;
        return this;
    }

    /**
     * Setzt alle Komponenten auf den gleichen Wert.
     * @return dieser Vektor für Methoden-Verkettung
     */
    public Vec3 set(float value) {
        this.x = value;
        this.y = value;
        this.z = value;
        return this;
    }

    /**
     * Addiert einen anderen Vektor zu diesem Vektor.
     * @return dieser Vektor für Methoden-Verkettung
     */
    public Vec3 add(Vec3 other) {
        this.x += other.x;
        this.y += other.y;
        this.z += other.z;
        return this;
    }

    /**
     * Addiert Werte zu den Komponenten dieses Vektors.
     * @return dieser Vektor für Methoden-Verkettung
     */
    public Vec3 add(float dx, float dy, float dz) {
        this.x += dx;
        this.y += dy;
        this.z += dz;
        return this;
    }

    /**
     * Subtrahiert einen anderen Vektor von diesem Vektor.
     * @return dieser Vektor für Methoden-Verkettung
     */
    public Vec3 subtract(Vec3 other) {
        this.x -= other.x;
        this.y -= other.y;
        this.z -= other.z;
        return this;
    }

    /**
     * Subtrahiert Werte von den Komponenten dieses Vektors.
     * @return dieser Vektor für Methoden-Verkettung
     */
    public Vec3 subtract(float dx, float dy, float dz) {
        this.x -= dx;
        this.y -= dy;
        this.z -= dz;
        return this;
    }

    /**
     * Multipliziert diesen Vektor mit einem Skalar.
     * @return dieser Vektor für Methoden-Verkettung
     */
    public Vec3 multiply(float scalar) {
        this.x *= scalar;
        this.y *= scalar;
        this.z *= scalar;
        return this;
    }

    /**
     * Multipliziert die Komponenten dieses Vektors komponentenweise mit einem anderen Vektor.
     * @return dieser Vektor für Methoden-Verkettung
     */
    public Vec3 multiply(Vec3 other) {
        this.x *= other.x;
        this.y *= other.y;
        this.z *= other.z;
        return this;
    }

    /**
     * Berechnet die Länge (Magnitude) dieses Vektors.
     */
    public float length() {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    /**
     * Berechnet die quadrierte Länge dieses Vektors (effizienter als length()).
     */
    public float lengthSquared() {
        return x * x + y * y + z * z;
    }

    /**
     * Normalisiert diesen Vektor (macht ihn zur Länge 1).
     * Schützt gegen sehr kleine Längen, um numerische Instabilitäten zu vermeiden.
     * @return dieser Vektor für Methoden-Verkettung
     */
    public Vec3 normalize() {
        float vectorLength = length();
        if (vectorLength > 0.00001f) {
            float inverseLengt = 1.0f / vectorLength;
            this.x *= inverseLengt;
            this.y *= inverseLengt;
            this.z *= inverseLengt;
        }
        return this;
    }

    /**
     * Berechnet das Skalarprodukt (Dot Product) mit einem anderen Vektor.
     */
    public float dot(Vec3 other) {
        return this.x * other.x + this.y * other.y + this.z * other.z;
    }

    /**
     * Berechnet das Kreuzprodukt (Cross Product) mit einem anderen Vektor und speichert das Ergebnis in diesem Vektor.
     * @return dieser Vektor für Methoden-Verkettung
     */
    public Vec3 cross(Vec3 other) {
        float resultX = this.y * other.z - this.z * other.y;
        float resultY = this.z * other.x - this.x * other.z;
        float resultZ = this.x * other.y - this.y * other.x;
        this.x = resultX;
        this.y = resultY;
        this.z = resultZ;
        return this;
    }

    /**
     * Berechnet den Abstand zu einem anderen Vektor.
     */
    public float distance(Vec3 other) {
        float dx = this.x - other.x;
        float dy = this.y - other.y;
        float dz = this.z - other.z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Override
    public String toString() {
        return String.format("Vec3(%.2f, %.2f, %.2f)", x, y, z);
    }
}
