/* Copyright (c) 2001 - 2008 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

/**
 * A geospatial resource.
 * 
 * @author Justin Deoliveira, The Open Planning Project
 */
public interface ResourceInfo extends Serializable {

    /**
     * @return The identifier of the resource.
     */
    String getId();
    
    /**
     * The catalog the resource is part of.
     */
    Catalog getCatalog();

    /**
     * Sets teh catalog the resource is part of.
     */
    void setCatalog( Catalog catalog );
    
    /**
     * The name of the resource.
     * <p>
     * This name corresponds to the "published" name of the resource.
     * </p>
     * 
     * @uml.property name="name"
     */
    String getName();

    /**
     * Sets the name of the resource.
     * 
     * @uml.property name="name"
     */
    void setName(String name);

    /**
     * The native name of the resource.
     * <p>
     * This name corresponds to the physical resource the feature type is 
     * derived from: a shapefile name, a database table, etc... 
     * </p>
     */
    String getNativeName();
    
    /**
     * Sets the native name of the resource.
     */
    void setNativeName( String nativeName );
    
    /**
     * Returns the prefixed name for the resource.
     * <p>
     * This method is a convenience for:
     * <pre>
     * return getNamespace().getPrefix() + ":" + getName();
     * </pre>
     * </p>
     * @return
     */
    String getPrefixedName();
    
    /**
     * A set of aliases or alternative names that the resource is also known by. 
     */
    List<String> getAlias();
    
    /**
     * The namespace uri of the resource.
     * <p>
     * Example would be an application schema namespace uri.
     * </p>
     * 
     * @uml.property name="namespace"
     * @uml.associationEnd inverse="resources:org.geoserver.catalog.NamespaceInfo"
     */
    NamespaceInfo getNamespace();

    /**
     * Setter of the property <tt>namespace</tt>
     * 
     * @param namespace
     *                The getNamespace to set.
     * @uml.property name="namespace"
     */
    void setNamespace(NamespaceInfo namespace);

    /**
     * The title of the resource.
     * <p>
     * This is usually something that is meant to be displayed in a user
     * interface.
     * </p>
     * 
     * @uml.property name="title"
     */
    String getTitle();

    /**
     * Sets the title of the resource.
     * 
     * @uml.property name="title"
     */
    void setTitle(String title);

    /**
     * The abstract for the resource.
     * 
     * @uml.property name="abstract"
     */
    String getAbstract();

    /**
     * Sets the abstract for the resource.
     * 
     * @uml.property name="abstract"
     */
    void setAbstract(String _abstract);

    /**
     * A description of the resource.
     * <p>
     * This is usually something that is meant to be displayed in a user
     * interface.
     * </p>
     * 
     * @uml.property name="description"
     */
    String getDescription();

    /**
     * Sets the description.
     * 
     * @uml.property name="description"
     */
    void setDescription(String description);

    /**
     * A collection of keywords associated with the resource.
     * 
     * @uml.property name="keywords"
     */
    List<String> getKeywords();

    /**
     * A collection of metadata links for the resource.
     * 
     * @uml.property name="metadataLinks"
     * @see MetadataLinkInfo
     */
    List<MetadataLinkInfo> getMetadataLinks();

    /**
     * Returns the bounds of the resource in lat / lon.
     * <p>
     * This value represents a "fixed value" and is not calulated on the
     * underlying dataset.
     * </p>
     * 
     * @return The bounds of the resource in lat / lon, or <code>null</code>
     *         if not set.
     * @uml.property name="latLonBoundingBox"
     */
    ReferencedEnvelope getLatLonBoundingBox();

    /**
     * Sets the bounds of the resource in lat / lon.
     * 
     * @param The
     *                lat/lon bounds.
     * @uml.property name="latLonBoundingBox"
     */
    void setLatLonBoundingBox(ReferencedEnvelope box);

    /**
     * Returns the bounds of the resource in the native crs.
     * <p>
     * This value represents a "fixed value" and is not calulated on the
     * underlying dataset.
     * </p>
     * 
     * @return The bounds of the resource in native crs., or <code>null</code>
     *         if not set.
     * @uml.property name="boundingBox"
     */
    ReferencedEnvelope getNativeBoundingBox();

