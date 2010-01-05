/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org.  All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wcs.response;

import static org.geoserver.ows.util.ResponseUtils.appendPath;
import static org.geoserver.ows.util.ResponseUtils.buildURL;

import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import net.opengis.wcs10.DescribeCoverageType;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.MetadataLinkInfo;
import org.geoserver.ows.URLMangler.URLType;
import org.geoserver.wcs.WCSInfo;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.coverage.grid.io.AbstractGridCoverage2DReader;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.geotools.referencing.operation.LinearTransform;
import org.geotools.referencing.operation.transform.AffineTransform2D;
import org.geotools.util.logging.Logging;
import org.geotools.xml.transform.TransformerBase;
import org.geotools.xml.transform.Translator;
import org.opengis.coverage.grid.GridEnvelope;
import org.opengis.coverage.grid.GridGeometry;
import org.opengis.coverage.grid.GridRange;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.datum.PixelInCell;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.Matrix;
import org.vfny.geoserver.wcs.WcsException;
import org.vfny.geoserver.wcs.WcsException.WcsExceptionCode;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Based on the <code>org.geotools.xml.transform</code> framework, does the job of encoding a WCS
 * 1.0.0 DescribeCoverage document.
 * 
 * @author Andrea Aime, TOPP
 * @author Alessio Fabiani, GeoSolutions
 * 
 */
public class Wcs10DescribeCoverageTransformer extends TransformerBase {
    private static final Logger LOGGER = Logging.getLogger(Wcs10DescribeCoverageTransformer.class
            .getPackage().getName());

    private static final String WCS_URI = "http://www.opengis.net/wcs";

    private static final String XSI_PREFIX = "xsi";

    private static final String XSI_URI = "http://www.w3.org/2001/XMLSchema-instance";

    private static final Map<String, String> METHOD_NAME_MAP = new HashMap<String, String>();

    static {
        METHOD_NAME_MAP.put("nearest neighbor", "nearest");
        METHOD_NAME_MAP.put("bilinear", "linear");
        METHOD_NAME_MAP.put("bicubic", "cubic");
    }

    private WCSInfo wcs;

    private Catalog catalog;

    /**
     * Creates a new WFSCapsTransformer object.
     */
    public Wcs10DescribeCoverageTransformer(WCSInfo wcs, Catalog catalog) {
        super();
        this.wcs = wcs;
        this.catalog = catalog;
        setNamespaceDeclarationEnabled(false);
    }

    public Translator createTranslator(ContentHandler handler) {
        return new WCS100DescribeCoverageTranslator(handler);
    }

    private class WCS100DescribeCoverageTranslator extends TranslatorSupport {
        // the path that does contain the GeoServer internal XML schemas
        public static final String SCHEMAS = "schemas";

        private DescribeCoverageType request;

        private String proxifiedBaseUrl;

        /**
         * Creates a new WFSCapsTranslator object.
         * 
         * @param handler
         *            DOCUMENT ME!
         */
        public WCS100DescribeCoverageTranslator(ContentHandler handler) {
            super(handler, null, null);
        }

