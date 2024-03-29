/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org.  All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.wcs.response;

import static org.geoserver.ows.util.ResponseUtils.appendPath;
import static org.geoserver.ows.util.ResponseUtils.buildURL;

import java.io.IOException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

import net.opengis.wcs10.CapabilitiesSectionType;
import net.opengis.wcs10.GetCapabilitiesType;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.MetadataLinkInfo;
import org.geoserver.config.GeoServer;
import org.geoserver.ows.URLMangler.URLType;
import org.geoserver.ows.util.ResponseUtils;
import org.geoserver.wcs.WCSInfo;
import org.geotools.coverage.grid.io.AbstractGridCoverage2DReader;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.util.logging.Logging;
import org.geotools.xml.transform.TransformerBase;
import org.geotools.xml.transform.Translator;
import org.vfny.geoserver.global.CoverageInfoLabelComparator;
import org.vfny.geoserver.wcs.WcsException;
import org.vfny.geoserver.wcs.WcsException.WcsExceptionCode;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Based on the <code>org.geotools.xml.transform</code> framework, does the job of encoding a WCS
 * 1.0.0 Capabilities document.
 * 
 * @author Alessio Fabiani (alessio.fabiani@gmail.com)
 * @author Simone Giannecchini (simboss1@gmail.com)
 * @author Andrea Aime, TOPP
 */
public class Wcs10CapsTransformer extends TransformerBase {
    private static final Logger LOGGER = Logging.getLogger(Wcs10CapsTransformer.class.getPackage()
            .getName());

    protected static final String WCS_URI = "http://www.opengis.net/wcs";

    protected static final String CUR_VERSION = "1.0.0";

    protected static final String XSI_PREFIX = "xsi";

    protected static final String XSI_URI = "http://www.w3.org/2001/XMLSchema-instance";

    private GeoServer geoServer;

    private WCSInfo wcs;

    private Catalog catalog;

    /**
     * Creates a new WFSCapsTransformer object.
     */
    public Wcs10CapsTransformer(GeoServer geoServer) {
        super();
        this.geoServer = geoServer;
        this.wcs = geoServer.getService(WCSInfo.class);
        this.catalog = geoServer.getCatalog();
        setNamespaceDeclarationEnabled(false);
    }

    public Translator createTranslator(ContentHandler handler) {
        return new WCS100CapsTranslator(handler);
    }

    private class WCS100CapsTranslator extends TranslatorSupport {
        // the path that does contain the GeoServer internal XML schemas
        public static final String SCHEMAS = "schemas";

        /**
         * DOCUMENT ME!
         */
        private GetCapabilitiesType request;

        private String proxifiedBaseUrl;

