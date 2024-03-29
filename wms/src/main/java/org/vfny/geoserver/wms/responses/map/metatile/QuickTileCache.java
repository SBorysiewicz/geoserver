/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.vfny.geoserver.wms.responses.map.metatile;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.image.RenderedImage;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.geoserver.config.ConfigurationListenerAdapter;
import org.geoserver.config.GeoServer;
import org.geoserver.config.GeoServerInfo;
import org.geoserver.config.ServiceInfo;
import org.geoserver.wfs.TransactionEvent;
import org.geoserver.wfs.TransactionListener;
import org.geoserver.wfs.WFSException;
import org.geotools.util.CanonicalSet;
import org.geotools.util.SoftValueHashMap;
import org.vfny.geoserver.wms.requests.GetMapRequest;

import com.vividsolutions.jts.geom.Envelope;

public class QuickTileCache implements TransactionListener {
    /**
     * Set of parameters that we can ignore, since they do not define a map, are either unrelated,
     * or define the tiling instead
     */
    private static final Set ignoredParameters;

    static {
        ignoredParameters = new HashSet();
        ignoredParameters.add("REQUEST");
        ignoredParameters.add("TILED");
        ignoredParameters.add("BBOX");
        ignoredParameters.add("WIDTH");
        ignoredParameters.add("HEIGHT");
        ignoredParameters.add("SERVICE");
        ignoredParameters.add("VERSION");
        ignoredParameters.add("EXCEPTIONS");
    }

    /**
     * Canonicalizer used to return the same object when two threads ask for the same meta-tile
     */
    private CanonicalSet<MetaTileKey> metaTileKeys = CanonicalSet.newInstance(MetaTileKey.class);

    private SoftValueHashMap tileCache = new SoftValueHashMap(0);

    public QuickTileCache(GeoServer geoServer) {
        geoServer.addListener(new ConfigurationListenerAdapter() {
            public void handleGlobalChange(GeoServerInfo global, List<String> propertyNames,
                    List<Object> oldValues, List<Object> newValues) {
                tileCache.clear();
            }
            public void handleServiceChange(ServiceInfo service, List<String> propertyNames,
                    List<Object> oldValues, List<Object> newValues) {
                tileCache.clear();
            }
            public void reloaded() {
                tileCache.clear();
            }        
        });
    }

    /**
     * For testing only
     */
    QuickTileCache() {
    }

    /**
     * Given a tiled request, builds a key that can be used to access the cache looking for a
     * specific meta-tile, and also as a synchronization tool to avoid multiple requests to trigger
     * parallel computation of the same meta-tile
     * 
     * @param request
     * @return
     */
    public MetaTileKey getMetaTileKey(GetMapRequest request) {
        String mapDefinition = buildMapDefinition(request.getHttpServletRequest());
        Envelope bbox = request.getBbox();
        Point2D origin = request.getTilesOrigin();
        MapKey mapKey = new MapKey(mapDefinition, normalize(bbox.getWidth() / request.getWidth()),
                origin);
        Point tileCoords = getTileCoordinates(bbox, origin);
        Point metaTileCoords = getMetaTileCoordinates(tileCoords);
        Envelope metaTileEnvelope = getMetaTileEnvelope(request, tileCoords, metaTileCoords);
        MetaTileKey key = new MetaTileKey(mapKey, metaTileCoords, metaTileEnvelope);

        // since this will be used for thread synchronization, we have to make
        // sure two thread asking for the same meta tile will get the same key
        // object
        return (MetaTileKey) metaTileKeys.unique(key);
    }

    private Envelope getMetaTileEnvelope(GetMapRequest request, Point tileCoords, Point metaTileCoords) {
        Envelope bbox = request.getBbox();
        double minx = bbox.getMinX() + (metaTileCoords.x - tileCoords.x) * bbox.getWidth(); 
        double miny = bbox.getMinY() + (metaTileCoords.y - tileCoords.y) * bbox.getHeight();
        double maxx = minx + bbox.getWidth() * 3;
        double maxy = miny + bbox.getHeight() * 3;
        return new Envelope(minx, maxx, miny, maxy);
    }

    /**
     * Given a tile, returns the coordinates of the meta-tile that contains it (where the meta-tile
     * coordinate is the coordinate of its lower left subtile)
     * 
     * @param tileCoords
     * @return
     */
    Point getMetaTileCoordinates(Point tileCoords) {
        int x = tileCoords.x;
        int y = tileCoords.y;
        int rx = x % 3;
        int ry = y % 3;
        int mtx = (rx == 0) ? x : ((x >= 0) ? (x - rx) : (x - 3 - rx));
        int mty = (ry == 0) ? y : ((y >= 0) ? (y - ry) : (y - 3 - ry));

        return new Point(mtx, mty);
    }

    /**
     * Given an envelope and origin, find the tile coordinate (row,col)
     * 
     * @param env
     * @param origin
     * @return
     */
    Point getTileCoordinates(Envelope env, Point2D origin) {
        // this was using the low left corner and Math.round, but turned
        // out to be fragile when fairly zoomed in. Using the tile center
        // and then flooring the division seems to work much more reliably.
        double centerx = env.getMinX() + env.getWidth() / 2;
        double centery = env.getMinY() + env.getHeight() / 2;
        int x = (int) Math.floor((centerx - origin.getX()) / env.getWidth());
        int y = (int) Math.floor((centery - origin.getY()) / env.getWidth());

        return new Point(x, y);
    }

