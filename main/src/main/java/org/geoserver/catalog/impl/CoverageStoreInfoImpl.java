/* Copyright (c) 2001 - 2008 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog.impl;

import java.util.Iterator;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogVisitor;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.opengis.util.ProgressListener;

/**
 * Default implementation of {@link CoverageStoreInfo}.
 */
public class CoverageStoreInfoImpl extends StoreInfoImpl implements
        CoverageStoreInfo {

    String url;
    
    AbstractGridFormat format;
    
    public CoverageStoreInfoImpl(Catalog catalog) {
        super(catalog);
    }

    public CoverageStoreInfoImpl(Catalog catalog,String id) {
        super(catalog,id);
    }

    public String getURL() {
        return url;
    }

    public void setURL(String url) {
        this.url = url;
    }
    
    public AbstractGridFormat getFormat() {
        return catalog.getResourcePool().getGridCoverageFormat(this);
    }
    
    public void accept(CatalogVisitor visitor) {
        visitor.visit( this );
    }
}
