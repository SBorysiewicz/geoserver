/* Copyright (c) 2001 - 2009 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.gwc;

import java.util.Iterator;
import java.util.List;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.test.GeoServerTestSupport;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.Grid;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.util.wms.BBOX;


/**
 * Test class for the GWCCatalogListener
 * 
 * @author Arne Kepp / OpenGeo 2009
 */
public class GWCCatalogListenerTest extends GeoServerTestSupport {

    /**
     * Runs through the Spring based initialization sequence against the mock catalog
     * 
     * Then
     * 1) Check that cite:Lakes is present, from GWCCatalogListener
     * 2) Check sf:GenerictEntity is present and initialized, from GWCCatalogListener 
     * 3) Basic get from TileLayerDispatcher
     * 4) Removal of LayerInfo from catalog, test TileLayerDispatcher
     * 5) Introducing new LayerInfo, test TileLayerDispatcher
     * 
     * @throws Exception
     */
    public void testInit() throws Exception {
        GWCCatalogListener gwcListener = (GWCCatalogListener) applicationContext.getBean("gwcCatalogListener");

        Catalog cat = gwcListener.cat;
        
        TileLayerDispatcher tld = gwcListener.layerDispatcher;
        
        try {
            tld.getTileLayer("");
        } catch (GeoWebCacheException gwce) {
            
        }
        
        List<TileLayer> layerList = gwcListener.getTileLayers(true);
        
        Iterator<TileLayer> tlIter = layerList.iterator();

        // 1) Check that cite:Lakes
        boolean foundLakes = false;
        while(tlIter.hasNext()) {
            TileLayer tl = tlIter.next();
            if(tl.getName().equals("cite:Lakes")) {
                tl.isInitialized();
                foundLakes = true;
                break;
            }
        }                    
        assertTrue(foundLakes); 

        // 2) Check sf:GenerictEntity is present and initialized
        boolean foundGenericEntity = false;
        while(tlIter.hasNext()) {
            TileLayer tl = tlIter.next();
            if(tl.getName().equals("sf:GenericEntity")) {
                tl.isInitialized();
                foundGenericEntity = true;
                Grid epsg4326 = tl.getGrid(SRS.getEPSG4326());
                assertTrue(epsg4326.getGridBounds().equals( new BBOX(-180.0,-90.0,180.0,90.0)));
                String mime = tl.getMimeTypes().get(1).getMimeType();
                assertTrue(mime.startsWith("image/") || mime.startsWith("application/vnd.google-earth.kml+xml"));
            }
        }
        
        assertTrue(foundGenericEntity);
        
        
        // 3) Basic get
        LayerInfo li = cat.getLayers().get(1);
        String layerName = li.getResource().getPrefixedName();
        
        TileLayer tl = tld.getTileLayer(layerName);
        
        assertEquals(layerName, tl.getName());
        
        
        // 4) Removal of LayerInfo from catalog
        cat.remove(li);
        
        assertTrue(cat.getLayer(tl.getName()) == null);
        
        boolean caughtException = false;
        try {
            TileLayer tl2 = tld.getTileLayer(layerName);
        } catch (GeoWebCacheException gwce) {
            caughtException = true;
        }
        assertTrue(caughtException);
        
        // 5) Introducing new LayerInfo
        LayerInfo layerInfo = cat.getFactory().createLayer();
        layerInfo.setName("hithere");
        ResourceInfo resInfo = li.getResource(); 
        resInfo.setName("hithere");
        resInfo.getNamespace().setPrefix("sf");
        layerInfo.setResource(resInfo);
        
        cat.add(layerInfo);
        TileLayer tl3 = tld.getTileLayer("sf:hithere");
        assertEquals(tl3.getName(),"sf:hithere");
    }
}