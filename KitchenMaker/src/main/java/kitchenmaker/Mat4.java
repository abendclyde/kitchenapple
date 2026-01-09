package kitchenmaker;

/**
 * 4x4-Matrix-Klasse für 3D-Transformationen.
 * Die Matrix wird im Column-Major-Format gespeichert (OpenGL-Konvention).
 */
public class Mat4 {
    /**
     * Matrix-Elemente im Column-Major-Format:
     * m[0]  m[4]  m[8]  m[12]
     * m[1]  m[5]  m[9]  m[13]
     * m[2]  m[6]  m[10] m[14]
     * m[3]  m[7]  m[11] m[15]
     */
    public final float[] matrixElements = new float[16];

    /**
     * Erstellt eine Identitätsmatrix.
     */
    public Mat4() {
        setIdentity();
    }

    /**
     * Erstellt eine Kopie der angegebenen Matrix.
     */
    public Mat4(Mat4 other) {
        System.arraycopy(other.matrixElements, 0, this.matrixElements, 0, 16);
    }

    /**
     * Setzt diese Matrix auf die Identitätsmatrix.
     * @return diese Matrix für Methoden-Verkettung
     */
    public Mat4 setIdentity() {
        for (int i = 0; i < 16; i++) {
            matrixElements[i] = 0;
        }
        matrixElements[0] = 1;  // m[0][0]
        matrixElements[5] = 1;  // m[1][1]
        matrixElements[10] = 1; // m[2][2]
        matrixElements[15] = 1; // m[3][3]
        return this;
    }

    /**
     * Wendet eine Translation (Verschiebung) auf diese Matrix an.
     * @return diese Matrix für Methoden-Verkettung
     */
    public Mat4 translate(Vec3 translation) {
        return translate(translation.x, translation.y, translation.z);
    }

    /**
     * Wendet eine Translation (Verschiebung) auf diese Matrix an.
     * @return diese Matrix für Methoden-Verkettung
     */
    public Mat4 translate(float deltaX, float deltaY, float deltaZ) {
        float[] m = matrixElements;
        m[12] += m[0] * deltaX + m[4] * deltaY + m[8] * deltaZ;
        m[13] += m[1] * deltaX + m[5] * deltaY + m[9] * deltaZ;
        m[14] += m[2] * deltaX + m[6] * deltaY + m[10] * deltaZ;
        m[15] += m[3] * deltaX + m[7] * deltaY + m[11] * deltaZ;
        return this;
    }

    /**
     * Wendet eine Rotation um die X-Achse an.
     * @param angleInRadians Rotationswinkel in Radiant
     * @return diese Matrix für Methoden-Verkettung
     */
    public Mat4 rotateAroundX(float angleInRadians) {
        float cosAngle = (float) Math.cos(angleInRadians);
        float sinAngle = (float) Math.sin(angleInRadians);
        float[] m = matrixElements;

        float m4 = m[4], m5 = m[5], m6 = m[6], m7 = m[7];
        float m8 = m[8], m9 = m[9], m10 = m[10], m11 = m[11];

        m[4] = m4 * cosAngle + m8 * sinAngle;
        m[5] = m5 * cosAngle + m9 * sinAngle;
        m[6] = m6 * cosAngle + m10 * sinAngle;
        m[7] = m7 * cosAngle + m11 * sinAngle;

        m[8] = m8 * cosAngle - m4 * sinAngle;
        m[9] = m9 * cosAngle - m5 * sinAngle;
        m[10] = m10 * cosAngle - m6 * sinAngle;
        m[11] = m11 * cosAngle - m7 * sinAngle;

        return this;
    }

