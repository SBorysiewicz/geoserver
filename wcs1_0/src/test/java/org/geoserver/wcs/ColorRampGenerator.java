/**
 * 
 */
package org.geoserver.wcs;

/**
 * @author Fabiania
 * 
 */
public class ColorRampGenerator {

    /**
     * @param args
     */
    public static void main(String[] args) {
        double min = 1.0E-4;
        double max = 1.0;

        int intervals = 250;

        String colorRampType = "hot-color-ramp";

        double res = (max - min) / intervals;

        System.out.println("<ColorMapEntry color=\"#000000\" quantity=\"" + (min - res)
                + "\" opacity=\"0.0\"/>");
        for (int c = 0; c <= intervals; c++) {
            double[] color = getColour(colorRampType, min + (c * res), min, max);

            String r = Integer.toHexString((int) Math.round(255.0 * color[0]));
            String g = Integer.toHexString((int) Math.round(255.0 * color[1]));
            String b = Integer.toHexString((int) Math.round(255.0 * color[2]));
            String hexColor = (r.length() == 2 ? r : "0" + r) + (g.length() == 2 ? g : "0" + g)
                    + (b.length() == 2 ? b : "0" + b);

            System.out.println("<ColorMapEntry color=\"#" + hexColor + "\" quantity=\""
                    + (min + (c * res)) + "\"/>");
        }
        System.out.println("<ColorMapEntry color=\"#000000\" quantity=\"" + (max + res)
                + "\" opacity=\"0.0\"/>");
    }

    private static double[] getColour(String colorRampType, double v, double vmin, double vmax) {
        double[] c = new double[] { 1.0, 1.0, 1.0 }; // white

        if (colorRampType.equals("hot-color-ramp")) {
            c = getHotRampColor(v, vmin, vmax);
        }

        return c;
    }

    /**
     * Return a RGB colour value given a scalar v in the range [vmin,vmax] In this case each colour
     * component ranges from 0 (no contribution) to 1 (fully saturated), modifications for other
     * ranges is trivial. The colour is clipped at the end of the scales if v is outside the range
     * [vmin,vmax]
     */
    private static double[] getHotRampColor(double v, double vmin, double vmax) {
        double[] c = new double[] { 1.0, 1.0, 1.0 }; // white
        double dv;

        if (v < vmin)
            v = vmin;
        if (v > vmax)
            v = vmax;
        dv = vmax - vmin;

        if (v < (vmin + 0.25 * dv)) {
            c[0] = 0;
            c[1] = 4 * (v - vmin) / dv;
        } else if (v < (vmin + 0.5 * dv)) {
            c[0] = 0;
            c[2] = 1 + 4 * (vmin + 0.25 * dv - v) / dv;
        } else if (v < (vmin + 0.75 * dv)) {
            c[0] = 4 * (v - vmin - 0.5 * dv) / dv;
            c[2] = 0;
        } else {
            c[1] = 1 + 4 * (vmin + 0.75 * dv - v) / dv;
            c[2] = 0;
        }

        return c;
    }
}