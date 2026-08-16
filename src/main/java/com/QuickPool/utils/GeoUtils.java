package com.QuickPool.utils;

public class GeoUtils {

    private static final double EARTH_RADIUS_M = 6371000;

    public static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_M * c;
    }

    /** Approximate distance (meters) from point P to the straight line segment A-B.
     *  Flat-earth projection — accurate enough for city-scale distances. */
    public static double distancePointToSegmentMeters(
            double px, double py, double ax, double ay, double bx, double by) {

        double latRad = Math.toRadians((ax + bx) / 2);
        double mPerDegLat = 111320;
        double mPerDegLng = 111320 * Math.cos(latRad);

        double axm = 0, aym = 0;
        double bxm = (by - ay) * mPerDegLng, bym = (bx - ax) * mPerDegLat;
        double pxm = (py - ay) * mPerDegLng, pym = (px - ax) * mPerDegLat;

        double dx = bxm - axm, dy = bym - aym;
        double lenSq = dx * dx + dy * dy;
        double t = lenSq == 0 ? 0 : ((pxm - axm) * dx + (pym - aym) * dy) / lenSq;
        t = Math.max(0, Math.min(1, t));

        double projX = axm + t * dx, projY = aym + t * dy;
        double ddx = pxm - projX, ddy = pym - projY;
        return Math.sqrt(ddx * ddx + ddy * ddy);
    }
}