        /**
         * Creates a new WCS100CapsTranslator object.
         * 
         * @param handler
         *            DOCUMENT ME!
         */
        public WCS100CapsTranslator(ContentHandler handler) {
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
            if (!(o instanceof GetCapabilitiesType)) {
                throw new IllegalArgumentException(new StringBuffer("Not a GetCapabilitiesType: ")
                        .append(o).toString());
            }

            this.request = (GetCapabilitiesType) o;

            // check the update sequence
            final int updateSequence = wcs.getGeoServer().getGlobal().getUpdateSequence();
            int requestedUpdateSequence = -1;
            if (request.getUpdateSequence() != null) {
                try {
                    requestedUpdateSequence = Integer.parseInt(request.getUpdateSequence());
                } catch (NumberFormatException e) {
                    if (request.getUpdateSequence().length() == 0)
                        requestedUpdateSequence = 0;
                    else
                        throw new WcsException("Invalid update sequence number format, "
                                + "should be an integer", WcsExceptionCode.InvalidUpdateSequence,
                                "updateSequence");
                }
                if (requestedUpdateSequence > updateSequence) {
                    throw new WcsException("Invalid update sequence value, it's higher "
                            + "than the current value, " + updateSequence,
                            WcsExceptionCode.InvalidUpdateSequence, "updateSequence");
                }

                if (requestedUpdateSequence == updateSequence) {
                    throw new WcsException(
                            "WCS capabilities document is current (updateSequence = "
                                    + updateSequence + ")", WcsExceptionCode.CurrentUpdateSequence,
                            "");
                }
            }

            final AttributesImpl attributes = new AttributesImpl();
            attributes.addAttribute("", "version", "version", "", CUR_VERSION);
            attributes.addAttribute("", "xmlns:wcs", "xmlns:wcs", "", WCS_URI);

            attributes.addAttribute("", "xmlns:xlink", "xmlns:xlink", "",
                    "http://www.w3.org/1999/xlink");
            attributes.addAttribute("", "xmlns:ogc", "xmlns:ogc", "", "http://www.opengis.net/ogc");
            attributes.addAttribute("", "xmlns:ows", "xmlns:ows", "",
                    "http://www.opengis.net/ows/1.1");
            attributes.addAttribute("", "xmlns:gml", "xmlns:gml", "", "http://www.opengis.net/gml");

            final String prefixDef = new StringBuffer("xmlns:").append(XSI_PREFIX).toString();
            attributes.addAttribute("", prefixDef, prefixDef, "", XSI_URI);

            final String locationAtt = new StringBuffer(XSI_PREFIX).append(":schemaLocation")
                    .toString();

            // proxifiedBaseUrl = RequestUtils.proxifiedBaseURL(request.getBaseUrl(), wcs
            // .getGeoServer().getProxyBaseUrl());
            // final String locationDef = WCS_URI + " " + proxifiedBaseUrl +
            // "schemas/wcs/1.0.0/wcsCapabilities.xsd";
            final String locationDef = WCS_URI
                    + " "
                    + buildURL(request.getBaseUrl(), appendPath(SCHEMAS,
                            "wcs/1.0.0/wcsCapabilities.xsd"), null, URLType.RESOURCE);

            attributes.addAttribute("", locationAtt, locationAtt, "", locationDef);
            attributes.addAttribute("", "updateSequence", "updateSequence", "", String
                    .valueOf(updateSequence));
            start("wcs:WCS_Capabilities", attributes);

            // handle the sections directive
            boolean allSections;
            CapabilitiesSectionType section;
            if (request.getSection() == null) {
                allSections = true;
                section = CapabilitiesSectionType.get("/");
            } else {
                section = request.getSection();
                allSections = (section.get("/").equals(section));
            }
            final Set<String> knownSections = new HashSet<String>(Arrays.asList("/",
                    "/WCS_Capabilities/Service", "/WCS_Capabilities/Capability",
                    "/WCS_Capabilities/ContentMetadata"));

            if (!knownSections.contains(section.getLiteral()))
                throw new WcsException("Unknown section " + section,
                        WcsExceptionCode.InvalidParameterValue, "Sections");

            // encode the actual capabilities contents taking into consideration
            // the sections
            if (requestedUpdateSequence < updateSequence) {
                if (allSections
                        || section.equals(CapabilitiesSectionType.WCS_CAPABILITIES_SERVICE_LITERAL)) {
                    handleService();
                }

                if (allSections
                        || section
                                .equals(CapabilitiesSectionType.WCS_CAPABILITIES_CAPABILITY_LITERAL))
                    handleCapabilities();

                if (allSections
                        || section
                                .equals(CapabilitiesSectionType.WCS_CAPABILITIES_CONTENT_METADATA_LITERAL))
                    handleContentMetadata();
            }

            end("wcs:WCS_Capabilities");
        }

        /**
         * Handles the service section of the capabilities document.
         * 
         * @param config
         *            The OGC service to transform.
         * 
         * @throws SAXException
         *             For any errors.
         */
        private void handleService() {
            AttributesImpl attributes = new AttributesImpl();
            attributes.addAttribute("", "version", "version", "", CUR_VERSION);
            start("wcs:Service", attributes);
            handleMetadataLink(wcs.getMetadataLink());
            element("wcs:description", wcs.getAbstract());
            element("wcs:name", wcs.getName());
            element("wcs:label", wcs.getTitle());
            handleKeywords(wcs.getKeywords());
            handleContact();

            String fees = wcs.getFees();

            if ((fees == null) || "".equals(fees)) {
                fees = "NONE";
            }

            element("wcs:fees", fees);

            String accessConstraints = wcs.getAccessConstraints();

            if ((accessConstraints == null) || "".equals(accessConstraints)) {
                accessConstraints = "NONE";
            }

            element("wcs:accessConstraints", accessConstraints);
            end("wcs:Service");
        }