    /**
     * Wendet eine Rotation um die Y-Achse an.
     * @param angleInRadians Rotationswinkel in Radiant
     * @return diese Matrix für Methoden-Verkettung
     */
    public Mat4 rotateAroundY(float angleInRadians) {
        float cosAngle = (float) Math.cos(angleInRadians);
        float sinAngle = (float) Math.sin(angleInRadians);
        float[] m = matrixElements;

        float m0 = m[0], m1 = m[1], m2 = m[2], m3 = m[3];
        float m8 = m[8], m9 = m[9], m10 = m[10], m11 = m[11];

        m[0] = m0 * cosAngle - m8 * sinAngle;
        m[1] = m1 * cosAngle - m9 * sinAngle;
        m[2] = m2 * cosAngle - m10 * sinAngle;
        m[3] = m3 * cosAngle - m11 * sinAngle;

        m[8] = m0 * sinAngle + m8 * cosAngle;
        m[9] = m1 * sinAngle + m9 * cosAngle;
        m[10] = m2 * sinAngle + m10 * cosAngle;
        m[11] = m3 * sinAngle + m11 * cosAngle;

        return this;
    }

    /**
     * Wendet eine Rotation um die Z-Achse an.
     * @param angleInRadians Rotationswinkel in Radiant
     * @return diese Matrix für Methoden-Verkettung
     */
    public Mat4 rotateAroundZ(float angleInRadians) {
        float cosAngle = (float) Math.cos(angleInRadians);
        float sinAngle = (float) Math.sin(angleInRadians);
        float[] m = matrixElements;

        float m0 = m[0], m1 = m[1], m2 = m[2], m3 = m[3];
        float m4 = m[4], m5 = m[5], m6 = m[6], m7 = m[7];

        m[0] = m0 * cosAngle + m4 * sinAngle;
        m[1] = m1 * cosAngle + m5 * sinAngle;
        m[2] = m2 * cosAngle + m6 * sinAngle;
        m[3] = m3 * cosAngle + m7 * sinAngle;

        m[4] = m4 * cosAngle - m0 * sinAngle;
        m[5] = m5 * cosAngle - m1 * sinAngle;
        m[6] = m6 * cosAngle - m2 * sinAngle;
        m[7] = m7 * cosAngle - m3 * sinAngle;

        return this;
    }

    /**
     * Wendet eine Skalierung auf diese Matrix an.
     * @return diese Matrix für Methoden-Verkettung
     */
    public Mat4 scale(Vec3 scaleFactors) {
        return scale(scaleFactors.x, scaleFactors.y, scaleFactors.z);
    }

    /**
     * Wendet eine Skalierung auf diese Matrix an.
     * @return diese Matrix für Methoden-Verkettung
     */
    public Mat4 scale(float scaleX, float scaleY, float scaleZ) {
        float[] m = matrixElements;
        m[0] *= scaleX;
        m[1] *= scaleX;
        m[2] *= scaleX;
        m[3] *= scaleX;

        m[4] *= scaleY;
        m[5] *= scaleY;
        m[6] *= scaleY;
        m[7] *= scaleY;

        m[8] *= scaleZ;
        m[9] *= scaleZ;
        m[10] *= scaleZ;
        m[11] *= scaleZ;

        return this;
    }

    /**
     * Setzt diese Matrix auf eine Perspektiv-Projektionsmatrix.
     * @param fieldOfViewInRadians Sichtfeld in Radiant
     * @param aspectRatio Seitenverhältnis (Breite / Höhe)
     * @param nearPlane nahe Clipping-Ebene
     * @param farPlane ferne Clipping-Ebene
     * @return diese Matrix für Methoden-Verkettung
     */
    public Mat4 setPerspective(float fieldOfViewInRadians, float aspectRatio, float nearPlane, float farPlane) {
        float tanHalfFov = (float) Math.tan(fieldOfViewInRadians / 2.0f);
        float range = farPlane - nearPlane;

        for (int i = 0; i < 16; i++) {
            matrixElements[i] = 0;
        }

        matrixElements[0] = 1.0f / (aspectRatio * tanHalfFov);
        matrixElements[5] = 1.0f / tanHalfFov;
        matrixElements[10] = -(farPlane + nearPlane) / range;
        matrixElements[11] = -1.0f;
        matrixElements[14] = -(2.0f * farPlane * nearPlane) / range;

        return this;
    }