        /**
         * Encode the object.
         * 
         * @param o
         *            The Object to encode.
         * 
         * @throws IllegalArgumentException
         *             if the Object is not encodeable.
         */
        public void encode(Object o) throws IllegalArgumentException {
            // try {
            if (!(o instanceof DescribeCoverageType)) {
                throw new IllegalArgumentException(new StringBuilder("Not a GetCapabilitiesType: ")
                        .append(o).toString());
            }

            this.request = (DescribeCoverageType) o;

            final AttributesImpl attributes = new AttributesImpl();
            attributes.addAttribute("", "xmlns:wcs", "xmlns:wcs", "", WCS_URI);
            attributes.addAttribute("", "xmlns:xlink", "xmlns:xlink", "",
                    "http://www.w3.org/1999/xlink");
            attributes.addAttribute("", "xmlns:ogc", "xmlns:ogc", "", "http://www.opengis.net/ogc");
            attributes.addAttribute("", "xmlns:ows", "xmlns:ows", "",
                    "http://www.opengis.net/ows/1.1");
            attributes.addAttribute("", "xmlns:gml", "xmlns:gml", "", "http://www.opengis.net/gml");

            final String prefixDef = new StringBuilder("xmlns:").append(XSI_PREFIX).toString();
            attributes.addAttribute("", prefixDef, prefixDef, "", XSI_URI);

            final String locationAtt = new StringBuilder(XSI_PREFIX).append(":schemaLocation")
                    .toString();

            // proxifiedBaseUrl = RequestUtils.proxifiedBaseURL(request.getBaseUrl(),
            // wcs.getGeoServer().getGlobal().getProxyBaseUrl());
            // final String locationDef = WCS_URI + " " + proxifiedBaseUrl +
            // "schemas/wcs/1.0.0/describeCoverage.xsd";
            final String locationDef = WCS_URI
                    + " "
                    + buildURL(request.getBaseUrl(), appendPath(SCHEMAS,
                            "wcs/1.0.0/describeCoverage.xsd"), null, URLType.RESOURCE);
            attributes.addAttribute("", locationAtt, locationAtt, "", locationDef);

            attributes.addAttribute("", "version", "version", "", "1.0.0");

            start("wcs:CoverageDescription", attributes);
            for (Iterator it = request.getCoverage().iterator(); it.hasNext();) {
                String coverageName = (String) it.next();
                String coverageId = null;
                String fieldId = null;

                coverageId = coverageName.indexOf("@") > 0 ? coverageName.substring(0, coverageName
                        .indexOf("@")) : coverageName;
                fieldId = coverageName.indexOf("@") > 0 ? coverageName.substring(coverageName
                        .indexOf("@") + 1) : null;

                // check the coverage is known
                LayerInfo layer = catalog.getLayerByName(coverageId);
                if (layer == null || layer.getType() != LayerInfo.Type.RASTER) {
                    throw new WcsException("Could not find the specified coverage: " + coverageId,
                            WcsExceptionCode.InvalidParameterValue, "coverage");
                }

                CoverageInfo ci = catalog.getCoverageByName(coverageId);
                try {
                    handleCoverageOffering(ci);
                } catch (Exception e) {
                    throw new RuntimeException(
                            "Unexpected error occurred during describe coverage xml encoding", e);
                }

            }
            end("wcs:CoverageDescription");
        }

        private void handleCoverageOffering(CoverageInfo ci) throws Exception {
            start("wcs:CoverageOffering");
            for (MetadataLinkInfo mdl : ci.getMetadataLinks())
                handleMetadataLink(mdl);
            element("wcs:description", ci.getDescription());
            element("wcs:name", ci.getPrefixedName());
            element("wcs:label", ci.getTitle());
            handleLonLatEnvelope(ci, ci.getLatLonBoundingBox());
            handleKeywords(ci.getKeywords());

            handleDomain(ci);
            handleRange(ci);

            handleSupportedCRSs(ci);
            handleSupportedFormats(ci);
            handleSupportedInterpolations(ci);
            end("wcs:CoverageOffering");
        }

        /**
         * DOCUMENT ME!
         * 
         * @param metadataLink
         */
        private void handleMetadataLink(MetadataLinkInfo mdl) {
            if (mdl != null) {
                AttributesImpl attributes = new AttributesImpl();

                if ((mdl.getAbout() != null) && (mdl.getAbout() != "")) {
                    attributes.addAttribute("", "about", "about", "", mdl.getAbout());
                }

                if ((mdl.getMetadataType() != null) && (mdl.getMetadataType() != "")) {
                    attributes.addAttribute("", "metadataType", "metadataType", "", mdl
                            .getMetadataType());
                }

                if (attributes.getLength() > 0) {
                    element("wcs:metadataLink", mdl.getContent(), attributes);
                } else {
                    element("wcs:metadataLink", mdl.getContent());
                }
            }
        }

