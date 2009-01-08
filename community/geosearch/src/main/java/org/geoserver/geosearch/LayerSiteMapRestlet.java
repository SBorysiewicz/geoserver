package org.geoserver.geosearch;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.geoserver.ows.util.RequestUtils;
import org.geotools.data.jdbc.JDBCUtils;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.geotools.util.logging.Logging;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.restlet.data.Method;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.springframework.jdbc.support.incrementer.H2SequenceMaxValueIncrementer;
import org.vfny.geoserver.config.DataConfig;
import org.vfny.geoserver.global.Data;
import org.vfny.geoserver.global.FeatureTypeInfo;
import org.vfny.geoserver.global.GeoServer;
import org.vfny.geoserver.global.MapLayerInfo;

import com.vividsolutions.jts.geom.Envelope;

public class LayerSiteMapRestlet extends GeoServerProxyAwareRestlet{

    private static Logger LOGGER = Logging.getLogger("org.geoserver.geosearch");

    private Data myData;
    private DataConfig myDataConfig;
    private GeoServer myGeoserver;
    private String GEOSERVER_URL;
    
    private Namespace SITEMAP = Namespace.getNamespace("http://www.sitemaps.org/schemas/sitemap/0.9");
    private static Namespace GEOSITEMAP = Namespace.getNamespace("geo","http://www.google.com/geo/schemas/sitemap/1.0");

    static final CoordinateReferenceSystem WGS84;

    static final ReferencedEnvelope WORLD_BOUNDS;

    static final double MAX_TILE_WIDTH;

    static {
        try {
            // Common geographic info
            WGS84 = CRS.decode("EPSG:4326");
            WORLD_BOUNDS = new ReferencedEnvelope(
                    new Envelope(180.0, -180.0,90.0, -90.0), WGS84);
            MAX_TILE_WIDTH = WORLD_BOUNDS.getWidth() / 2.0;

            // Make sure that H2 is around
            Class.forName("org.h2.Driver");
        } catch (Exception e) {
            throw new RuntimeException(
                    "Could not initialize the class constants", e);
        }
    }
    
    public void setData(Data d){
        myData = d;
    }

    public void setDataConfig(DataConfig dc){
        myDataConfig = dc;
    }

    public void setGeoServer(GeoServer gs){
        myGeoserver = gs;
    }

    public Data getData(){
        return myData;
    }

    public DataConfig getDataConfig(){
        return myDataConfig;
    }

    public GeoServer getGeoServer(){
        return myGeoserver;
    }

    public LayerSiteMapRestlet() {
    }

    public void handle(Request request, Response response){ 
        GEOSERVER_URL = RequestUtils.proxifiedBaseURL(request.getRootRef().getParentRef().toString()
                , getGeoServer().getProxyBaseUrl());
        
        if (request.getMethod().equals(Method.GET)){
            doGet(request, response);
        } else {
            response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
        }
    }

    /**
     * Performs basic checks, making sure that this layer
     * does indeed support regionating and is supposed to be searchable. 
     * 
     * Then kicks off the process that creates a document, filling
     * in the details from the H2 database.
     * 
     * @param request
     * @param response
     */
    public void doGet(Request request, Response response){
        String layerName = (String)request.getAttributes().get("layer");
        
        MapLayerInfo mli = getLayer(layerName);
        
        // Check that we know the layer
        if(mli == null) {
            response.setStatus(Status.CLIENT_ERROR_NOT_FOUND);
            //TODO nice error message
            return;
        }
        
        // And that we allow people to index it
        FeatureTypeInfo fti = mli.getFeature();
        if(fti == null || ! fti.isIndexingEnabled()) {
            response.setStatus(Status.CLIENT_ERROR_NOT_FOUND);
            //TODO nice error message
            return;
        }

        // Do we have a regionating strategy ?
        if(fti.getRegionateAttribute() == null 
                || fti.getRegionateAttribute().length() == 0) {
            response.setStatus(Status.CLIENT_ERROR_NOT_FOUND);
            //TODO nice error message
            return;
        }
        
        // All good, we're finally here:
        Document d = buildSitemap(layerName, fti);
        response.setEntity(new JDOMRepresentation(d));
    }
    
    /**
     * Wrapper function that constructs document, which is then passed 
     * on to the H2 database which goes and looks for tiles to link to. 
     * 
     * @param layerName
     * @param fti
     * @return
     */
    private Document buildSitemap(String layerName, FeatureTypeInfo fti) {
        final Document d = new Document();
        
        Element urlSet = new Element("urlset", SITEMAP);
        urlSet.addNamespaceDeclaration(GEOSITEMAP);
        d.setRootElement(urlSet);
        
        try {
            // Look for the actual tiles
            getTilesFromDatababase(urlSet, fti);
        } catch (IOException ioe) {
            //TODO log
        }
        
        return d;
    }
    