    /**
     * Sets the bounds of the resource in the native crs.
     * 
     * @param The
     *                native crs bounds.
     * @uml.property name="boundingBox"
     */
    void setNativeBoundingBox( ReferencedEnvelope box);

    /**
     * Returns the bounds of the resource in its declared CRS.
     * <p>
     * This value is derived from {@link #getNativeBoundingBox()}, {@link #getCRS()}, 
     * and {@link #getProjectionPolicy()}.
     * </p>
     * 
     * @throws Exception If the bounding box can not be calculated.
     */
    ReferencedEnvelope getBoundingBox() throws Exception;
    
    /**
     * Returns the identifier of coordinate reference system of the resource.
     * <p>
     * Srs can be in multiple forms, examples:
     * <ol>
     * </ol>
     * </p>
     * 
     * @return A crs identifier, or <code>null</code> if not set.
     * @uml.property name="sRS"
     */
    String getSRS();

    /**
     * Sets the identifier coordinate reference system of the resource.
     * 
     * @param crs
     *                The identifier of cordinate reference system.
     * @uml.property name="sRS"
     */
    void setSRS(String srs);

    /**
     * The native coordinate reference system object of the resource.
     */
    CoordinateReferenceSystem getNativeCRS();

    /**
     * Sets the native coordinate reference system object of the resource.
     */
    void setNativeCRS( CoordinateReferenceSystem nativeCRS );
    
    /**
     * The coordinate reference system object for the resource.
     * <p>
     * This object is derived from {@link #getSRS()}.
     * </p>
     */
    CoordinateReferenceSystem getCRS() throws Exception;
    
    /**
     * The policy that should be used with the native projection of the resource
     * with respect to the declare projection.
     * 
     */
    ProjectionPolicy getProjectionPolicy();
    
    /**
     * Sets the policy that should be used with the native projection of the resource
     * with respect to the declare projection.
     * 
     */
    void setProjectionPolicy( ProjectionPolicy policy );
    
    /**
     * A persistent map of metadata.
     * <p>
     * Data in this map is intended to be persisted. Common case of use is to
     * have services associate various bits of data with a particular resource.
     * An example might include caching information.
     * </p>
     * <p>
     * The key values of this map are of type {@link String} and values are of
     * type {@link Serializable}.
     * </p>
     * 
     * @uml.property name="metadata"
     */
    Map<String,Serializable> getMetadata();

    /**
     * A flag indicating if the resource is enabled or not.
     * 
     * @uml.property name="enabled"
     */
    boolean isEnabled();

    /**
     * Sets the enabled flag for the resource.
     * 
     * @uml.property name="enabled"
     */
    void setEnabled(boolean enabled);
    
    /**
     * The store the resource is a part of.
     * 
     * @uml.property name="store"
     * @uml.associationEnd inverse="resourceInfo:org.geoserver.catalog.StoreInfo"
     */
    StoreInfo getStore();

    /**
     * Sets the store the resource is a part of.
     * 
     * @param store
     *                The store to set.
     * @uml.property name="store"
     */
    void setStore(StoreInfo store);

    /**
     * Creates an adapter for the resource.
     * <p>
     * 
     * </p>
     * 
     * @param adapterClass
     *                The class of the adapter.
     * @param hints
     *                Hints to use when creating the adapter.
     * 
     * @return The adapter, an intsanceof adapterClass, or <code>null</code>.
     */
    <T extends Object> T getAdapter(Class<T> adapterClass, Map<?,?> hints);

    /**
     * The handle to the live resource.
     * <p>
     * This method does I/O and is potentially blocking. The <tt>listener</tt>
     * is used to report the progress of obtaining the resource and to report
     * any warnings / errors that occur during.
     * </p>
     * 
     * @uml.property name="resource"
     * @uml.associationEnd inverse="resourceInfo:org.geoserver.catalog.Resource"
     */
    //Resource getResource(ProgressListener listener) throws IOException;
}