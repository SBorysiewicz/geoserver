/* Copyright (c) 2001 - 2008 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogVisitor;
import org.geoserver.catalog.CoverageDimensionInfo;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geotools.factory.Hints;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.opengis.coverage.grid.GridCoverage;
import org.opengis.coverage.grid.GridCoverageReader;
import org.opengis.coverage.grid.GridGeometry;
import org.opengis.util.ProgressListener;

public class CoverageInfoImpl extends ResourceInfoImpl implements CoverageInfo {

    String nativeFormat;

    GridGeometry grid;
    
    List<String> supportedFormats = new ArrayList<String>();

    List<String> interpolationMethods = new ArrayList<String>();

    String defaultInterpolationMethod;

    List<CoverageDimensionInfo> dimensions = new ArrayList<CoverageDimensionInfo>();

    List<String> requestSRS = new ArrayList<String>();

    List<String> responseSRS = new ArrayList<String>();
    
    Map parameters = new HashMap();

    public CoverageInfoImpl(Catalog catalog) {
        super( catalog );
    }

    public CoverageInfoImpl(Catalog catalog, String id) {
        super(catalog, id);
    }

    public CoverageStoreInfo getStore() {
        return (CoverageStoreInfo) super.getStore();
    }

    public GridGeometry getGrid() {
        return grid;
    }
    
    public void setGrid(GridGeometry grid) {
        this.grid = grid;
    }
    
    public String getNativeFormat() {
        return nativeFormat;
    }

    public void setNativeFormat(String nativeFormat) {
        this.nativeFormat = nativeFormat;
    }

    public List<String> getSupportedFormats() {
        return supportedFormats;
    }

    public List<String> getInterpolationMethods() {
        return interpolationMethods;
    }

    public String getDefaultInterpolationMethod() {
        return defaultInterpolationMethod;
    }

    public void setDefaultInterpolationMethod(String defaultInterpolationMethod) {
        this.defaultInterpolationMethod = defaultInterpolationMethod;
    }

    public List getDimensions() {
        return dimensions;
    }

    public List<String> getRequestSRS() {
        return requestSRS;
    }

    public List<String> getResponseSRS() {
        return responseSRS;
    }

    public Map getParameters() {
        return parameters;
    }

    public void setParameters(Map parameters) {
        this.parameters = parameters;
    }

    public GridCoverage getGridCoverage(ProgressListener listener, Hints hints)
            throws IOException {
        return catalog.getResourcePool().getGridCoverage(this, null, hints); 
    }
    
    public GridCoverage getGridCoverage(ProgressListener listener,
            ReferencedEnvelope envelope, Hints hints) throws IOException {
        return catalog.getResourcePool().getGridCoverage(this, envelope, hints);
    }
    
    public GridCoverageReader getGridCoverageReader(ProgressListener listener,
            Hints hints) throws IOException {
        return catalog.getResourcePool().getGridCoverageReader(getStore(), hints);
    }
    
    public void accept(CatalogVisitor visitor) {
        visitor.visit(this);
    }

    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime
                * result
                + ((defaultInterpolationMethod == null) ? 0
                        : defaultInterpolationMethod.hashCode());
        result = prime * result
                + ((dimensions == null) ? 0 : dimensions.hashCode());
        result = prime * result + ((grid == null) ? 0 : grid.hashCode());
        result = prime
                * result
                + ((interpolationMethods == null) ? 0 : interpolationMethods
                        .hashCode());
        result = prime * result
                + ((nativeFormat == null) ? 0 : nativeFormat.hashCode());
        result = prime * result
                + ((parameters == null) ? 0 : parameters.hashCode());
        result = prime * result
                + ((requestSRS == null) ? 0 : requestSRS.hashCode());
        result = prime * result
                + ((responseSRS == null) ? 0 : responseSRS.hashCode());
        result = prime
                * result
                + ((supportedFormats == null) ? 0 : supportedFormats.hashCode());
        return result;
    }

    public boolean equals(Object obj) {
        if ( !( obj instanceof CoverageInfo ) ) {
            return false;
        }
        if ( !super.equals( obj ) ) {
            return false;
        }
        
        final CoverageInfo other = (CoverageInfo) obj;
        if (defaultInterpolationMethod == null) {
            if (other.getDefaultInterpolationMethod() != null)
                return false;
        } else if (!defaultInterpolationMethod
                .equals(other.getDefaultInterpolationMethod()))
            return false;
        if (dimensions == null) {
            if (other.getDimensions() != null)
                return false;
        } else if (!dimensions.equals(other.getDimensions()))
            return false;
        if (grid == null) {
            if (other.getGrid() != null)
                return false;
        } else if (!grid.equals(other.getGrid()))
            return false;
        if (interpolationMethods == null) {
            if (other.getInterpolationMethods() != null)
                return false;
        } else if (!interpolationMethods.equals(other.getInterpolationMethods()))
            return false;
        if (nativeFormat == null) {
            if (other.getNativeFormat() != null)
                return false;
        } else if (!nativeFormat.equals(other.getNativeFormat()))
            return false;
        if (parameters == null) {
            if (other.getParameters() != null)
                return false;
        } else if (!parameters.equals(other.getParameters()))
            return false;
        if (requestSRS == null) {
            if (other.getRequestSRS() != null)
                return false;
        } else if (!requestSRS.equals(other.getRequestSRS()))
            return false;
        if (responseSRS == null) {
            if (other.getResponseSRS() != null)
                return false;
        } else if (!responseSRS.equals(other.getResponseSRS()))
            return false;
        if (supportedFormats == null) {
            if (other.getSupportedFormats() != null)
                return false;
        } else if (!supportedFormats.equals(other.getSupportedFormats()))
            return false;
        return true;
    }
}