    /**
     * This is tricky. We need to have doubles that can be compared by equality because resolution
     * and origin are doubles, and are part of a hashmap key, so we have to normalize them somehow,
     * in order to make the little differences disappear. Here we take the mantissa, which is made
     * of 52 bits, and throw away the 20 more significant ones, which means we're dealing with 12
     * significant decimal digits (2^40 -> more or less one billion million). See also <a
     * href="http://en.wikipedia.org/wiki/IEEE_754">IEEE 754</a> on Wikipedia.
     * 
     * @param d
     * @return
     */
    static double normalize(double d) {
        if (Double.isInfinite(d) || Double.isNaN(d)) {
            return d;
        }

        return Math.round(d * 10e6) / 10e6;
    }

    /**
     * Turns the request back into a sort of GET request (not url-encoded) for fast comparison
     * 
     * @param request
     * @return
     */
    private String buildMapDefinition(HttpServletRequest request) {
        StringBuffer sb = new StringBuffer();
        Enumeration en = request.getParameterNames();

        while (en.hasMoreElements()) {
            String paramName = (String) en.nextElement();

            if (ignoredParameters.contains(paramName.toUpperCase())) {
                continue;
            }

            // we don't have multi-valued parameters afaik, otherwise we would
            // have to use getParameterValues and deal with the returned array
            sb.append(paramName).append('=').append(request.getParameter(paramName));

            if (en.hasMoreElements()) {
                sb.append('&');
            }
        }

        return sb.toString();
    }

    /**
     * Key defining a tiling layer in a map
     */
    static class MapKey {
        String mapDefinition;

        double resolution;

        Point2D origin;

        public MapKey(String mapDefinition, double resolution, Point2D origin) {
            super();
            this.mapDefinition = mapDefinition;
            this.resolution = resolution;
            this.origin = origin;
        }

        public int hashCode() {
            return new HashCodeBuilder().append(mapDefinition).append(resolution)
                    .append(resolution).append(origin).toHashCode();
        }

        public boolean equals(Object obj) {
            if (!(obj instanceof MapKey)) {
                return false;
            }

            MapKey other = (MapKey) obj;

            return new EqualsBuilder().append(mapDefinition, other.mapDefinition).append(
                    resolution, other.resolution).append(origin, other.origin).isEquals();
        }

        public String toString() {
            return mapDefinition + "\nw:" + "\nresolution:" + resolution + "\norig:"
                    + origin.getX() + "," + origin.getY();
        }
    }

    /**
     * Key that identifies a certain meta-tile in a tiled map layer
     */
    static class MetaTileKey {
        MapKey mapKey;

        Point metaTileCoords;
        
        Envelope metaTileEnvelope;

        public MetaTileKey(MapKey mapKey, Point metaTileCoords, Envelope metaTileEnvelope) {
            super();
            this.mapKey = mapKey;
            this.metaTileCoords = metaTileCoords;
            this.metaTileEnvelope = metaTileEnvelope;
        }

        public Envelope getMetaTileEnvelope() {
//            This old code proved to be too much unstable, numerically wise, to be used
//            when very much zoomed in, so we moved to a local meta tile envelope computation
//            based on the requested tile bounds instead
//            double minx = mapKey.origin.getX() + (mapKey.resolution * 256 * metaTileCoords.x);
//            double miny = mapKey.origin.getY() + (mapKey.resolution * 256 * metaTileCoords.y);
//
//            return new Envelope(minx, minx + (mapKey.resolution * 256 * 3), miny, miny
//                    + (mapKey.resolution * 256 * 3));
            return metaTileEnvelope;
        }

        public int hashCode() {
            return new HashCodeBuilder().append(mapKey).append(metaTileCoords).toHashCode();
        }

        public boolean equals(Object obj) {
            if (!(obj instanceof MetaTileKey)) {
                return false;
            }

            MetaTileKey other = (MetaTileKey) obj;

            return new EqualsBuilder().append(mapKey, other.mapKey).append(metaTileCoords,
                    other.metaTileCoords).isEquals();
        }

        public int getMetaFactor() {
            return 3;
        }

        public int getTileSize() {
            return 256;
        }

        public String toString() {
            return mapKey + "\nmtc:" + metaTileCoords.x + "," + metaTileCoords.y;
        }
    }

    /**
     * Gathers a tile from the cache, if available
     * 
     * @param key
     * @param request
     * @return
     */
    public synchronized RenderedImage getTile(MetaTileKey key, GetMapRequest request) {
        CacheElement ce = (CacheElement) tileCache.get(key);

        if (ce == null) {
            return null;
        }

        return getTile(key, request, ce.tiles);
    }

    /**
     * 
     * @param key
     * @param request
     * @param tiles
     * @return
     */
    public RenderedImage getTile(MetaTileKey key, GetMapRequest request, RenderedImage[] tiles) {
        Point tileCoord = getTileCoordinates(request.getBbox(), key.mapKey.origin);
        Point metaCoord = key.metaTileCoords;

        return tiles[tileCoord.x - metaCoord.x
                + ((tileCoord.y - metaCoord.y) * key.getMetaFactor())];
    }

    /**
     * Puts the specified tile array in the cache, and returns the tile the request was looking for
     * 
     * @param key
     * @param request
     * @param tiles
     * @return
     */
    public synchronized void storeTiles(MetaTileKey key, RenderedImage[] tiles) {
        tileCache.put(key, new CacheElement(tiles));
    }

    class CacheElement {
        RenderedImage[] tiles;

        public CacheElement(RenderedImage[] tiles) {
            this.tiles = tiles;
        }
    }

    public void dataStoreChange(TransactionEvent event) throws WFSException {
        // if anything changes we just wipe out the cache. the mapkey
        // contains a string with part of the map request where the layer
        // name is included, but we would have to parse it and consider
        // also that the namespace may be missing in the getmap request
        tileCache.clear();
    }
}
