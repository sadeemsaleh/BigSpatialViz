package util.render;

import util.UnsignedByte;

public class MeanSquaredError implements IErrorMetric {

    /**
     * Compute mean squared error between two renderings
     *
     * - Use 1-D array to simulate a 3-D array
     *   suppose 3-D array has dimension lengths: side * side * 3
     *   [i][j][k] = i * side * 3 + j * 3 + k
     *
     * @param _rendering1
     * @param _rendering2
     * @param _resolution
     * @return
     */
    @Override
    public double totalError(byte[] _rendering1, byte[] _rendering2, int _resolution) {
        int side = _resolution;
        // compute squared error between pixels with gray scaling
        double error = 0.0;
        for (int i = 0; i < side; i++) {
            for (int j = 0; j < side; j++) {
                int r1 = UnsignedByte.toInt(_rendering1[i * side * 3 + j * 3 + 0]);
                int g1 = UnsignedByte.toInt(_rendering1[i * side * 3 + j * 3 + 1]);
                int b1 = UnsignedByte.toInt(_rendering1[i * side * 3 + j * 3 + 2]);
                // gray scaling formula = (0.3 * R) + (0.59 * G) + (0.11 * B)
                int gray1 = (int) ((0.3 * r1) + (0.59 * g1) + (0.11 * b1));
                int r2 = UnsignedByte.toInt(_rendering2[i * side * 3 + j * 3 + 0]);
                int g2 = UnsignedByte.toInt(_rendering2[i * side * 3 + j * 3 + 1]);
                int b2 = UnsignedByte.toInt(_rendering2[i * side * 3 + j * 3 + 2]);
                int gray2 = (int) ((0.3 * r2) + (0.59 * g2) + (0.11 * b2));
                error += (gray1 - gray2) * (gray1 - gray2);
            }
        }
        return error;
    }
}
