/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org.  All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.vfny.geoserver.wms.responses.helpers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;

import org.apache.xalan.transformer.TransformerIdentityImpl;
import org.geoserver.ows.util.RequestUtils;
import org.geotools.geometry.GeneralEnvelope;
import org.geotools.referencing.CRS;
import org.geotools.referencing.wkt.UnformattableObjectException;
import org.geotools.styling.Style;
import org.geotools.xml.transform.TransformerBase;
import org.geotools.xml.transform.Translator;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.vfny.geoserver.global.CoverageInfo;
import org.vfny.geoserver.global.CoverageInfoLabelComparator;
import org.vfny.geoserver.global.Data;
import org.vfny.geoserver.global.FeatureTypeInfo;
import org.vfny.geoserver.global.FeatureTypeInfoTitleComparator;
import org.vfny.geoserver.global.GeoServer;
import org.vfny.geoserver.global.LegendURL;
import org.vfny.geoserver.global.MetaDataLink;
import org.vfny.geoserver.global.WMS;
import org.vfny.geoserver.util.requests.CapabilitiesRequest;
import org.vfny.geoserver.wms.WmsException;
import org.vfny.geoserver.wms.requests.GetLegendGraphicRequest;
import org.vfny.geoserver.wms.requests.WMSCapabilitiesRequest;
import org.vfny.geoserver.wms.responses.DescribeLayerResponse;
import org.vfny.geoserver.wms.responses.GetFeatureInfoResponse;
import org.vfny.geoserver.wms.responses.GetLegendGraphicResponse;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.AttributesImpl;

import com.vividsolutions.jts.geom.Envelope;


/**
 * Geotools xml framework based encoder for a Capabilities WMS 1.1.1 document.
 *
 * @author Gabriel Roldan, Axios Engineering
 * @version $Id
 */
public class WMSCapsTransformer extends TransformerBase {
    /** fixed MIME type for the returned capabilities document */
    public static final String WMS_CAPS_MIME = "application/vnd.ogc.wms_xml";

    /** The geoserver base URL to append it the schemas/wms/1.1.1/WMS_MS_Capabilities.dtd DTD location */
    private String schemaBaseUrl;

    /** The list of output formats to state as supported for the GetMap request */
    private Set<String> getMapFormats;

    /** The list of output formats to state as supported for the GetLegendGraphic request */
    private Set<String> getLegendGraphicFormats;

    /**
     * Creates a new WMSCapsTransformer object.
     *
     * @param schemaBaseUrl
     *            the server base url which to append the "schemas/wms/1.1.1/WMS_MS_Capabilities.dtd" 
     *            DTD location to
     * @param getMapFormats the list of supported output formats to state for the GetMap request
     * @param getLegendGraphicFormats the list of supported output formats to state for the 
     *          GetLegendGraphic request
     *
     * @throws NullPointerException
     *             if <code>schemaBaseUrl</code> is null;
     */
    public WMSCapsTransformer(String schemaBaseUrl, Set<String> getMapFormats, Set<String> getLegendGraphicFormats) {
        super();
        if (schemaBaseUrl == null) {
            throw new NullPointerException("schemaBaseUrl");
        }
        if (getMapFormats == null) {
            throw new NullPointerException("getMapFormats");
        }
        if (getLegendGraphicFormats == null) {
            throw new NullPointerException("getLegendGraphicFormats");
        }

        this.getMapFormats = getMapFormats;
        this.getLegendGraphicFormats = getLegendGraphicFormats;
        this.schemaBaseUrl = schemaBaseUrl;
        this.setNamespaceDeclarationEnabled(false);
    }

    /**
     * DOCUMENT ME!
     *
     * @param handler
     *            DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    public Translator createTranslator(ContentHandler handler) {
        return new CapabilitiesTranslator(handler, getMapFormats, getLegendGraphicFormats);
    }

    /**
     * Gets the <code>Transformer</code> created by the overriden method in
     * the superclass and adds it the system DOCTYPE token pointing to the
     * Capabilities DTD on this server instance.
     *
     * <p>
     * The DTD is set at the fixed location given by the
     * <code>schemaBaseUrl</code> passed to the constructor <code>+
     * "wms/1.1.1/WMS_MS_Capabilities.dtd</code>.
     * </p>
     *
     * @return a Transformer propoerly configured to produce DescribeLayer
     *         responses.
     *
     * @throws TransformerException
     *             if it is thrown by <code>super.createTransformer()</code>
     */
    public Transformer createTransformer() throws TransformerException {
        Transformer transformer = super.createTransformer();
        String dtdUrl = schemaBaseUrl + (schemaBaseUrl.endsWith("/")? "" : "/") +
            "schemas/wms/1.1.1/WMS_MS_Capabilities.dtd"; // DJB: fixed this to
                                                                                  // point to correct
                                                                                  // location

        transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, dtdUrl);

