package kitchenmaker;

/**
 * Mathematische Vektorklasse für 3D-Koordinaten und Richtungen.
 *
 * @author Niklas Puls
 */
public class Vec3 {

    public float x, y, z;

    /**
     * Standardkonstruktor (0,0,0).
     */
    public Vec3() {
        this(0, 0, 0);
    }

    /**
     * Konstruktor mit expliziten Koordinaten.
     */
    public Vec3(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Copy-Konstruktor.
     */
    public Vec3(Vec3 other) {
        this.x = other.x;
        this.y = other.y;
        this.z = other.z;
    }

    /**
     * Setzt die Komponenten dieses Vektors neu.
     */
    public Vec3 set(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    /**
     * Setzt alle Komponenten auf denselben Wert.
     * (Wird für Initialisierungen wie Float.MAX_VALUE benötigt).
     */
    public Vec3 set(float value) {
        this.x = value;
        this.y = value;
        this.z = value;
        return this;
    }

    /**
     * Übernimmt die Werte eines anderen Vektors.
     */
    public Vec3 set(Vec3 other) {
        return set(other.x, other.y, other.z);
    }

    /**
     * Addiert einen anderen Vektor zu diesem (this += other).
     */
    public Vec3 add(Vec3 other) {
        this.x += other.x;
        this.y += other.y;
        this.z += other.z;
        return this;
    }

    /**
     * Addiert explizite Werte zu diesem Vektor (this += x, y, z).
     * (Wird für BoundingBox-Padding benötigt).
     */
    public Vec3 add(float x, float y, float z) {
        this.x += x;
        this.y += y;
        this.z += z;
        return this;
    }

    /**
     * Subtrahiert einen anderen Vektor von diesem (this -= other).
     */
    public Vec3 subtract(Vec3 other) {
        this.x -= other.x;
        this.y -= other.y;
        this.z -= other.z;
        return this;
    }

    /**
     * Subtrahiert explizite Werte (this -= x, y, z).
     */
    public Vec3 subtract(float x, float y, float z) {
        this.x -= x;
        this.y -= y;
        this.z -= z;
        return this;
    }

    /**
     * Skaliert den Vektor (Multiplikation mit Skalar).
     */
    public Vec3 multiply(float scalar) {
        this.x *= scalar;
        this.y *= scalar;
        this.z *= scalar;
        return this;
    }

    /**
     * Komponentenweise Multiplikation mit einem anderen Vektor.
     */
    public Vec3 multiply(Vec3 scale) {
        this.x *= scale.x;
        this.y *= scale.y;
        this.z *= scale.z;
        return this;
    }

    /**
     * Normalisiert den Vektor auf die Länge 1.
     */
    public Vec3 normalize() {
        float length = length();
        if (length > 0) {
            multiply(1.0f / length);
        }
        return this;
    }

    /**
     * Berechnet das Kreuzprodukt.
     */
    public Vec3 cross(Vec3 other) {
        float nx = this.y * other.z - this.z * other.y;
        float ny = this.z * other.x - this.x * other.z;
        float nz = this.x * other.y - this.y * other.x;
        return set(nx, ny, nz);
    }

    /**
     * Berechnet das Skalarprodukt (Dot Product).
     */
    public float dot(Vec3 other) {
        return this.x * other.x + this.y * other.y + this.z * other.z;
    }

    /**
     * Berechnet die Länge des Vektors.
     */
    public float length() {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    @Override
    public String toString() {
        return String.format("Vec3(%.2f, %.2f, %.2f)", x, y, z);
    }
}