        /**
         * DOCUMENT ME!
         * 
         * @param lonLatEnvelope
         */
        private void handleLonLatEnvelope(CoverageInfo ci, ReferencedEnvelope referencedEnvelope) {

            CoverageStoreInfo csinfo = ci.getStore();
            
            if(csinfo == null)
                throw new WcsException("Unable to acquire coverage store resource for coverage: " + ci.getName());
            
            AbstractGridCoverage2DReader reader = null;
            try {
                reader = (AbstractGridCoverage2DReader) ci.getGridCoverageReader(null, null);
            } catch (IOException e) {
                LOGGER.severe("Unable to acquire a reader for this coverage with format: " + csinfo.getFormat().getName());
            }
            
            if(reader == null)
                throw new WcsException("Unable to acquire a reader for this coverage with format: " + csinfo.getFormat().getName());

            final String[] metadataNames = reader.getMetadataNames();
            

            String timeMetadata = null;
            if (metadataNames != null && metadataNames.length > 0) {
                // TIME DIMENSION
                timeMetadata = reader.getMetadataValue("TIME_DOMAIN");
                                
            }

            if (referencedEnvelope != null) {
                AttributesImpl attributes = new AttributesImpl();
                attributes.addAttribute("", "srsName", "srsName", "", /* "WGS84(DD)" */ "urn:ogc:def:crs:OGC:1.3:CRS84");

                start("wcs:lonLatEnvelope", attributes);

                final StringBuilder minCP = new StringBuilder(Double.toString(referencedEnvelope.getMinX())).append(" ").append(referencedEnvelope.getMinY());
                final StringBuilder maxCP = new StringBuilder(Double.toString(referencedEnvelope.getMaxX())).append(" ").append(referencedEnvelope.getMaxY());
                element("gml:pos", minCP.toString());
                element("gml:pos", maxCP.toString());

                if (timeMetadata != null && timeMetadata.length() > 0) {
                    String[] timePositions = timeMetadata.split(",");
                    element("gml:timePosition", timePositions[0]);
                    element("gml:timePosition", timePositions[timePositions.length - 1]);
                }

                end("wcs:lonLatEnvelope");
            }
        }

        /**
         * DOCUMENT ME!
         * 
         * @param kwords
         *            DOCUMENT ME!
         * 
         * @throws SAXException
         *             DOCUMENT ME!
         */
        private void handleKeywords(List kwords) {
            start("wcs:keywords");

            if (kwords != null) {
                for (Iterator it = kwords.iterator(); it.hasNext();) {
                    element("wcs:keyword", it.next().toString());
                }
            }

            end("wcs:keywords");
        }

        private void handleDomain(CoverageInfo ci) throws Exception {
            String timeMetadata = null;
            String elevationMetadata = null;

            CoverageStoreInfo csinfo = ci.getStore();
            
            if(csinfo == null)
                throw new WcsException("Unable to acquire coverage store resource for coverage: " + ci.getName());
            
            AbstractGridCoverage2DReader reader = null;
            try {
                reader = (AbstractGridCoverage2DReader) ci.getGridCoverageReader(null, null);
            } catch (IOException e) {
                LOGGER.severe("Unable to acquire a reader for this coverage with format: " + csinfo.getFormat().getName());
            }
            
            if(reader == null)
                throw new WcsException("Unable to acquire a reader for this coverage with format: " + csinfo.getFormat().getName());

            final String[] metadataNames = reader.getMetadataNames();
            
            if (metadataNames != null && metadataNames.length > 0) {
                // TIME DIMENSION
                timeMetadata = reader.getMetadataValue("TIME_DOMAIN");
                
                
            }

            start("wcs:domainSet");
            start("wcs:spatialDomain");
                handleBoundingBox(ci.getSRS(), ci.getNativeBoundingBox(), timeMetadata);
                handleGrid(ci);
            end("wcs:spatialDomain");
            if (timeMetadata != null && timeMetadata.length() > 0) {
                start("wcs:temporalDomain");
                    handleTemporalDomain(ci, timeMetadata);
                end("wcs:temporalDomain");
            }
            end("wcs:domainSet");
        }

        /**
         * DOCUMENT ME!
         * 
         * @param referencedEnvelope
         * @param elevationMetadata 
         * @param timeMetadata 
         * @param set2
         * @param set
         */
        private void handleBoundingBox(String srsName, ReferencedEnvelope referencedEnvelope, String timeMetadata) {
            if (referencedEnvelope != null) {
                AttributesImpl attributes = new AttributesImpl();
                attributes.addAttribute("", "srsName", "srsName", "", srsName);

                final StringBuilder minCP = new StringBuilder(Double.toString(referencedEnvelope.getMinX())).append(" ").append(referencedEnvelope.getMinY());
                final StringBuilder maxCP = new StringBuilder(Double.toString(referencedEnvelope.getMaxX())).append(" ").append(referencedEnvelope.getMaxY());

                
                if (timeMetadata != null && timeMetadata.length() > 0) {
                    start("gml:EnvelopeWithTimePeriod", attributes);
                        element("gml:pos", minCP.toString());
                        element("gml:pos", maxCP.toString());

                        final String[] timePositions = timeMetadata.split(",");
                        element("gml:timePosition", timePositions[0]);
                        element("gml:timePosition", timePositions[timePositions.length - 1]);
                    end("gml:EnvelopeWithTimePeriod");
                } else {
                    start("gml:Envelope", attributes);
                        element("gml:pos", minCP.toString());
                        element("gml:pos", maxCP.toString());
                    end("gml:Envelope");
                }
            }
        }