        return transformer;
    }

    /**
     * DOCUMENT ME!
     *
     * @author Gabriel Roldan, Axios Engineering
     * @version $Id
     */
    private static class CapabilitiesTranslator extends TranslatorSupport {
        /** DOCUMENT ME! */
        private static final Logger LOGGER = org.geotools.util.logging.Logging.getLogger(CapabilitiesTranslator.class.getPackage()
                                                                                          .getName());

        /** DOCUMENT ME! */
        private static final String EPSG = "EPSG:";

        /** DOCUMENT ME! */
        private static AttributesImpl wmsVersion = new AttributesImpl();

        /** DOCUMENT ME! */
        private static final String XLINK_NS = "http://www.w3.org/1999/xlink";

        static {
            wmsVersion.addAttribute("", "version", "version", "", "1.1.1");
        }

        /**
         * The request from wich all the information needed to produce the
         * capabilities document can be obtained
         */
        private CapabilitiesRequest request;
        private Set getMapFormats;

        private Set<String> getLegendGraphicFormats;

        /**
         * Creates a new CapabilitiesTranslator object.
         *
         * @param handler
         *            content handler to send sax events to.
         */
        public CapabilitiesTranslator(ContentHandler handler, Set getMapFormats,
            Set<String> getLegendGraphicFormats) {
            super(handler, null, null);
            this.getMapFormats = getMapFormats;
            this.getLegendGraphicFormats = getLegendGraphicFormats;
        }

        /**
         * DOCUMENT ME!
         *
         * @param o the {@link WMSCapabilitiesRequest}
         * @throws IllegalArgumentException if {@code o} is not of the expected type
         */
        public void encode(Object o) throws IllegalArgumentException {
            if (!(o instanceof CapabilitiesRequest)) {
                throw new IllegalArgumentException();
            }

            this.request = (CapabilitiesRequest) o;

            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(new StringBuffer("producing a capabilities document for ").append(
                        request).toString());
            }

            WMS wms = (WMS) request.getServiceConfig();
            AttributesImpl rootAtts = new AttributesImpl(wmsVersion);
            rootAtts.addAttribute("", "updateSequence", "updateSequence", "", wms.getGeoServer().getUpdateSequence() + "");
            start("WMT_MS_Capabilities", rootAtts);
            handleService();
            handleCapability();
            end("WMT_MS_Capabilities");
        }

        /**
         * Encodes the service metadata section of a WMS capabilities document.
         */
        private void handleService() {
            WMS wms = (WMS) request.getServiceConfig();
            start("Service");

            element("Name", "OGC:WMS");
            element("Title", wms.getTitle());
            element("Abstract", wms.getAbstract());

            handleKeywordList(wms.getKeywords());

            AttributesImpl orAtts = new AttributesImpl();
            orAtts.addAttribute("", "xmlns:xlink", "xmlns:xlink", "", XLINK_NS);
            orAtts.addAttribute(XLINK_NS, "xlink:type", "xlink:type", "", "simple");
            orAtts.addAttribute("", "xlink:href", "xlink:href", "",
                RequestUtils.proxifiedBaseURL(request.getBaseUrl(),wms.getGeoServer().getProxyBaseUrl()) + "wms");
            element("OnlineResource", null, orAtts);

            handleContactInfo(wms.getGeoServer());

            element("Fees", wms.getFees());
            element("AccessConstraints", wms.getAccessConstraints());
            end("Service");
        }

        /**
         * Encodes contact information in the WMS capabilities document
         * @param geoServer
         */
        public void handleContactInfo(GeoServer geoServer) {
            start("ContactInformation");

            start("ContactPersonPrimary");
            element("ContactPerson", geoServer.getContactPerson());
            element("ContactOrganization", geoServer.getContactOrganization());
            end("ContactPersonPrimary");

            element("ContactPosition", geoServer.getContactPosition());

            start("ContactAddress");
            element("AddressType", geoServer.getAddressType());
            element("Address", geoServer.getAddress());
            element("City", geoServer.getAddressCity());
            element("StateOrProvince", geoServer.getAddressState());
            element("PostCode", geoServer.getAddressPostalCode());
            element("Country", geoServer.getAddressCountry());
            end("ContactAddress");

            element("ContactVoiceTelephone", geoServer.getContactVoice());
            element("ContactFacsimileTelephone", geoServer.getContactFacsimile());
            element("ContactElectronicMailAddress", geoServer.getContactEmail());

            end("ContactInformation");
        }

        /**
         * Turns the keyword list to XML
         *
         * @param keywords
         */
        private void handleKeywordList(List keywords) {
            start("KeywordList");

            if (keywords != null) {
                for (Iterator it = keywords.iterator(); it.hasNext();) {
                    element("Keyword", String.valueOf(it.next()));
                }
            }

            end("KeywordList");
        }

        /**
         * Turns the metadata URL list to XML
         *
         * @param keywords
         */
        private void handleMetadataList(List metadataURLs) {
            if (metadataURLs == null) {
                return;
            }

            for (Iterator it = metadataURLs.iterator(); it.hasNext();) {
                MetaDataLink link = (MetaDataLink) it.next();

                AttributesImpl lnkAtts = new AttributesImpl();
                lnkAtts.addAttribute("", "type", "type", "", link.getMetadataType());
                start("MetadataURL", lnkAtts);

                element("Format", link.getType());

                AttributesImpl orAtts = new AttributesImpl();
                orAtts.addAttribute("", "xmlns:xlink", "xmlns:xlink", "", XLINK_NS);
                orAtts.addAttribute(XLINK_NS, "xlink:type", "xlink:type", "", "simple");
                orAtts.addAttribute("", "xlink:href", "xlink:href", "", link.getContent());
                element("OnlineResource", null, orAtts);

                end("MetadataURL");
            }
        }

        /**
         * Encodes the capabilities metadata section of a WMS capabilities
         * document
         */
        private void handleCapability() {
            start("Capability");
            handleRequest();
            handleException();
            handleSLD();
            handleLayers();
            end("Capability");
        }

        /**
         * DOCUMENT ME!
         */
        private void handleRequest() {
            start("Request");

            start("GetCapabilities");
            element("Format", WMS_CAPS_MIME);
            
            String serviceUrl = 
                RequestUtils.proxifiedBaseURL(request.getBaseUrl(), request.getServiceConfig().getGeoServer().getProxyBaseUrl()) +
                "wms?SERVICE=WMS&";

            handleDcpType(serviceUrl, serviceUrl);
            end("GetCapabilities");

            start("GetMap");

            List sortedFormats = new ArrayList(getMapFormats);
            Collections.sort(sortedFormats);
            // this is a hack necessary to make cite tests pass: we need an output format
            // that is equal to the mime type as the first one....
            if( sortedFormats.contains("image/png")) {
                sortedFormats.remove("image/png");
                sortedFormats.add(0, "image/png");
            }
            for (Iterator it = sortedFormats.iterator(); it.hasNext();) {
                element("Format", String.valueOf(it.next()));
            }

            handleDcpType(serviceUrl, null);
            end("GetMap");

            start("GetFeatureInfo");

            for (Iterator it = GetFeatureInfoResponse.getFormats().iterator(); it.hasNext();) {
                element("Format", String.valueOf(it.next()));
            }

            handleDcpType(serviceUrl, serviceUrl);
            end("GetFeatureInfo");

            start("DescribeLayer");
            element("Format", DescribeLayerResponse.DESCLAYER_MIME_TYPE);
            handleDcpType(serviceUrl, null);
            end("DescribeLayer");

            start("GetLegendGraphic");

            for (Iterator it = getLegendGraphicFormats.iterator();
                    it.hasNext();) {
                element("Format", String.valueOf(it.next()));
            }

            handleDcpType(serviceUrl, null);
            end("GetLegendGraphic");

            end("Request");
        }

        /**
         * Encodes a <code>DCPType</code> fragment for HTTP GET and POST
         * methods.
         *
         * @param getUrl the URL of the onlineresource for HTTP GET method
         *        requests
         * @param postUrl the URL of the onlineresource for HTTP POST method
         *        requests
         */
        private void handleDcpType(String getUrl, String postUrl) {
            AttributesImpl orAtts = new AttributesImpl();
            orAtts.addAttribute("", "xmlns:xlink", "xmlns:xlink", "", XLINK_NS);
            orAtts.addAttribute("", "xlink:type", "xlink:type", "", "simple");
            orAtts.addAttribute("", "xlink:href", "xlink:href", "", getUrl);
            start("DCPType");
            start("HTTP");

            if (getUrl != null) {
                start("Get");
                element("OnlineResource", null, orAtts);
                end("Get");
            }

            if (postUrl != null) {
                orAtts.setAttribute(2, "", "xlink:href", "xlink:href", "", postUrl);
                start("Post");
                element("OnlineResource", null, orAtts);
                end("Post");
            }

            end("HTTP");
            end("DCPType");
        }

        /**
         * DOCUMENT ME!
         */
        private void handleException() {
            start("Exception");

            WMS wms = (WMS) request.getServiceConfig();
            Iterator it = Arrays.asList(wms.getExceptionFormats()).iterator();

            while (it.hasNext()) {
                element("Format", String.valueOf(it.next()));
            }

            end("Exception");
        }

        /**
         * DOCUMENT ME!
         */
        private void handleSLD() {
            AttributesImpl sldAtts = new AttributesImpl();
            WMS config = (WMS) request.getServiceConfig();
            String supportsSLD = config.supportsSLD() ? "1" : "0";
            String supportsUserLayer = config.supportsUserLayer() ? "1" : "0";
            String supportsUserStyle = config.supportsUserStyle() ? "1" : "0";
            String supportsRemoteWFS = config.supportsRemoteWFS() ? "1" : "0";
            sldAtts.addAttribute("", "SupportSLD", "SupportSLD", "", supportsSLD);
            sldAtts.addAttribute("", "UserLayer", "UserLayer", "", supportsUserLayer);
            sldAtts.addAttribute("", "UserStyle", "UserStyle", "", supportsUserStyle);
            sldAtts.addAttribute("", "RemoteWFS", "RemoteWFS", "", supportsRemoteWFS);

            start("UserDefinedSymbolization", sldAtts);
            //          djb: this was removed, even though they are correct - the CITE tests have an incorrect DTD
            //       element("SupportedSLDVersion","1.0.0");  //djb: added that we support this.  We support partial 1.1
            end("UserDefinedSymbolization");

            //element("UserDefinedSymbolization", null, sldAtts);
        }

        /**
         * Handles the encoding of the layers elements.
         *
         * <p>
         * This method does a search over the SRS of all the layers to see if
         * there are at least a common one, as needed by the spec: "<i>The root
         * Layer element shall include a sequence of zero or more &lt;SRS&gt;
         * elements listing all SRSes that are common to all subsidiary layers.
         * Use a single SRS element with empty content (like so:
         * "&lt;SRS&gt;&lt;/SRS&gt;") if there is no common SRS."</i>
         * </p>
         *
         * <p>
         * By the other hand, this search is also used to collecto the whole
         * latlon bbox, as stated by the spec: <i>"The bounding box metadata in
         * Capabilities XML specify the minimum enclosing rectangle for the
         * layer as a whole."</i>
         * </p>
         *
         * @task TODO: manage this differently when we have the layer list of
         *       the WMS service decoupled from the feature types configured for
         *       the server instance. (This involves nested layers,
         *       gridcoverages, etc)
         */
        private void handleLayers() {
            WMS wms = (WMS) request.getServiceConfig();
            start("Layer");

            Data catalog = wms.getData();
            List ftypes = new ArrayList(catalog.getFeatureTypeInfos().values());
            List coverages = new ArrayList(catalog.getCoverageInfos().values());
            
            // filter the layers if a namespace filter has been set
            if(request.getNamespace() != null) {
                String namespace = request.getNamespace();
                for (Iterator it = ftypes.iterator(); it.hasNext();) {
                    FeatureTypeInfo ft = (FeatureTypeInfo) it.next();
                    if(!namespace.equals(ft.getNameSpace().getPrefix()))
                        it.remove();
                }
                for (Iterator it = coverages.iterator(); it.hasNext();) {
                    CoverageInfo cv = (CoverageInfo) it.next();
                    if(!namespace.equals(cv.getNameSpace().getPrefix()))
                        it.remove();
                }
            }

            element("Title", wms.getTitle());
            element("Abstract", wms.getAbstract());
            
            handleRootCrsList(wms.getCapabilitiesCrsList());
            
            List layers = new ArrayList(ftypes.size() + coverages.size());
            layers.addAll(ftypes);
            layers.addAll(coverages);
            handleRootBbox(layers);
            
            // now encode each layer individually
            LayerTree featuresLayerTree = new LayerTree(ftypes);
            handleFeaturesTree(featuresLayerTree);

            LayerTree coveragesLayerTree = new LayerTree(coverages);
            handleCoveragesTree(coveragesLayerTree);

            try {
                handleLayerGroups(wms.getBaseMapLayers(), wms.getBaseMapStyles(),
                    wms.getBaseMapEnvelopes());
            } catch (FactoryException e) {
                throw new RuntimeException("Can't obtain Envelope of Layer-Groups: "
                    + e.getMessage(), e);
            } catch (TransformException e) {
                throw new RuntimeException("Can't obtain Envelope of Layer-Groups: "
                    + e.getMessage(), e);
            }

            end("Layer");
        }
        

        /**
         * Called by <code>handleLayers()</code>, writes down list of
         * supported CRS's for the root Layer.
         * <p>
         * If <code>epsgCodes</code> is not empty, the list of supported CRS
         * identifiers written down to the capabilities document is limited to
         * those in the <code>epsgCodes</code> list. Otherwise, all the
         * GeoServer supported CRS identifiers are used.
         * </p>
         * 
         * @param epsgCodes
         *            possibly empty set of CRS identifiers to limit the number
         *            of SRS elements to.
         */
        private void handleRootCrsList(final Set epsgCodes) {
            final Set capabilitiesCrsIdentifiers;
            if(epsgCodes.isEmpty()){
                comment("All supported EPSG projections:");
                capabilitiesCrsIdentifiers = CRS.getSupportedCodes("EPSG");
            }else{
                comment("Limited list of EPSG projections:");
                capabilitiesCrsIdentifiers = new TreeSet(epsgCodes);
            }
            
            try {
                Iterator it = capabilitiesCrsIdentifiers.iterator();
                String currentSRS;

                while (it.hasNext()) {
                    currentSRS = it.next().toString();
                    if(currentSRS.indexOf(':') == -1){
                        currentSRS = EPSG + currentSRS;
                    }
                    element("SRS", currentSRS);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, e.getLocalizedMessage(), e);
            }
        }

        /**
         * Called by <code>handleLayers()</code>, iterates over the available
         * featuretypes and coverages to summarize their LatLonBBox'es and write
         * the aggregated bounds for the root layer.
         * 
         * @param ftypes
         *            the collection of FeatureTypeInfo and CoverageInfo objects
         *            to traverse
         */
        private void handleRootBbox(Collection ftypes) {
            Envelope latlonBbox = new Envelope();
            Envelope layerBbox = null;

            LOGGER.finer("Collecting summarized latlonbbox and common SRS...");

                FeatureTypeInfo vectorLayer;
                CoverageInfo rasterLayer;

            for (Iterator it = ftypes.iterator(); it.hasNext();) {
                Object layer = it.next();
                if(layer instanceof FeatureTypeInfo){
                    vectorLayer = (FeatureTypeInfo) layer;
                    if(!vectorLayer.isEnabled()){
                        continue;
                    }
                    try {
                        layerBbox = vectorLayer.getLatLongBoundingBox();
                    } catch (IOException e) {
                        throw new RuntimeException("Can't obtain latLonBBox of "
                            + vectorLayer.getName() + ": " + e.getMessage(), e);
                    }
                }else{
                    rasterLayer = (CoverageInfo) layer;
                    if(!rasterLayer.isEnabled()){
                        continue;
                    }
                    final GeneralEnvelope bbox = rasterLayer.getWGS84LonLatEnvelope();
                    if(bbox != null){
                        layerBbox = new Envelope(bbox.getLowerCorner().getOrdinate(0),
                                bbox.getUpperCorner().getOrdinate(0),
                                bbox.getLowerCorner().getOrdinate(1),
                                bbox.getUpperCorner().getOrdinate(1));
                    }
                }
                latlonBbox.expandToInclude(layerBbox);
            }

            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Summarized LatLonBBox is " + latlonBbox);
            }

            handleLatLonBBox(latlonBbox);
        }

        /**
         * @param featuresLayerTree
         */
        private void handleFeaturesTree(LayerTree featuresLayerTree) {
            final List data = new ArrayList(featuresLayerTree.getData());
            final Collection childrens = featuresLayerTree.getChildrens();
            FeatureTypeInfo fLayer;

            Collections.sort(data, new FeatureTypeInfoTitleComparator());
            for (Iterator it = data.iterator(); it.hasNext();) {
                fLayer = (FeatureTypeInfo) it.next();
                
                boolean geometryless = true;
                try {
                    geometryless = fLayer.isGeometryless();
                } catch(Exception e) {
                    LOGGER.log(Level.SEVERE, "An error occurred trying to determine if the layer is geometryless", e);
                }

                if (fLayer.isEnabled() && !geometryless) {
                    handleFeatureType(fLayer);
                }
            }

            LayerTree layerTree;

            for (Iterator it = childrens.iterator(); it.hasNext();) {
                layerTree = (LayerTree) it.next();
                start("Layer");
                element("Name", layerTree.getName());
                element("Title", layerTree.getName());
                handleFeaturesTree(layerTree);
                end("Layer");
            }
        }

        /**
         * Calls super.handleFeatureType to add common FeatureType content such
         * as Name, Title and LatLonBoundingBox, and then writes WMS specific
         * layer properties as Styles, Scale Hint, etc.
         *
         * @param ftype
         *            The featureType to write out.
         *
         * @throws RuntimeException
         *             DOCUMENT ME!
         *
         * @task TODO: write wms specific elements.
         */
        protected void handleFeatureType(FeatureTypeInfo ftype) {
            // HACK: by now all our layers are queryable, since they reference
            // only featuretypes managed by this server
            AttributesImpl qatts = new AttributesImpl();
            qatts.addAttribute("", "queryable", "queryable", "", "1");
            start("Layer", qatts);
            element("Name", ftype.getName());
            element("Title", ftype.getTitle());
            element("Abstract", ftype.getAbstract());

            handleKeywordList(ftype.getKeywords());

            /**
             * @task REVISIT: should getSRS() return the full URL? no - the spec
             *       says it should be a set of <SRS>EPSG:#</SRS>...
             */
            element("SRS", EPSG + ftype.getSRS());

            // DJB: I want to be nice to the people reading the capabilities
            // file - I'm going to get the
            // human readable name and stick it in the capabilities file
            // NOTE: this isnt well done because "comment()" isnt in the
            // ContentHandler interface...
            try {
                CoordinateReferenceSystem crs = CRS.decode(EPSG + ftype.getSRS());
                String desc = "WKT definition of this CRS:\n" + crs;
                comment(desc);
            } catch (Exception e) {
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.log(Level.WARNING, e.getLocalizedMessage(), e);
                }
            }

            Envelope bbox = null;
            Envelope llbbox = null;

            try {
                bbox = ftype.getBoundingBox();
                llbbox = ftype.getLatLongBoundingBox();
            } catch (IOException ex) {
                throw new RuntimeException("Can't obtain latLongBBox of " + ftype.getName() + ": "
                    + ex.getMessage(), ex);
            }

            handleLatLonBBox(llbbox);
            // the native bbox might be null
            if(bbox != null)
                handleBBox(bbox, EPSG + ftype.getSRS());
            // handle metadata URLs
            handleMetadataList(ftype.getMetadataLinks());

            // add the layer style
            start("Style");

            Style ftStyle = ftype.getDefaultStyle();
            element("Name", ftStyle.getName());
            element("Title", ftStyle.getTitle());
            element("Abstract", ftStyle.getAbstract());
            handleLegendURL(ftype);
            end("Style");

            final ArrayList styles = ftype.getStyles();
            Iterator s_IT = styles.iterator();

            while (s_IT.hasNext()) {
                ftStyle = (Style) s_IT.next();
                start("Style");
                element("Name", ftStyle.getName());
                element("Title", ftStyle.getTitle());
                element("Abstract", ftStyle.getAbstract());
                handleLegendURL(ftype);
                end("Style");
            }

            end("Layer");
        }

        /**
         * @param coveragesLayerTree
         */
        private void handleCoveragesTree(LayerTree coveragesLayerTree) {
            final List data = new ArrayList(coveragesLayerTree.getData());
            final Collection childrens = coveragesLayerTree.getChildrens();
            CoverageInfo cLayer;

            Collections.sort(data, new CoverageInfoLabelComparator());
            for (Iterator it = data.iterator(); it.hasNext();) {
                cLayer = (CoverageInfo) it.next();

                if (cLayer.isEnabled()) {
                    handleCoverage(cLayer);
                }
            }

            LayerTree layerTree;

            for (Iterator it = childrens.iterator(); it.hasNext();) {
                layerTree = (LayerTree) it.next();
                start("Layer");
                element("Name", layerTree.getName());
                element("Title", layerTree.getName());
                handleCoveragesTree(layerTree);
                end("Layer");
            }
        }

        protected void handleCoverage(CoverageInfo coverage) {
            // HACK: by now all our layers are queryable, since they reference
            // only featuretypes managed by this server
            AttributesImpl qatts = new AttributesImpl();
            qatts.addAttribute("", "queryable", "queryable", "", "1");
            // qatts.addAttribute("", "opaque", "opaque", "", "1");
            // qatts.addAttribute("", "cascaded", "cascaded", "", "1");
            start("Layer", qatts);
            element("Name", coverage.getName());
            element("Title", coverage.getLabel());
            element("Abstract", coverage.getDescription());

            handleKeywordList(coverage.getKeywords());

            CoordinateReferenceSystem crs = coverage.getCrs();
            String desc;
            try{
                String publishedCoverageCrsWKT = crs.toWKT();
                desc = "WKT definition of this CRS:\n" + publishedCoverageCrsWKT;
            }catch(UnformattableObjectException e){
                desc = "Unable to get the WKT representation for the coverage crs: " + coverage.getSrsName();
            }
            comment(desc);

            String authority = coverage.getSrsName();

            /*CoordinateReferenceSystem crs = coverage.getCrs();
            if (crs != null && !crs.getIdentifiers().isEmpty()) {
                    Identifier[] idents = (Identifier[]) crs.getIdentifiers()
                                    .toArray(new Identifier[crs.getIdentifiers().size()]);
                    authority = idents[0].toString();
            } else if (crs != null && crs instanceof DerivedCRS) {
                    final CoordinateReferenceSystem baseCRS = ((DerivedCRS) crs)
                                    .getBaseCRS();
                    if (baseCRS != null && !baseCRS.getIdentifiers().isEmpty())
                            authority = ((Identifier[]) baseCRS.getIdentifiers()
                                            .toArray(
                                                            new Identifier[baseCRS.getIdentifiers()
                                                                            .size()]))[0].toString();
                    else
                            authority = coverage.getNativeCRS();
            } else if (crs != null && crs instanceof ProjectedCRS) {
                    final CoordinateReferenceSystem baseCRS = ((ProjectedCRS) crs)
                                    .getBaseCRS();
                    if (baseCRS != null && !baseCRS.getIdentifiers().isEmpty())
                            authority = ((Identifier[]) baseCRS.getIdentifiers()
                                            .toArray(
                                                            new Identifier[baseCRS.getIdentifiers()
                                                                            .size()]))[0].toString();
                    else
                            authority = coverage.getNativeCRS();
            } else
                    authority = coverage.getNativeCRS();*/
            element("SRS", authority);

            GeneralEnvelope bounds = null;
            GeneralEnvelope llBounds = null;

            // try {
            // We need LON/LAT Envelopes
            // TODO check for BBOX, maybe it should be expressed in original
            // CRS coords!!
            final GeneralEnvelope latLonEnvelope = coverage.getWGS84LonLatEnvelope();
            // final CoordinateReferenceSystem llCRS = latLonEnvelope
            // .getCoordinateReferenceSystem();
            bounds = coverage.getEnvelope();
            llBounds = latLonEnvelope;

            // bounds =
            // CoverageStoreUtils.adjustEnvelopeLongitudeFirst(llCRS,
            // coverage.getEnvelope());
            // llBounds =
            // CoverageStoreUtils.adjustEnvelopeLongitudeFirst(llCRS,
            // // latLonEnvelope);
            // } catch (MismatchedDimensionException e) {
            //	
            // } catch (IndexOutOfBoundsException e) {
            //			
            // }
            final Envelope bbox = new Envelope(bounds.getLowerCorner().getOrdinate(0),
                    bounds.getUpperCorner().getOrdinate(0), bounds.getLowerCorner().getOrdinate(1),
                    bounds.getUpperCorner().getOrdinate(1));

            final Envelope llBbox = new Envelope(llBounds.getLowerCorner().getOrdinate(0),
                    llBounds.getUpperCorner().getOrdinate(0),
                    llBounds.getLowerCorner().getOrdinate(1),
                    llBounds.getUpperCorner().getOrdinate(1));

            handleLatLonBBox(llBbox);
            handleBBox(bbox, authority);

            // add the layer style
            start("Style");

            Style cvStyle = coverage.getDefaultStyle();
            element("Name", cvStyle.getName());
            element("Title", cvStyle.getTitle());
            element("Abstract", cvStyle.getAbstract());
            handleLegendURL(coverage);
            end("Style");

            final ArrayList styles = coverage.getStyles();
            Iterator s_IT = styles.iterator();

            while (s_IT.hasNext()) {
                cvStyle = (Style) s_IT.next();
                start("Style");
                element("Name", cvStyle.getName());
                element("Title", cvStyle.getTitle());
                element("Abstract", cvStyle.getAbstract());
                handleLegendURL(coverage);
                end("Style");
            }

            end("Layer");
        }
        
        protected void handleLayerGroups(Map baseMapLayers, Map baseMapStyles, Map baseMapEnvelopes)
            throws FactoryException, TransformException {
            if (baseMapLayers == null) {
                return;
            }

            List names = new ArrayList(baseMapLayers.keySet());
            Collections.sort(names);

            CoordinateReferenceSystem wgs84 = CRS.decode("EPSG:4326");

            for (Iterator it = names.iterator(); it.hasNext();) {
                String layerName = (String) it.next();

                AttributesImpl qatts = new AttributesImpl();
                qatts.addAttribute("", "queryable", "queryable", "", "0");
                // qatts.addAttribute("", "opaque", "opaque", "", "1");
                // qatts.addAttribute("", "cascaded", "cascaded", "", "1");
                start("Layer", qatts);
                element("Name", layerName);
                element("Title", layerName);
                element("Abstract", "Layer-Group type layer: " + layerName);

                //handleKeywordList(...getKeywords());

                /*String desc = "WKT definition of this CRS:\n" + coverage.getSrsWKT();
                comment(desc);*/
                GeneralEnvelope bounds = (GeneralEnvelope) baseMapEnvelopes.get(layerName);
                GeneralEnvelope llBounds = null;

                String authority = bounds.getCoordinateReferenceSystem().getIdentifiers().toArray()[0]
                    .toString();

                element("SRS", authority);

                if (CRS.equalsIgnoreMetadata(wgs84, bounds.getCoordinateReferenceSystem())) {
                    llBounds = bounds;
                } else {
                    try {
                        final MathTransform srcCRStoWGS84 = CRS.findMathTransform(bounds
                                .getCoordinateReferenceSystem(), wgs84, true);
                        final GeneralEnvelope latLonEnvelope = CRS.transform(srcCRStoWGS84,
                                bounds);
                        latLonEnvelope.setCoordinateReferenceSystem(wgs84);
                        llBounds = latLonEnvelope;
                    } catch(TransformException e) {
                        throw new WmsException("Cannot transform envelope to WGS84 for layer group " + layerName, "TransformException", e);
                    }
                }

                final Envelope bbox = new Envelope(bounds.getLowerCorner().getOrdinate(0),
                        bounds.getUpperCorner().getOrdinate(0),
                        bounds.getLowerCorner().getOrdinate(1),
                        bounds.getUpperCorner().getOrdinate(1));

                final Envelope llBbox = new Envelope(llBounds.getLowerCorner().getOrdinate(0),
                        llBounds.getUpperCorner().getOrdinate(0),
                        llBounds.getLowerCorner().getOrdinate(1),
                        llBounds.getUpperCorner().getOrdinate(1));

                handleLatLonBBox(llBbox);
                handleBBox(bbox, authority);

                // the layer style is not provided since the group does just have 
                // one possibility, the lack of styles that will make it use
                // the default ones for each layer

                end("Layer");
            }
        }

        /**
         * Writes layer LegendURL pointing to the user supplied icon URL, if
         * any, or to the proper GetLegendGraphic operation if an URL was not
         * supplied by configuration file.
         *
         * <p>
         * It is common practice to supply a URL to a WMS accesible legend
         * graphic when it is difficult to create a dynamic legend for a layer.
         * </p>
         *
         * @param ft
         *            The FeatureTypeInfo that holds the legendURL to write out,
         *            or<code>null</code> if dynamically generated.
         *
         * @task TODO: figure out how to unhack legend parameters such as WIDTH,
         *       HEIGHT and FORMAT
         */
        protected void handleLegendURL(Object layer) {
            LegendURL legend = null;
            String layerName = null;

            if (layer instanceof FeatureTypeInfo) {
                legend = ((FeatureTypeInfo) layer).getLegendURL();
                layerName = ((FeatureTypeInfo) layer).getName();
            } else if (layer instanceof CoverageInfo) {
                layerName = ((CoverageInfo) layer).getName();
            }

            if (legend != null) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("using user supplied legend URL");
                }

                AttributesImpl attrs = new AttributesImpl();
                attrs.addAttribute("", "width", "width", "", String.valueOf(legend.getWidth()));
                attrs.addAttribute("", "height", "height", "", String.valueOf(legend.getHeight()));

                start("LegendURL", attrs);

                element("Format", legend.getFormat());
                attrs.clear();
                attrs.addAttribute("", "xmlns:xlink", "xmlns:xlink", "", XLINK_NS);
                attrs.addAttribute(XLINK_NS, "type", "xlink:type", "", "simple");
                attrs.addAttribute(XLINK_NS, "href", "xlink:href", "", legend.getOnlineResource());

                element("OnlineResource", null, attrs);

                end("LegendURL");
            } else {
                String defaultFormat = GetLegendGraphicRequest.DEFAULT_FORMAT;

                if (!GetLegendGraphicResponse.supportsFormat(defaultFormat)) {
                    if (LOGGER.isLoggable(Level.WARNING)) {
                        LOGGER.warning(new StringBuffer("Default legend format (").append(
                                defaultFormat)
                                                                                  .append(")is not supported (jai not available?), can't add LegendURL element")
                                                                                  .toString());
                    }

                    return;
                }

                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("Adding GetLegendGraphic call as LegendURL");
                }

                AttributesImpl attrs = new AttributesImpl();
                attrs.addAttribute("", "width", "width", "",
                    String.valueOf(GetLegendGraphicRequest.DEFAULT_WIDTH));

                // DJB: problem here is that we do not know the size of the
                // legend apriori - we need
                // to make one and find its height. Not the best way, but it
                // would work quite well.
                // This was advertising a 20*20 icon, but actually producing
                // ones of a different size.
                // An alternative is to just scale the resulting icon to what
                // the server requested, but this isnt
                // the nicest thing since warped images dont look nice. The
                // client should do the warping.

                // however, to actually estimate the size is a bit difficult.
                // I'm going to do the scaling
                // so it obeys the what the request says. For people with a
                // problem with that should consider
                // changing the default size here so that the request is for the
                // correct size.
                attrs.addAttribute("", "height", "height", "",
                    String.valueOf(GetLegendGraphicRequest.DEFAULT_HEIGHT));

                start("LegendURL", attrs);

                element("Format", defaultFormat);
                attrs.clear();

                StringBuffer onlineResource =
                    new StringBuffer(RequestUtils.proxifiedBaseURL(
                            request.getBaseUrl()
                            ,request.getServiceConfig().getGeoServer().getProxyBaseUrl()
                            ));
                onlineResource.append("wms/GetLegendGraphic?VERSION=");
                onlineResource.append(GetLegendGraphicRequest.SLD_VERSION);
                onlineResource.append("&FORMAT=");
                onlineResource.append(defaultFormat);
                onlineResource.append("&WIDTH=");
                onlineResource.append(GetLegendGraphicRequest.DEFAULT_WIDTH);
                onlineResource.append("&HEIGHT=");
                onlineResource.append(GetLegendGraphicRequest.DEFAULT_HEIGHT);
                onlineResource.append("&LAYER=");
                onlineResource.append(layerName);

                attrs.addAttribute("", "xmlns:xlink", "xmlns:xlink", "", XLINK_NS);
                attrs.addAttribute(XLINK_NS, "type", "xlink:type", "", "simple");
                attrs.addAttribute(XLINK_NS, "href", "xlink:href", "", onlineResource.toString());
                element("OnlineResource", null, attrs);

                end("LegendURL");
            }
        }

        /**
         * Encodes a LatLonBoundingBox for the given Envelope.
         *
         * @param bbox
         */
        private void handleLatLonBBox(Envelope bbox) {
            String minx = String.valueOf(bbox.getMinX());
            String miny = String.valueOf(bbox.getMinY());
            String maxx = String.valueOf(bbox.getMaxX());
            String maxy = String.valueOf(bbox.getMaxY());

            AttributesImpl bboxAtts = new AttributesImpl();
            bboxAtts.addAttribute("", "minx", "minx", "", minx);
            bboxAtts.addAttribute("", "miny", "miny", "", miny);
            bboxAtts.addAttribute("", "maxx", "maxx", "", maxx);
            bboxAtts.addAttribute("", "maxy", "maxy", "", maxy);

            element("LatLonBoundingBox", null, bboxAtts);
        }

        /**
         * adds a comment to the output xml file. THIS IS A BIG HACK. TODO: do
         * this in the correct manner!
         *
         * @param comment
         */
        public void comment(String comment) {
            if (contentHandler instanceof TransformerIdentityImpl) // HACK HACK
                                                                   // HACK --
                                                                   // not sure
                                                                   // of the
                                                                   // proper
                                                                   // way to do
                                                                   // this.
             {
                try {
                    TransformerIdentityImpl ch = (TransformerIdentityImpl) contentHandler;
                    ch.comment(comment.toCharArray(), 0, comment.length());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        /**
         * Encodes a BoundingBox for the given Envelope.
         *
         * @param bbox
         */
        private void handleBBox(Envelope bbox, String SRS) {
            String minx = String.valueOf(bbox.getMinX());
            String miny = String.valueOf(bbox.getMinY());
            String maxx = String.valueOf(bbox.getMaxX());
            String maxy = String.valueOf(bbox.getMaxY());

            AttributesImpl bboxAtts = new AttributesImpl();
            bboxAtts.addAttribute("", "SRS", "SRS", "", SRS);
            bboxAtts.addAttribute("", "minx", "minx", "", minx);
            bboxAtts.addAttribute("", "miny", "miny", "", miny);
            bboxAtts.addAttribute("", "maxx", "maxx", "", maxx);
            bboxAtts.addAttribute("", "maxy", "maxy", "", maxy);

            element("BoundingBox", null, bboxAtts);
        }
    }
}


/**
 * A Class to manage the WMS Layer structure
 *
 * @author fabiania
 *
 * TODO To change the template for this generated type comment go to Window -
 * Preferences - Java - Code Style - Code Templates
 */
class LayerTree {
    private String name;
    private Collection childrens;
    private Collection data;

    /**
     * @param name
     *            String
     */
    public LayerTree(String name) {
        this.name = name;
        this.childrens = new ArrayList();
        this.data = new ArrayList();
    }

    /**
     * @param c
     *            Collection
     */
    public LayerTree(Collection c) {
        this.name = "";
        this.childrens = new ArrayList();
        this.data = new ArrayList();

        for (Iterator it = c.iterator(); it.hasNext();) {
            Object element = it.next();

            if (element instanceof CoverageInfo) {
                CoverageInfo cLayer = (CoverageInfo) element;

                if (cLayer.isEnabled()) {
                    String wmsPath = (((cLayer.getWmsPath() != null)
                        && (cLayer.getWmsPath().length() > 0)) ? cLayer.getWmsPath() : "/");

                    if (wmsPath.startsWith("/")) {
                        wmsPath = wmsPath.substring(1, wmsPath.length());
                    }

                    String[] treeStructure = wmsPath.split("/");
                    addToNode(this, treeStructure, cLayer);
                }
            } else if (element instanceof FeatureTypeInfo) {
                FeatureTypeInfo fLayer = (FeatureTypeInfo) element;

                if (fLayer.isEnabled()) {
                    String wmsPath = (((fLayer.getWmsPath() != null)
                        && (fLayer.getWmsPath().length() > 0)) ? fLayer.getWmsPath() : "/");

                    if (wmsPath.startsWith("/")) {
                        wmsPath = wmsPath.substring(1, wmsPath.length());
                    }

                    String[] treeStructure = wmsPath.split("/");
                    addToNode(this, treeStructure, fLayer);
                }
            }
        }
    }

    /**
     * @param tree
     * @param treeStructure
     * @param layer
     */
    private void addToNode(LayerTree tree, String[] treeStructure, CoverageInfo layer) {
        final int length = treeStructure.length;

        if ((length == 0) || (treeStructure[0].length() == 0)) {
            tree.data.add(layer);
        } else {
            LayerTree node = tree.getNode(treeStructure[0]);

            if (node == null) {
                node = new LayerTree(treeStructure[0]);
                tree.childrens.add(node);
            }

            String[] subTreeStructure = new String[length - 1];
            System.arraycopy(treeStructure, 1, subTreeStructure, 0, length - 1);
            addToNode(node, subTreeStructure, layer);
        }
    }

    private void addToNode(LayerTree tree, String[] treeStructure, FeatureTypeInfo layer) {
        final int length = treeStructure.length;

        if ((length == 0) || (treeStructure[0].length() == 0)) {
            tree.data.add(layer);
        } else {
            LayerTree node = tree.getNode(treeStructure[0]);

            if (node == null) {
                node = new LayerTree(treeStructure[0]);
                tree.childrens.add(node);
            }

            String[] subTreeStructure = new String[length - 1];
            System.arraycopy(treeStructure, 1, subTreeStructure, 0, length - 1);
            addToNode(node, subTreeStructure, layer);
        }
    }

    /**
     * @param string
     * @return
     */
    public LayerTree getNode(String name) {
        LayerTree node = null;

        for (Iterator it = this.childrens.iterator(); it.hasNext();) {
            LayerTree tmpNode = (LayerTree) it.next();

            if (tmpNode.name.equals(name)) {
                node = tmpNode;
            }
        }

        return node;
    }

    public Collection getChildrens() {
        return childrens;
    }

    public Collection getData() {
        return data;
    }

    public String getName() {
        return name;
    }
}
