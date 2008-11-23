/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.restconfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.vfny.geoserver.config.DataConfig;
import org.vfny.geoserver.config.DataStoreConfig;
import org.vfny.geoserver.config.FeatureTypeConfig;
import org.vfny.geoserver.global.ConfigurationException;
import org.vfny.geoserver.global.Data;
import org.vfny.geoserver.global.GeoserverDataDirectory;
import org.vfny.geoserver.global.dto.DataDTO;
import org.vfny.geoserver.global.xml.XMLConfigWriter;
import org.vfny.geoserver.util.DataStoreUtils;
import org.geotools.data.DataStore;
import org.opengis.feature.simple.SimpleFeatureType;
import javax.servlet.ServletContext;

import com.vividsolutions.jts.geom.Envelope;

import org.restlet.data.MediaType;

import org.geoserver.rest.MapResource;
import org.geoserver.rest.AutoXMLFormat;
import org.geoserver.rest.FreemarkerFormat;
import org.geoserver.rest.JSONFormat;

/**
 * Restlet for DataStore resources
 *
 * @author Arne Kepp <ak@openplans.org> , The Open Planning Project
 */
public class FeatureTypeResource extends MapResource {
    private DataConfig myDC;
    private DataStoreConfig myDSC = null;
    private FeatureTypeConfig myFTC = null;
    private Data myData;

    public FeatureTypeResource(Data d, DataConfig dc){
        super();
        setData(d);
        setDataConfig(dc);
    }

    public void setDataConfig(DataConfig dc){
        myDC = dc;
    }

    public DataConfig getDataConfig(){
        return myDC;
    }
    
    public void setData(Data d){
        myData = d;
    }
    
    public Data getData(){
        return myData;
    }

    public Map getSupportedFormats() {
        Map m = new HashMap();

        m.put("html", new FreemarkerFormat("HTMLTemplates/featuretype.ftl", getClass(), MediaType.TEXT_HTML));
        m.put("json", new JSONFormat());
        m.put("xml", new AutoXMLFormat("FeatureType"));
        m.put(null, m.get("html"));

        return m;
    }

    public Map getMap(){
        return getMap(findMyFeatureTypeConfig());
    }

    public static Map getMap(FeatureTypeConfig myFTC) {
        Map m = new HashMap();

        m.put("Style", myFTC.getDefaultStyle());
        m.put("AdditionalStyles", myFTC.getStyles());
        m.put("SRS", myFTC.getSRS());
        m.put("SRSHandling", getSRSHandling(myFTC));
        m.put("Title", myFTC.getTitle());
        m.put("BBox", getBoundingBox(myFTC)); 
        m.put("Keywords", getKeywords(myFTC));
        m.put("Abstract", myFTC.getAbstract());
        m.put("WMSPath", myFTC.getWmsPath());
        m.put("MetadataLinks", getMetadataLinks(myFTC));
        m.put("CachingEnabled", Boolean.toString(myFTC.isCachingEnabled()));
        m.put("CacheTime", (myFTC.isCachingEnabled() ? 
                    Integer.valueOf(myFTC.getCacheMaxAge()) : 
                    null
                    )
        );
        m.put("SchemaBase", myFTC.getSchemaBase());

        return m;
    }

    protected void putMap(Map m) throws Exception{
    	// TODO: Don't blindly assume map contains valid config info
        myFTC = findMyFeatureTypeConfig();

        String featureTypeName = (String) getRequest().getAttributes().get("layer");
    	String dataStoreName = (String) getRequest().getAttributes().get("folder");
 
        if (myFTC == null){
            DataStore store = DataStoreUtils.acquireDataStore(
                    myDSC.getConnectionParams(),
                    (ServletContext)null
                    );

            SimpleFeatureType type = store.getSchema(featureTypeName);

            if (type == null){
                throw new Exception(
                        "FeatureType " + featureTypeName + 
                        " in DataStore " + dataStoreName +
                        "not found."
                        );
            } else {
                myFTC = new FeatureTypeConfig(dataStoreName, type, false);
            }
        } 

        myFTC.setDefaultStyle((String)m.get("Style"));
        ArrayList styles = (ArrayList)m.get("AdditionalStyles");
        myFTC.setStyles(styles == null ? new ArrayList() : styles);
        myFTC.setSRS(Integer.valueOf((String)m.get("SRS")));
        myFTC.setSRSHandling(decodeSRSHandling((String)m.get("SRSHandling")));
        myFTC.setTitle((String)m.get("Title"));

        Envelope latLonBbox = decodeBoundingBox((List)m.get("BBox"));
        if(!myFTC.getLatLongBBox().equals(latLonBbox)) {
            myFTC.setLatLongBBox(latLonBbox);

            try{
                Envelope nativeBBox = convertBBoxFromLatLon(latLonBbox, "EPSG:" + myFTC.getSRS());
                myFTC.setNativeBBox(nativeBBox);
            } catch (Exception e) {
                LOG.severe("Couldn't convert new BBox to native coordinate system! Error was:" + e);
            }
        }

        List keywords = (List)m.get("Keywords");
        myFTC.setKeywords(keywords == null ?
                new TreeSet() :
                new TreeSet((List)m.get("Keywords"))
                );
        myFTC.setAbstract((String)m.get("Abstract"));
        myFTC.setWmsPath((String)m.get("WMSPath"));

        List metadataLinks = (List)m.get("MetadataLinks");
        myFTC.setMetadataLinks(metadataLinks == null ?
                new TreeSet() :
                new TreeSet((metadataLinks))
                );
        myFTC.setCachingEnabled(Boolean.valueOf((String)m.get("CachingEnabled")));
        myFTC.setCacheMaxAge((String)myFTC.getCacheMaxAge());
        myFTC.setSchemaBase((String)m.get("SchemaBase"));

        String qualifiedName = dataStoreName + ":" + featureTypeName;
        myDC.removeFeatureType(qualifiedName);
        myDC.addFeatureType(qualifiedName, myFTC); // TODO: This isn't needed, is it?

        myData.load(myDC.toDTO());

        saveConfiguration();
    }