        /**
         * DOCUMENT ME!
         * 
         * @param metadataLink
         *            DOCUMENT ME!
         * 
         * @throws SAXException
         *             DOCUMENT ME!
         */
        private void handleMetadataLink(MetadataLinkInfo mdl) {
            if (mdl != null) {
                AttributesImpl attributes = new AttributesImpl();

                if ((mdl.getAbout() != null) && (mdl.getAbout() != "")) {
                    attributes.addAttribute("", "about", "about", "", mdl.getAbout());
                }

                // if( mdl.getType() != null && mdl.getType() != "" ) {
                // attributes.addAttribute("", "type", "type", "",
                // mdl.getType());
                // }
                if ((mdl.getMetadataType() != null) && (mdl.getMetadataType() != "")) {
                    attributes.addAttribute("", "metadataType", "metadataType", "", mdl
                            .getMetadataType());
                }

                if (attributes.getLength() > 0) {
                    start("wcs:metadataLink", attributes);
                    // characters(mdl.getContent());
                    end("wcs:metadataLink");
                }
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

        /**
         * Handles contacts.
         * 
         * @param config
         *            the service.
         */
        private void handleContact() {
            final GeoServer gs = wcs.getGeoServer();
            String tmp = "";

            if (((gs.getGlobal().getContact() != null) && (gs.getGlobal().getContact()
                    .getContactPerson() != ""))
                    || ((gs.getGlobal().getContact().getContactOrganization() != null) && (gs
                            .getGlobal().getContact().getContactOrganization() != ""))) {
                start("wcs:responsibleParty");

                tmp = gs.getGlobal().getContact().getContactPerson();

                if ((tmp != null) && (tmp != "")) {
                    element("wcs:individualName", tmp);

                    tmp = gs.getGlobal().getContact().getContactOrganization();

                    if ((tmp != null) && (tmp != "")) {
                        element("wcs:organisationName", tmp);
                    }
                } else {
                    tmp = gs.getGlobal().getContact().getContactOrganization();

                    if ((tmp != null) && (tmp != "")) {
                        element("wcs:organisationName", tmp);
                    }
                }

                tmp = gs.getGlobal().getContact().getContactPosition();

                if ((tmp != null) && (tmp != "")) {
                    element("wcs:positionName", tmp);
                }

                start("wcs:contactInfo");

                start("wcs:phone");
                tmp = gs.getGlobal().getContact().getContactVoice();

                if ((tmp != null) && (tmp != "")) {
                    element("wcs:voice", tmp);
                }

                tmp = gs.getGlobal().getContact().getContactFacsimile();

                if ((tmp != null) && (tmp != "")) {
                    element("wcs:facsimile", tmp);
                }

                end("wcs:phone");

                start("wcs:address");
                tmp = gs.getGlobal().getContact().getAddressType();

                if ((tmp != null) && (tmp != "")) {
                    String addr = "";
                    addr = gs.getGlobal().getContact().getAddress();

                    if ((addr != null) && (addr != "")) {
                        element("wcs:deliveryPoint", tmp + " " + addr);
                    }
                } else {
                    tmp = gs.getGlobal().getContact().getAddress();

                    if ((tmp != null) && (tmp != "")) {
                        element("wcs:deliveryPoint", tmp);
                    }
                }

                tmp = gs.getGlobal().getContact().getAddressCity();

                if ((tmp != null) && (tmp != "")) {
                    element("wcs:city", tmp);
                }

                tmp = gs.getGlobal().getContact().getAddressState();

                if ((tmp != null) && (tmp != "")) {
                    element("wcs:administrativeArea", tmp);
                }

                tmp = gs.getGlobal().getContact().getAddressPostalCode();

                if ((tmp != null) && (tmp != "")) {
                    element("wcs:postalCode", tmp);
                }

                tmp = gs.getGlobal().getContact().getAddressCountry();

                if ((tmp != null) && (tmp != "")) {
                    element("wcs:country", tmp);
                }

                tmp = gs.getGlobal().getContact().getContactEmail();

                if ((tmp != null) && (tmp != "")) {
                    element("wcs:electronicMailAddress", tmp);
                }

                end("wcs:address");

                tmp = gs.getGlobal().getContact().getOnlineResource();

                if ((tmp != null) && (tmp != "")) {
                    AttributesImpl attributes = new AttributesImpl();
                    attributes.addAttribute("", "xlink:href", "xlink:href", "", tmp);
                    start("wcs:onlineResource", attributes);
                    end("wcs:onlineResource");
                }

                end("wcs:contactInfo");

                end("wcs:responsibleParty");
            }
        }

        /**
         * DOCUMENT ME!
         * 
         * @param serviceConfig
         *            DOCUMENT ME!
         * 
         * @throws SAXException
         *             DOCUMENT ME!
         */
        private void handleCapabilities() {
            start("wcs:Capability");
            handleRequest();
            handleExceptions();
            handleVendorSpecifics();
            end("wcs:Capability");

        }

        /**
         * Handles the request portion of the document, printing out the capabilities and where to
         * bind to them.
         * 
         * @param config
         *            The global wms.
         * 
         * @throws SAXException
         *             For any problems.
         */
        private void handleRequest() {
            start("wcs:Request");
            handleCapability("wcs:GetCapabilities");
            handleCapability("wcs:DescribeCoverage");
            handleCapability("wcs:GetCoverage");
            end("wcs:Request");
        }

        private void handleCapability(String capabilityName) {
            AttributesImpl attributes = new AttributesImpl();
            start(capabilityName);

            start("wcs:DCPType");
            start("wcs:HTTP");

            // String baseURL = RequestUtils.proxifiedBaseURL(request.getBaseUrl(),
            // wcs.getGeoServer().getGlobal().getProxyBaseUrl());
            String baseURL = proxifiedBaseUrl;
            baseURL = /* ResponseUtils.appendPath(baseURL, */"wcs"/* ) */;

            // ensure ends in "?" or "&"
            baseURL = ResponseUtils.appendQueryString(baseURL, "");

            attributes.addAttribute("", "xlink:href", "xlink:href", "", baseURL);

            start("wcs:Get");
            start("wcs:OnlineResource", attributes);
            end("wcs:OnlineResource");
            end("wcs:Get");
            end("wcs:HTTP");
            end("wcs:DCPType");

            attributes = new AttributesImpl();
            attributes.addAttribute("", "xlink:href", "xlink:href", "", baseURL);

            start("wcs:DCPType");
            start("wcs:HTTP");
            start("wcs:Post");
            start("wcs:OnlineResource", attributes);
            end("wcs:OnlineResource");
            end("wcs:Post");
            end("wcs:HTTP");
            end("wcs:DCPType");
            end(capabilityName);
        }

        /**
         * Handles the printing of the exceptions information, prints the formats that GeoServer can
         * return exceptions in.
         * 
         * @param config
         *            The wms service global config.
         * 
         * @throws SAXException
         *             For any problems.
         */
        private void handleExceptions() {
            start("wcs:Exception");

            final List<String> exceptionFormats = wcs.getExceptionFormats();

            if (exceptionFormats == null || exceptionFormats.isEmpty()) {
                exceptionFormats.add("application/vnd.ogc.se_xml");
            }

            for (String format : exceptionFormats) {
                element("wcs:Format", format);
            }

            end("wcs:Exception");
        }

        /**
         * Handles the vendor specific capabilities. Right now there are none, so we do nothing.
         * 
         * @param config
         *            The global config that may contain vendor specifics.
         * 
         * @throws SAXException
         *             For any problems.
         */
        private void handleVendorSpecifics() {
        }

        /**
         * 
         * @param referencedEnvelope
         */
        private void handleEnvelope(ReferencedEnvelope referencedEnvelope, String timeMetadata) {
            AttributesImpl attributes = new AttributesImpl();

            attributes.addAttribute("", "srsName", "srsName", "", /* "WGS84(DD)" */ "urn:ogc:def:crs:OGC:1.3:CRS84");
            start("wcs:lonLatEnvelope", attributes);
            final StringBuffer minCP = new StringBuffer(Double.toString(referencedEnvelope.getMinX())).append(" ").append(referencedEnvelope.getMinY());
            final StringBuffer maxCP = new StringBuffer(Double.toString(referencedEnvelope.getMaxX())).append(" ").append(referencedEnvelope.getMaxY());

            
            element("gml:pos", minCP.toString());
            element("gml:pos", maxCP.toString());

            if (timeMetadata != null && timeMetadata.length() > 0) {
                String[] timePositions = orderTimeArray(timeMetadata.split(","));
                element("gml:timePosition", timePositions[0]);
                element("gml:timePosition", timePositions[timePositions.length - 1]);
            }
            
            end("wcs:lonLatEnvelope");
        }

        /**
         * 
         * @param originalArray
         * @return
         */
        private String[] orderDoubleArray(String[] originalArray) {
            List finalArray = Arrays.asList(originalArray);
            
            Collections.sort(finalArray, new Comparator<String>() {

                public int compare(String o1, String o2) {
                    if (o1.equals(o2))
                        return 0;
                    
                    return (Double.parseDouble(o1) > Double.parseDouble(o2) ? 1 : -1);
                }
                
            });
            
            return (String[]) finalArray.toArray(new String[1]);
        }
        
        /**
         * 
         * @param originalArray
         * @return
         */
        private String[] orderTimeArray(String[] originalArray) {
            List finalArray = Arrays.asList(originalArray);

            Collections.sort(finalArray, new Comparator<String>() {
                /**
                 * All patterns that are correct regarding the ISO-8601 norm.
                 */
                final String[] PATTERNS = {
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                    "yyyy-MM-dd'T'HH:mm:sss'Z'",
                    "yyyy-MM-dd'T'HH:mm:ss'Z'",
                    "yyyy-MM-dd'T'HH:mm'Z'",
                    "yyyy-MM-dd'T'HH'Z'",
                    "yyyy-MM-dd",
                    "yyyy-MM",
                    "yyyy"
                };
                
                public int compare(String o1, String o2) {
                    if (o1.equals(o2))
                        return 0;
                    
                    Date d1 = getDate(o1);
                    Date d2 = getDate(o2);
                    
                    if (d1 == null || d2 == null)
                        return 0;
                    
                    return (d1.getTime() > d2.getTime() ? 1 : -1);
                }
                
                private Date getDate(final String value) {
                    
                    // special handling for current keyword
                    if(value.equalsIgnoreCase("current"))
                            return null;
                    for (int i=0; i<PATTERNS.length; i++) {
                        // rebuild formats at each parse, date formats are not thread safe
                        SimpleDateFormat format = new SimpleDateFormat(PATTERNS[i], Locale.CANADA);

                        /* We do not use the standard method DateFormat.parse(String), because if the parsing
                         * stops before the end of the string, the remaining characters are just ignored and
                         * no exception is thrown. So we have to ensure that the whole string is correct for
                         * the format.
                         */
                        ParsePosition pos = new ParsePosition(0);
                        Date time = format.parse(value, pos);
                        if (pos.getIndex() == value.length()) {
                            return time;
                        }
                    }
                    
                    return null;
                }

                
            });
            
            return (String[]) finalArray.toArray(new String[1]);
        }

        /**
         * 
         * @param mdl
         * @param linkType
         */
        private void handleMetadataLink(MetadataLinkInfo mdl, String linkType) {
            if (mdl != null) {
                AttributesImpl attributes = new AttributesImpl();

                if ((mdl.getAbout() != null) && (mdl.getAbout() != "")) {
                    attributes.addAttribute("", "about", "about", "", mdl.getAbout());
                }

                if ((mdl.getMetadataType() != null) && (mdl.getMetadataType() != "")) {
                    attributes.addAttribute("", "xlink:type", "xlink:type", "", linkType);
                }

                if (attributes.getLength() > 0) {
                    element("ows:Metadata", null, attributes);
                }
            }
        }

        /**
         * 
         */
        private void handleContentMetadata() {
            AttributesImpl attributes = new AttributesImpl();
            attributes.addAttribute("", "version", "version", "", CUR_VERSION);

            start("wcs:ContentMetadata", attributes);

            List<CoverageInfo> coverages = catalog.getCoverages();
            Collections.sort(coverages, new CoverageInfoLabelComparator());
            for (CoverageInfo cvInfo : coverages) {
                handleCoverageOfferingBrief(cvInfo);
            }

            end("wcs:ContentMetadata");
        }

        /**
         * 
         * @param cv
         */
        private void handleCoverageOfferingBrief(CoverageInfo cv) {
            if (cv.isEnabled()) {
                start("wcs:CoverageOfferingBrief");

                String tmp;

                for (MetadataLinkInfo mdl : cv.getMetadataLinks())
                    handleMetadataLink(mdl);

                tmp = cv.getDescription();

                if ((tmp != null) && (tmp != "")) {
                    element("wcs:description", tmp);
                }

                tmp = cv.getPrefixedName();

                if ((tmp != null) && (tmp != "")) {
                    element("wcs:name", tmp);
                }

                tmp = cv.getTitle();

                if ((tmp != null) && (tmp != "")) {
                    element("wcs:label", tmp);
                }

                
                String timeMetadata = null;

                CoverageStoreInfo csinfo = cv.getStore();
                
                if(csinfo == null)
                    throw new WcsException("Unable to acquire coverage store resource for coverage: " + cv.getName());
                
                AbstractGridCoverage2DReader reader = null;
                try {
                    reader = (AbstractGridCoverage2DReader) catalog.getResourcePool().getGridCoverageReader(csinfo, null);
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

                handleEnvelope(cv.getLatLonBoundingBox(), timeMetadata);
                handleKeywords(cv.getKeywords());

                end("wcs:CoverageOfferingBrief");
            }
        }

        /**
         * Writes the element if and only if the content is not null and not empty
         * 
         * @param elementName
         * @param content
         */
        private void elementIfNotEmpty(String elementName, String content) {
            if (content != null && !"".equals(content.trim()))
                element(elementName, content);
        }
    }
}