        /**
         * DOCUMENT ME!
         * 
         * @param ci
         * @param timeMetadata
         * @param elevationMetadata
         */
        private void handleTemporalDomain(CoverageInfo ci, String timeMetadata) {
            if (timeMetadata != null && timeMetadata.length() > 0) {
                final String[] timePositions = timeMetadata.split(",");
                for (String timePosition : timePositions) {
                    element("gml:timePosition", timePosition);
                }
            }
        }
        
        /**
         * 
         * @param ci
         * @param elevationMetadata 
         * @throws Exception
         */
        private void handleGrid(CoverageInfo ci) throws Exception {
        	final GridGeometry originalGrid = ci.getGrid();
        	final GridEnvelope gridRange=originalGrid.getGridRange();
        	final AffineTransform2D gridToCRS = (AffineTransform2D) originalGrid.getGridToCRS();
            final int gridDimension = (gridToCRS != null ? gridToCRS.getSourceDimensions() : 0);

            AttributesImpl attributes = new AttributesImpl();
            attributes.addAttribute("", "dimension", "dimension", "", String.valueOf(gridDimension));

            // RectifiedGrid
            start("gml:RectifiedGrid", attributes);
            
            // Grid Envelope
            String lowers = "";
            String uppers = "";
            for (int r = 0; r < gridDimension; r++) {
                if (gridToCRS.getSourceDimensions() > r) {
                    lowers += (gridRange.getLow(r) + " ");
                    uppers += (gridRange.getHigh(r) + " ");
                } else {
                    lowers += (0 + " ");
                    uppers += (0 + " ");
                }
            }
 
            start("gml:limits");
                start("gml:GridEnvelope");
                    element("gml:low", lowers.trim());
                    element("gml:high", uppers.trim());
                end("gml:GridEnvelope");
            end("gml:limits");

            // Grid Axes
            for (int dn = 0; dn < ci.getCRS().getCoordinateSystem().getDimension(); dn++) {
                String axisName = ci.getCRS().getCoordinateSystem().getAxis(dn).getAbbreviation();
                axisName = axisName.toLowerCase().startsWith("lon") ? "x" : axisName;
                axisName = axisName.toLowerCase().startsWith("lat") ? "y" : axisName;
                element("gml:axisName", axisName);
            }
   

            
            // Grid Origins
            final StringBuilder origins = new StringBuilder();
            origins.append(gridToCRS.getTranslateX()).append(" ").append(gridToCRS.getTranslateY());
            start("gml:origin");
                element("gml:pos", origins.toString());
            end("gml:origin");
            
            // Grid Offsets
            final StringBuilder offsetX = new StringBuilder();
            offsetX.append(gridToCRS.getScaleX()).append(" ").append(gridToCRS.getShearX());
            element("gml:offsetVector", offsetX.toString());
            final StringBuilder offsetY = new StringBuilder();
            offsetY.append(gridToCRS.getShearY()).append(" ").append(gridToCRS.getScaleY());
            element("gml:offsetVector", offsetY.toString());

            end("gml:RectifiedGrid");
        }