    private void saveConfiguration() throws ConfigurationException{
        getData().load(getDataConfig().toDTO());
        XMLConfigWriter.store((DataDTO)getData().toDTO(),
            GeoserverDataDirectory.getGeoserverDataDirectory()
            );
    }
    
    private static String getSRSHandling(FeatureTypeConfig myFTC){
        try{
            return (new String[]{"Force","Reproject","Ignore"})[myFTC.getSRSHandling()];
        } catch (Exception e){
            return "Ignore";
        }
    }
    
    private int decodeSRSHandling(String handling){
       List decoder = Arrays.asList(new String[]{
    		   "Force",
    		   "Reproject",
    		   "Ignore"
       });
       
       return decoder.indexOf(handling);
    }
    
    private Envelope decodeBoundingBox(List l){
    	double xmin = Double.valueOf((String)l.get(0));
    	double xmax = Double.valueOf((String)l.get(1));
    	double ymin = Double.valueOf((String)l.get(2));
    	double ymax = Double.valueOf((String)l.get(3));
    	return new Envelope(xmin, xmax, ymin, ymax);
    }
    
    /**
     * Convert a bounding box in latitude/longitude coordinates to another CRS, specified by name.
     * @param latLonBbox the latitude/longitude bounding box
     * @param crsName the name of the CRS to which it should be converted
     * @return the converted bounding box
     * @throws Exception if anything goes wrong
     */
    private Envelope convertBBoxFromLatLon(Envelope latLonBbox, String crsName) throws Exception {
            CoordinateReferenceSystem latLon = CRS.decode("EPSG:4326");
            CoordinateReferenceSystem nativeCRS = CRS.decode(crsName);
            Envelope env = null;

            if (!CRS.equalsIgnoreMetadata(latLon, nativeCRS)) {
                MathTransform xform = CRS.findMathTransform(latLon, nativeCRS, true);
                env = JTS.transform(latLonBbox, null, xform, 10); //convert data bbox to lat/long
            } else {
                env = latLonBbox;
            }

            return env;
    }


    private static List getBoundingBox(FeatureTypeConfig myFTC){
        List l = new ArrayList();
        Envelope e = myFTC.getLatLongBBox();
        l.add(e.getMinX());
        l.add(e.getMaxX());
        l.add(e.getMinY());
        l.add(e.getMaxY());
        return l;
    }

    private static List getKeywords(FeatureTypeConfig myFTC){
        List l = new ArrayList();
        l.addAll(myFTC.getKeywords());
        return l;
    }

    private static List getMetadataLinks(FeatureTypeConfig myFTC){
        List l = new ArrayList();
        l.addAll(myFTC.getMetadataLinks());
        return l;
    }

    private FeatureTypeConfig findMyFeatureTypeConfig() {
        Map attributes = getRequest().getAttributes();
        String dsid = null;

        //The key for the featureTypeConfig depends on the datastores name
        if (attributes.containsKey("folder")) {
            dsid = (String) attributes.get("folder");
            myDSC = myDC.getDataStore(dsid);
        }

        if ((myDSC != null) && attributes.containsKey("layer")) {
            String ftid = (String) attributes.get("layer");

            // Append the datastore prefix
            return myDC.getFeatureTypeConfig(dsid + ":" + ftid);
        }

        return null;
    }

    public boolean allowGet() {
        return true;
    }
    
    public boolean allowPut() {
        return true;
    }

    public boolean allowDelete() {
        return true;
    }

    public void handleDelete() {
    }
}
