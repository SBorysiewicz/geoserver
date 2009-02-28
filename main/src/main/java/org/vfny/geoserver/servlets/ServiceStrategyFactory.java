/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.vfny.geoserver.servlets;

import org.geoserver.config.GeoServer;
import org.geoserver.ows.OutputStrategyFactory;
import org.geoserver.ows.ServiceStrategy;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.web.context.WebApplicationContext;
import org.vfny.geoserver.util.PartialBufferedOutputStream2;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;


public class ServiceStrategyFactory implements OutputStrategyFactory, ApplicationContextAware {
    /** Class logger */
    static Logger LOGGER = org.geotools.util.logging.Logging.getLogger("org.vfny.geoserver.servlets");

    /**
     * GeoServer configuratoin
     */
    GeoServer geoServer;

    /**
     * The application context
     */
    ApplicationContext context;

    /**
     * The default service strategy
     */
    String serviceStrategy;

    /**
     * The default buffer size when the partial buffer strategy is used
     */
    int partialBufferSize = PartialBufferedOutputStream2.DEFAULT_BUFFER_SIZE;

    public ServiceStrategyFactory(GeoServer geoServer) {
        this.geoServer = geoServer;
    }

    public void setApplicationContext(ApplicationContext context)
        throws BeansException {
        this.context = context;
    }

    public void setServiceStrategy(String serviceStrategy) {
        this.serviceStrategy = serviceStrategy;
    }

    public void setPartialBufferSize(int partialBufferSize) {
        this.partialBufferSize = partialBufferSize;
    }

    public ServletContext getServletContext() {
        return ((WebApplicationContext) context).getServletContext();
    }

    public ServiceStrategy createOutputStrategy(HttpServletResponse response) {
        //If verbose exceptions is on then lets make sure they actually get the
        // exception by using the file strategy.
        ServiceStrategy theStrategy = null;

        if (geoServer.getGlobal().isVerboseExceptions()) {
            theStrategy = (ServiceStrategy) context.getBean("fileServiceStrategy");
        } else {
            if (serviceStrategy == null) {
                // none set, look up in web applicatino context
                serviceStrategy = getServletContext().getInitParameter("serviceStrategy");
            }

            // do a lookup
            if (serviceStrategy != null) {
                theStrategy = (ServiceStrategy) context.getBean(serviceStrategy);
            }
        }

        if (theStrategy == null) {
            // default to buffer
            theStrategy = (ServiceStrategy) context.getBean("bufferServiceStrategy");
        }

        // clone the strategy since at the moment the strategies are marked as singletons
        // in the web.xml file.
        try {
            theStrategy = (ServiceStrategy) theStrategy.clone();
        } catch (CloneNotSupportedException e) {
            LOGGER.log(Level.SEVERE,
                "Programming error found, service strategies should be cloneable, " + e, e);
            throw new RuntimeException("Found a strategy that does not support cloning...", e);
        }

        // TODO: this hack should be removed once modules have their own config
        if (theStrategy instanceof PartialBufferStrategy2) {
            if (partialBufferSize == 0) {
                String size = getServletContext().getInitParameter("PARTIAL_BUFFER_STRATEGY_SIZE");

                if (size != null) {
                    try {
                        partialBufferSize = Integer.valueOf(size).intValue();

                        if (partialBufferSize <= 0) {
                            LOGGER.warning("Invalid partial buffer size, defaulting to "
                                + PartialBufferedOutputStream2.DEFAULT_BUFFER_SIZE + " (was "
                                + partialBufferSize + ")");
                            partialBufferSize = 0;
                        }
                    } catch (NumberFormatException nfe) {
                        LOGGER.warning("Invalid partial buffer size, defaulting to "
                            + PartialBufferedOutputStream2.DEFAULT_BUFFER_SIZE + " (was "
                            + partialBufferSize + ")");
                        partialBufferSize = 0;
                    }
                }
            }

            ((PartialBufferStrategy2) theStrategy).setBufferSize(partialBufferSize);
        }

        return theStrategy;
    }
}