    /**
     * Setzt diese Matrix auf eine View-Matrix (Kamera-Transformation).
     * @param cameraPosition Position der Kamera
     * @param targetPosition Punkt, auf den die Kamera schaut
     * @param upDirection Aufwärtsrichtung der Kamera (normalerweise (0, 1, 0))
     * @return diese Matrix für Methoden-Verkettung
     */
    public Mat4 setLookAt(Vec3 cameraPosition, Vec3 targetPosition, Vec3 upDirection) {
        // Berechne Richtungsvektoren
        Vec3 forwardDirection = new Vec3(cameraPosition).subtract(targetPosition).normalize();
        Vec3 rightDirection = new Vec3(upDirection).cross(forwardDirection).normalize();
        Vec3 cameraUpDirection = new Vec3(forwardDirection).cross(rightDirection);

        float[] m = matrixElements;
        m[0] = rightDirection.x;
        m[4] = rightDirection.y;
        m[8] = rightDirection.z;
        m[12] = -rightDirection.dot(cameraPosition);

        m[1] = cameraUpDirection.x;
        m[5] = cameraUpDirection.y;
        m[9] = cameraUpDirection.z;
        m[13] = -cameraUpDirection.dot(cameraPosition);

        m[2] = forwardDirection.x;
        m[6] = forwardDirection.y;
        m[10] = forwardDirection.z;
        m[14] = -forwardDirection.dot(cameraPosition);

        m[3] = 0;
        m[7] = 0;
        m[11] = 0;
        m[15] = 1;

        return this;
    }

    /**
     * Multipliziert diese Matrix mit einer anderen Matrix (this = this * other).
     * @return diese Matrix für Methoden-Verkettung
     */
    public Mat4 multiplyMatrix(Mat4 other) {
        float[] result = new float[16];
        float[] a = this.matrixElements;
        float[] b = other.matrixElements;

        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                int index = column * 4 + row;
                result[index] =
                    a[row] * b[column * 4] +
                    a[row + 4] * b[column * 4 + 1] +
                    a[row + 8] * b[column * 4 + 2] +
                    a[row + 12] * b[column * 4 + 3];
            }
        }

        System.arraycopy(result, 0, matrixElements, 0, 16);
        return this;
    }

    /**
     * Invertiert diese Matrix (berechnet die inverse Matrix).
     * @return diese Matrix für Methoden-Verkettung
     */
    public Mat4 invertMatrix() {
        float[] m = matrixElements;
        float[] inv = new float[16];

        // Berechne die Inverse mittels Gauss-Jordan-Elimination
        inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] +
                 m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10];

        inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] -
                  m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10];

        inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] +
                 m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9];

        inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] -
                   m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9];

        inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] -
                  m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10];

        inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] +
                 m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10];

        inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] -
                  m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9];

        inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] +
                  m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9];

        inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] +
                 m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6];

        inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] -
                  m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6];

        inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] +
                  m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5];

        inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] -
                   m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5];

        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] -
                  m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6];

        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] +
                 m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6];

        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] -
                   m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5];

        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] +
                  m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5];

        float determinant = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12];

        if (Math.abs(determinant) < 0.00001f) {
            // Matrix ist nicht invertierbar, setze auf Identität
            setIdentity();
            return this;
        }

        float inverseDeterminant = 1.0f / determinant;
        for (int i = 0; i < 16; i++) {
            matrixElements[i] = inv[i] * inverseDeterminant;
        }

        return this;
    }

    /**
     * Gibt die Matrix-Elemente als float-Array zurück (für OpenGL).
     */
    public float[] toFloatArray() {
        return matrixElements;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Mat4:\n");
        for (int row = 0; row < 4; row++) {
            sb.append("  [");
            for (int col = 0; col < 4; col++) {
                sb.append(String.format("%8.3f", matrixElements[col * 4 + row]));
                if (col < 3) sb.append(", ");
            }
            sb.append("]\n");
        }
        return sb.toString();
    }
}

