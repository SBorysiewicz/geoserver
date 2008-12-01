/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.wms;

import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.geotools.data.DataStore;
import org.geotools.data.wfs.WFSDataStoreFactory;

/** 
 * Utility class used to check wheter REMOTE_OWS_XXX related tests can be run against Sigma
 * or not.
 * @author Andrea Aime - TOPP
 */
public class RemoteOWSTestSupport {
    static final Logger LOGGER = org.geotools.util.logging.Logging.getLogger("org.geoserver.test"); 
    
    // support for remote OWS layers
    public static final String TOPP_STATES = "topp:states";
    public static final String WFS_SERVER_URL = "http://demo.opengeo.org/geoserver/wfs?";
    static Boolean remoteStatesAvailable;
    
    public static boolean isRemoteStatesAvailable() {
        if(remoteStatesAvailable == null) {
            // let's check if the remote WFS tests are runnable
            try {
                WFSDataStoreFactory factory = new WFSDataStoreFactory();
                Map<String, Serializable> params = new HashMap(factory.getImplementationHints());
                URL url = new URL(WFS_SERVER_URL + "service=WFS&request=GetCapabilities");
                params.put(WFSDataStoreFactory.URL.key, url);
                params.put(WFSDataStoreFactory.TRY_GZIP.key, Boolean.TRUE);
                //give it five seconds to respond...
                params.put(WFSDataStoreFactory.TIMEOUT.key, Integer.valueOf(5000));
                DataStore remoteStore = factory.createDataStore(params);
                remoteStore.getFeatureSource(TOPP_STATES);
                remoteStatesAvailable = Boolean.TRUE;
            } catch(IOException e) {
                LOGGER.log(Level.WARNING, "Skipping remote OWS test, either sigma " +
                        "is down or the topp:states layer is not there", e);
                remoteStatesAvailable = Boolean.FALSE;
            }
        } 
        return remoteStatesAvailable.booleanValue();
   }
}
