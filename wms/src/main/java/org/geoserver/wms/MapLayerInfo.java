/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org.  All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.wms;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.StyleInfo;
import org.geotools.data.FeatureSource;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.styling.Style;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.vfny.geoserver.util.DataStoreUtils;

import com.vividsolutions.jts.geom.Envelope;

/**
 * DOCUMENT ME!
 * 
 * @author $Author: Alessio Fabiani (alessio.fabiani@gmail.com) $ (last modification)
 * @author $Author: Simone Giannecchini (simboss1@gmail.com) $ (last modification)
 * @author Gabriel Roldan
 */
public final class MapLayerInfo {
    public static int TYPE_VECTOR = LayerInfo.Type.VECTOR.getCode();

    public static int TYPE_RASTER = LayerInfo.Type.RASTER.getCode();

    public static int TYPE_REMOTE_VECTOR = LayerInfo.Type.REMOTE.getCode();

    /**
     * The feature source for the remote WFS layer (see REMOVE_OWS_TYPE/URL in the SLD spec)
     */
    private final FeatureSource<SimpleFeatureType, SimpleFeature> remoteFeatureSource;

    /**
     * 
     * @uml.property name="type" multiplicity="(0 1)"
     */
    private final int type;

    /**
     * 
     * @uml.property name="name" multiplicity="(0 1)"
     */
    private final String name;

    /**
     * 
     * @uml.property name="label" multiplicity="(0 1)"
     */
    private final String label;

    /**
     * 
     * @uml.property name="description" multiplicity="(0 1)"
     */
    private final String description;

    private final LayerInfo layerInfo;

    public MapLayerInfo(FeatureSource<SimpleFeatureType, SimpleFeature> remoteSource) {
        this.remoteFeatureSource = remoteSource;
        this.layerInfo = null;
        name = remoteFeatureSource.getSchema().getTypeName();
        label = name;
        description = "Remote WFS";
        type = TYPE_REMOTE_VECTOR;
    }

    public MapLayerInfo(LayerInfo layerInfo) {
        this.layerInfo = layerInfo;
        this.remoteFeatureSource = null;
        ResourceInfo resource = layerInfo.getResource();

        // handle InlineFeatureStuff
        this.name = resource.getName();
        this.label = resource.getTitle();
        this.description = resource.getAbstract();

        this.type = layerInfo.getType().getCode();
    }

    /**
     * <p>
     * The feature source bounds. Mind, it might be null, in that case, grab the lat/lon bounding
     * box and reproject to the native bounds.
     * </p>
     * 
     * @return Envelope the feature source bounds.
     * @throws Exception
     */
    public ReferencedEnvelope getBoundingBox() throws Exception {
        if (layerInfo != null) {
            return layerInfo.getResource().getBoundingBox();
        } else if (this.type == TYPE_REMOTE_VECTOR) {
            return remoteFeatureSource.getBounds();
        }
        return null;
    }

    /**
     * Get the bounding box in latitude and longitude for this layer.
     * 
     * @return Envelope the feature source bounds.
     * 
     * @throws IOException
     *             when an error occurs
     */
    public Envelope getLatLongBoundingBox() throws IOException {
        if (layerInfo != null) {
            return layerInfo.getResource().getLatLonBoundingBox();
        }

        return DataStoreUtils.getBoundingBoxEnvelope(remoteFeatureSource);
    }

    /**
     * 
     * @uml.property name="coverage"
     */
    public CoverageInfo getCoverage() {
        return (CoverageInfo) layerInfo.getResource();
    }

    /**
     * 
     * @uml.property name="description"
     */
    public String getDescription() {
        return description;
    }

    /**
     * 
     * @uml.property name="feature"
     */
    public FeatureTypeInfo getFeature() {
        return (FeatureTypeInfo) layerInfo.getResource();
    }

    /**
     * 
     * @uml.property name="label"
     */
    public String getLabel() {
        return label;
    }

    /**
     * 
     * @uml.property name="name"
     */
    public String getName() {
        return name;
    }

    /**
     * 
     * @uml.property name="type"
     */
    public int getType() {
        return type;
    }

    public Style getDefaultStyle() {
        if (layerInfo != null) {
            try {
                return layerInfo.getDefaultStyle().getStyle();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return null;
    }

    /**
     * Returns the remote feature source in case this layer is a remote WFS layer
     * 
     * @return
     */
    public FeatureSource<SimpleFeatureType, SimpleFeature> getRemoteFeatureSource() {
        return remoteFeatureSource;
    }

    /**
     * @return the resource SRS name or {@code null} if the underlying resource is not a registered
     *         one
     */
    public String getSRS() {
        if (layerInfo != null) {
            return layerInfo.getResource().getSRS();
        }
        return null;
    }

    /**
     * Returns a full list of the alternate style names
     * 
     * @return
     */
    public List<String> getStyleNames() {
        if (layerInfo == null) {
            return Collections.emptyList();
        }
        final List<String> styleNames = new ArrayList<String>();

        for (StyleInfo si : layerInfo.getStyles()) {
            styleNames.add(si.getName());
        }
        return styleNames;
    }

}