    /**
     * Just adds one URL to the sitemap, with geo tags
     * 
     * @param urlSet
     * @param url
     */
    private void addTile(Element urlSet, String url) {
        Element urlElement = new Element("url", SITEMAP);
        Element loc = new Element("loc", SITEMAP);
        loc.setText(url);
        urlElement.addContent(loc);


        Element geo = new Element("geo",GEOSITEMAP);
        Element geoformat = new Element("format",GEOSITEMAP);
        geoformat.setText("kml");    
        geo.addContent(geoformat);
        urlElement.addContent(geo);
        
        urlSet.addContent(urlElement);
    }
    
    /**
     * This method has a bit too much content at the moment, but the trouble
     * is really elsewhere. We need a nice way to build the entire hierarchy.
     * 
     * This code extracts all the tiles from the H2 database.
     * 
     * If it finds none, it will link to the topmost tile.
     * 
     * Afterwards, it links to all the tiles one zoomlevel below. The crawler
     * will then add them to the H2 database, thereby expanding the index.
     * 
     * @param urlSet
     * @param fti
     * @throws IOException
     */
    private void getTilesFromDatababase(Element urlSet, FeatureTypeInfo fti) 
    throws IOException {        
        String dataDir = this.myData.getDataDirectory().getCanonicalPath();
        String tableName = fti.getTypeName() + "_" + fti.getRegionateAttribute();

        //System.out.println("jdbc:h2:file:" + dataDir + "/geosearch/h2cache_" + tableName);
        
        Connection conn = null;
        Statement st = null;
        ResultSet rs = null;
        
        long[] maxCoords = {0,0,0,0,-1};
        
        try {
            conn = DriverManager.getConnection("jdbc:h2:file:" + dataDir
                            + "/geosearch/h2cache_" + tableName, "geoserver", "geopass");
            st = conn.createStatement();
            rs = st.executeQuery("SELECT x,y,z FROM TILECACHE WHERE FID IS NOT NULL GROUP BY x,y,z ORDER BY z ASC");
            
            while(rs.next()) {
                long[] coords = new long[3];
                coords[0] = rs.getLong(1);
                coords[1] = rs.getLong(2);
                coords[2] = rs.getLong(3);
                
                //System.out.println("x:"+coords[0]+" y:"+coords[1]+" z:"+coords[2]);
                
                updateMaxCoords(maxCoords, coords);
                addTile(urlSet, makeUrl(coords, fti));
            }
            
            rs.close();
            
        } catch (SQLException se) {
            LOGGER.severe(se.getMessage());
            se.printStackTrace();
        } finally {
            JDBCUtils.close(st);
            JDBCUtils.close(conn, null, null);
            
            // Check that we got something ? 
            if(maxCoords[4] < 0) {
                // Nope. We start from the top.
                long[][] coordss = zoomedOut(fti);
                for(int i=0; i<coordss.length; i++) {
                    addTile(urlSet, makeUrl(coordss[i], fti));
                    updateMaxCoords(maxCoords, coordss[i]);
                }
            }
            
            expandHierarchy(urlSet, fti, maxCoords);
        }
    }
    
    
    private long[][] zoomedOut(FeatureTypeInfo fti) {
        try {
            Envelope env = fti.getLatLongBoundingBox();
            //double[] coords = {env.getMinX(), env.getMinY(), env.getMaxX(), env.getMaxY()};
            
            // World wide case
            if(env.getMinX() < 0.0 && env.getMaxX() > 0.0) {
                long[][] ret = {{0,0,0},{1,0,0}};
                return ret;
            }
            
            long[] nextQuad = new long[3];
            if(env.getMinX() < 0.0) {
                nextQuad[0] = 0; nextQuad[1] = 0; nextQuad[2] = 0;
            } else {
                nextQuad[0] = 1; nextQuad[1] = 0; nextQuad[2] = 0;
            }
            
            long[] prevQuad = null;

            while(nextQuad != null) {                
                // Try each of the quadrants
                long[][] quads = { 
                        { nextQuad[0] * 2,  nextQuad[1] * 2,  nextQuad[2] + 1}, 
                        { nextQuad[0] * 2 + 1,  nextQuad[1] * 2,  nextQuad[2] + 1}, 
                        { nextQuad[0] * 2,  nextQuad[1] * 2 + 1 ,  nextQuad[2] + 1}, 
                        { nextQuad[0] * 2 + 1,  nextQuad[1] * 2 + 1,  nextQuad[2] + 1} };
                
                prevQuad = nextQuad;
                nextQuad = null;
                
                for(int i=0; i<quads.length; i++) {
                    ReferencedEnvelope testEnv = envelope(quads[i][0], quads[i][1], quads[i][2]);
                    if(testEnv.contains(env)) {
                        nextQuad = quads[i];
                    }
                }
            }
            
            long[][] ret = {prevQuad};
            return ret;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * The maxCoords describe as far as the hierarchy is built.
     * 
     * This expands one level further, which may lead to linking to
     * some 204s, but unless the data is distributed perfectly evenly
     * it will only be a few tiles.
     * 
     * @param urlSet
     * @param maxCoords
     */
    private void expandHierarchy(Element urlSet, FeatureTypeInfo fti, long[] maxCoords) {
        long z = maxCoords[4] + 1;
        for(long x=maxCoords[0]; x<=maxCoords[2]; x++) {
            for(long y=maxCoords[1]; y<=maxCoords[3]; y++) {
                long[] bl = {x * 2, y * 2, z};
                long[] br = {x * 2 + 1, y * 2, z};
                long[] tl = {x * 2, y * 2 + 1 , z};
                long[] tr = {x * 2 + 1, y * 2 + 1, z};
                
                addTile(urlSet, makeUrl(bl, fti));
                addTile(urlSet, makeUrl(br, fti));
                addTile(urlSet, makeUrl(tl, fti));
                addTile(urlSet, makeUrl(tr, fti));
            }
        }
    }

    /**
     * Converts x,y,z into an envelope, to be used in the WMS URL  
     * 
     * @param x
     * @param y
     * @param z
     * @return
     */
    private ReferencedEnvelope envelope(long x, long y, long z) {
        double tileSize = MAX_TILE_WIDTH / Math.pow(2, z);
        double xMin = x * tileSize + WORLD_BOUNDS.getMinX();
        double yMin = y * tileSize + WORLD_BOUNDS.getMinY();
        return new ReferencedEnvelope(xMin, xMin + tileSize, yMin, yMin
                + tileSize, WGS84);
    }

    /**
     * This keeps track of the bounds and resets every time
     * the zoomlevel changes
     * 
     * @param maxCoords
     * @param coords
     */
    private void updateMaxCoords(long[] maxCoords, long[] coords) {
        if(coords[2] > maxCoords[4]) {
            maxCoords[0] = Long.MAX_VALUE;
            maxCoords[1] = Long.MAX_VALUE;
            maxCoords[2] = Long.MIN_VALUE;
            maxCoords[3] = Long.MIN_VALUE;
            maxCoords[4] = coords[2];
        }
        
        if(maxCoords[0] > coords[0]) {
            maxCoords[0] = coords[0];
        }
        if(maxCoords[1] > coords[1]) {
            maxCoords[1] = coords[1];
        }
        if(maxCoords[2] < coords[0]) {
            maxCoords[2] = coords[0];
        }
        if(maxCoords[3] < coords[1]) {
            maxCoords[3] = coords[1];
        }
    }
    
    /**
     * Constructs a WMS URL for the given coordinates 
     * 
     * @param coords
     * @param fti
     * @return
     */
    private String makeUrl(long[] coords, FeatureTypeInfo fti) {
        // Ok we have the coordinates, now we turn that into a bbox for a WMS query
        ReferencedEnvelope env = envelope(coords[0],coords[1],coords[2]);
        
        String url = 
            GEOSERVER_URL + "wms?"
            + "service=WMS&"
            + "version=1.1.0&"
            + "request=GetMap&"
            + "format=application/vnd.google-earth.kml+xml&"
            + "format_options=regionateby:auto&"
            + "exceptions=application/vnd.ogc.se_inimage&"
            + "bbox=" + env.getMinX() + "," + env.getMinY() 
            + "," + env.getMaxX() + "," + env.getMaxY() + "&"
            + "srs=EPSG:4326&"
            + "styles=" + fti.getDefaultStyle().getName() +"&"
            + "layers=" + fti.getName() + "&"
            + "tiled=FALSE&"
            + "width=256&"
            + "height=256";
        
        return url;
    }
    
    /**
     * Retrieves the requested layer object based on the name witth prefix
     * 
     * @param layerName the layer (with prefix) to look for
     * @return the requested layer, otherwise null
     */
    private MapLayerInfo getLayer(String layerName){
        Iterator it = getData().getFeatureTypeInfos().entrySet().iterator();
        while (it.hasNext()){
            Entry entry = (Entry) it.next();
            String name = ((org.vfny.geoserver.global.FeatureTypeInfo) entry.getValue()).getName();
            if (name.equalsIgnoreCase(layerName)){
                return getData().getMapLayerInfo(name);
            }
        }
        return null;
    }
}