        /**
         * 
         * @param ci
         * @param field
         */
        private void handleRange(CoverageInfo ci) {
            // rangeSet
            start("wcs:rangeSet");
            start("wcs:RangeSet");
            element("wcs:name", ci.getName());
            element("wcs:label", ci.getTitle());

            start("wcs:axisDescription");
            start("wcs:AxisDescription");

            //
            // STANDARD BANDS
            //
            int numSampleDimensions = ci.getDimensions().size();
            


            element("wcs:name", "Band");
            element("wcs:label", "Band");
            start("wcs:values");
            if (numSampleDimensions > 1) {
                start("wcs:interval");
                element("wcs:min", "1");
                element("wcs:max", String.valueOf(numSampleDimensions));
                end("wcs:interval");
            } else {
                element("wcs:singleValue", "1");
            }
            end("wcs:values");

            end("wcs:AxisDescription");
            
            // 
            // ELEVATION
            //
            // now get possible elevation
            AbstractGridCoverage2DReader reader = null;
            try {
                reader = (AbstractGridCoverage2DReader) ci.getGridCoverageReader(null, null);
            } catch (IOException e) {
                LOGGER.severe("Unable to acquire a reader for this coverage with format: " + ci.getStore().getFormat().getName());
            }            
            if(reader == null)
                throw new WcsException("Unable to acquire a reader for this coverage with format: " + ci.getStore().getFormat().getName());

            final String[] metadataNames = reader.getMetadataNames();
            String elevationMetadata=null;
            if (metadataNames != null && metadataNames.length > 0) {
                // TIME DIMENSION
                elevationMetadata = reader.getMetadataValue("ELEVATION_DOMAIN"); 
                
                start("wcs:AxisDescription");
                element("wcs:name", "ELEVATION");
                element("wcs:label", "ELEVATION");
                start("wcs:values");
                
                final String [] values=elevationMetadata.split(",");
                for(String s:values){
                	element("wcs:singleValue", s);
                }
                element("wcs:default", values[0]);
               
               
                end("wcs:values");

                end("wcs:AxisDescription");
                
            }
                        
            end("wcs:axisDescription");
            end("wcs:RangeSet");
            end("wcs:rangeSet");
        }

        /**
         * DOCUMENT ME!
         * 
         * @param ci
         * @throws Exception
         */
        private void handleSupportedCRSs(CoverageInfo ci) throws Exception {
            Set supportedCRSs = new LinkedHashSet();
            if (ci.getRequestSRS() != null)
                supportedCRSs.addAll(ci.getRequestSRS());
            if (ci.getResponseSRS() != null)
                supportedCRSs.addAll(ci.getResponseSRS());
            start("wcs:supportedCRSs");
            for (Iterator it = supportedCRSs.iterator(); it.hasNext();) {
                String crsName = (String) it.next();
                CoordinateReferenceSystem crs = CRS.decode(crsName, true);
                // element("requestResponseCRSs", urnIdentifier(crs));
                element("wcs:requestResponseCRSs", CRS.lookupIdentifier(crs, false));
            }
            end("wcs:supportedCRSs");
        }

        private String urnIdentifier(final CoordinateReferenceSystem crs) throws FactoryException {
            String authorityAndCode = CRS.lookupIdentifier(crs, false);
            String code = authorityAndCode.substring(authorityAndCode.lastIndexOf(":") + 1);
            // we don't specify the version, but we still need to put a space
            // for it in the urn form, that's why we have :: before the code
            return "urn:ogc:def:crs:EPSG::" + code;
        }

        /**
         * DOCUMENT ME!
         * 
         * @param ci
         * @throws Exception
         */
        private void handleSupportedFormats(CoverageInfo ci) throws Exception {
            final String nativeFormat = (((ci.getNativeFormat() != null) && ci.getNativeFormat()
                    .equalsIgnoreCase("GEOTIFF")) ? "GeoTIFF" : ci.getNativeFormat());

            final AttributesImpl attributes = new AttributesImpl();
            attributes.addAttribute("", "nativeFormat", "nativeFormat", "", nativeFormat);

            // gather all the formats for this coverage
            Set<String> formats = new HashSet<String>();
            for (Iterator it = ci.getSupportedFormats().iterator(); it.hasNext();) {
                String format = (String) it.next();
                formats.add(format);
            }
            // sort them
            start("wcs:supportedFormats", attributes);
            List<String> sortedFormats = new ArrayList<String>(formats);
            Collections.sort(sortedFormats);
            for (String format : sortedFormats) {
                element("wcs:formats", format.equalsIgnoreCase("GEOTIFF") ? "GeoTIFF" : format);
            }
            end("wcs:supportedFormats");
        }

        /**
         * DOCUMENT ME!
         * 
         * @param ci
         */
        private void handleSupportedInterpolations(CoverageInfo ci) {
            if (ci.getDefaultInterpolationMethod() != null) {
                final AttributesImpl attributes = new AttributesImpl();
                attributes.addAttribute("", "default", "default", "", ci.getDefaultInterpolationMethod());

                start("wcs:supportedInterpolations", attributes);
            } else {
                start("wcs:supportedInterpolations");
            }
            
            for (Iterator it = ci.getInterpolationMethods().iterator(); it.hasNext();) {
                String method = (String) it.next();
                String converted = METHOD_NAME_MAP.get(method);
                if (/* converted */ method != null)
                    element("wcs:interpolationMethod", /* converted */ method);

            }
            end("wcs:supportedInterpolations");
        }
    }


   

}