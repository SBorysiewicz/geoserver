package org.geoserver.wcs;

import static org.custommonkey.xmlunit.XMLAssert.*;
import junit.framework.Test;

import org.geoserver.data.test.MockData;
import org.geoserver.wcs.test.WCSTestSupport;
import org.vfny.geoserver.global.GeoServer;
import org.vfny.geoserver.global.dto.ContactDTO;
import org.vfny.geoserver.global.dto.GeoServerDTO;
import org.vfny.geoserver.wcs.WcsException.WcsExceptionCode;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

public class GetCapabilitiesTest extends WCSTestSupport {
    
    private static GeoServer geoServer;
    
    /**
     * This is a READ ONLY TEST so we can use one time setup
     */
    public static Test suite() {
        return new OneTimeTestSetup(new GetCapabilitiesTest());
    }
    
    @Override
    protected void oneTimeSetUp() throws Exception {
        super.oneTimeSetUp();
        geoServer = (GeoServer) applicationContext.getBean("geoServer");
    }
    
    @Override
    protected void populateDataDirectory(MockData dataDirectory) throws Exception {
        super.populateDataDirectory(dataDirectory);
        dataDirectory.disableCoverageStore(WORLD.getLocalPart());
    }
    
//    @Override
//    protected String getDefaultLogConfiguration() {
//        return "/GEOTOOLS_DEVELOPER_LOGGING.properties";
//    }

    public void testGetBasic() throws Exception {
        Document dom = getAsDOM(BASEPATH + "?request=GetCapabilities&service=WCS&acceptversions=1.1.1");
         print(dom);
        checkValidationErrors(dom, WCS11_SCHEMA);
        
        // make sure we provided the store values
        assertXpathEvaluatesTo("TrueFalse", "/wcs:Capabilities/ows:OperationsMetadata" +
        		"/ows:Operation[@name=\"GetCoverage\"]/ows:Parameter/ows:AllowedValues", dom);
        
        // make sure the disabled coverage store is really disabled
        assertXpathEvaluatesTo("0", "count(//ows:Title[text()='World'])", dom);
    }
    
    
    
    public void testNoServiceContactInfo() throws Exception {
        // alter geoserver state so that there is no contact information
        GeoServerDTO dto = (GeoServerDTO) geoServer.toDTO();
        dto.setContact(new ContactDTO());
        geoServer.load(dto);
        
        Document dom = getAsDOM(BASEPATH + "?request=GetCapabilities&service=WCS");
//         print(dom);
        checkValidationErrors(dom, WCS11_SCHEMA);
    }

    public void testPostBasic() throws Exception {
        String request = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<wcs:GetCapabilities service=\"WCS\" xmlns:ows=\"http://www.opengis.net/ows/1.1\" "
                + "xmlns:wcs=\"http://www.opengis.net/wcs/1.1.1\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"/>";
        Document dom = postAsDOM(BASEPATH, request);
//        print(dom);
        checkValidationErrors(dom, WCS11_SCHEMA);
    }

    public void testUnsupportedVersionPost() throws Exception {
        String request = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<wcs:GetCapabilities service=\"WCS\" xmlns:ows=\"http://www.opengis.net/ows/1.1\""
                + " xmlns:wcs=\"http://www.opengis.net/wcs/1.1.1\""
                + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
                + "  <ows:AcceptVersions>" // 
                + "    <ows:Version>9.9.9</ows:Version>" //
                + "  </ows:AcceptVersions>" // 
                + "</wcs:GetCapabilities>";
        Document dom = postAsDOM(BASEPATH, request);
        checkValidationErrors(dom, WCS11_SCHEMA);
        checkOws11Exception(dom);
        assertEquals("ows:ExceptionReport", dom.getFirstChild().getNodeName());
        assertXpathEvaluatesTo("VersionNegotiationFailed", "ows:ExceptionReport/ows:Exception/@exceptionCode", dom);
    }
    
    public void testUnsupportedVersionGet() throws Exception {
        Document dom = getAsDOM(BASEPATH + "?request=GetCapabilities&service=WCS&acceptVersions=9.9.9,8.8.8");
        checkValidationErrors(dom, WCS11_SCHEMA);
        checkOws11Exception(dom);
        assertXpathEvaluatesTo("VersionNegotiationFailed", "ows:ExceptionReport/ows:Exception/@exceptionCode", dom);
    }
    
    public void testSupportedVersionGet() throws Exception {
        Document dom = getAsDOM(BASEPATH + "?request=GetCapabilities&service=WCS&acceptVersions=0.5.0,1.1.1");
        assertEquals("wcs:Capabilities", dom.getFirstChild().getNodeName());
    }
    
    public void testSupportedVersionPost() throws Exception {
        String request = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<wcs:GetCapabilities service=\"WCS\" xmlns:ows=\"http://www.opengis.net/ows/1.1\""
                + " xmlns:wcs=\"http://www.opengis.net/wcs/1.1.1\""
                + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
                + "  <ows:AcceptVersions>" // 
                + "    <ows:Version>0.5.0</ows:Version>" //
                + "    <ows:Version>1.1.1</ows:Version>" //
                + "  </ows:AcceptVersions>" // 
                + "</wcs:GetCapabilities>";
        Document dom = postAsDOM(BASEPATH, request);
        assertEquals("wcs:Capabilities", dom.getFirstChild().getNodeName());
    }
    
    public void testUpdateSequenceInferiorGet() throws Exception {
        Document dom = getAsDOM(BASEPATH + "?request=GetCapabilities&service=WCS&updateSequence=-1");
        checkValidationErrors(dom, WCS11_SCHEMA);
        final Node root = dom.getFirstChild();
        assertEquals("wcs:Capabilities", root.getNodeName());
        assertTrue(root.getChildNodes().getLength() > 0);
    }
    
    public void testUpdateSequenceInferiorPost() throws Exception {
        String request = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<wcs:GetCapabilities service=\"WCS\" xmlns:ows=\"http://www.opengis.net/ows/1.1\""
                + " xmlns:wcs=\"http://www.opengis.net/wcs/1.1.1\""
                + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                + " updateSequence=\"-1\"/>";
        Document dom = postAsDOM(BASEPATH, request);
        checkValidationErrors(dom, WCS11_SCHEMA);
        final Node root = dom.getFirstChild();
        assertEquals("wcs:Capabilities", root.getNodeName());
        assertTrue(root.getChildNodes().getLength() > 0);
    }
    
    public void testUpdateSequenceEqualsGet() throws Exception {
        Document dom = getAsDOM(BASEPATH + "?request=GetCapabilities&service=WCS&updateSequence=0");
        checkValidationErrors(dom, WCS11_SCHEMA);
        final Node root = dom.getFirstChild();
        assertEquals("wcs:Capabilities", root.getNodeName());
        assertEquals(0, root.getChildNodes().getLength());
    }
    
    public void testUpdateSequenceEqualsPost() throws Exception {
        String request = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<wcs:GetCapabilities service=\"WCS\" xmlns:ows=\"http://www.opengis.net/ows/1.1\""
                + " xmlns:wcs=\"http://www.opengis.net/wcs/1.1.1\""
                + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                + " updateSequence=\"0\"/>";
        Document dom = postAsDOM(BASEPATH, request);
        checkValidationErrors(dom, WCS11_SCHEMA);
        final Node root = dom.getFirstChild();
        assertEquals("wcs:Capabilities", root.getNodeName());
        assertEquals(0, root.getChildNodes().getLength());
    }
    
    public void testUpdateSequenceSuperiorGet() throws Exception {
        Document dom = getAsDOM(BASEPATH + "?request=GetCapabilities&service=WCS&updateSequence=1");
        checkValidationErrors(dom, WCS11_SCHEMA);
//        print(dom);
        checkOws11Exception(dom);
    }
    
    public void testUpdateSequenceSuperiorPost() throws Exception {
        String request = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<wcs:GetCapabilities service=\"WCS\" xmlns:ows=\"http://www.opengis.net/ows/1.1\""
                + " xmlns:wcs=\"http://www.opengis.net/wcs/1.1.1\""
                + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                + " updateSequence=\"1\"/>";
        Document dom = postAsDOM(BASEPATH, request);
        checkValidationErrors(dom, WCS11_SCHEMA);
//        print(dom);
        checkOws11Exception(dom);
    }
    
    public void testSectionsBogus() throws Exception {
        Document dom = getAsDOM(BASEPATH + "?request=GetCapabilities&service=WCS&sections=Bogus");
        checkValidationErrors(dom, WCS11_SCHEMA);
        checkOws11Exception(dom);
        assertXpathEvaluatesTo(WcsExceptionCode.InvalidParameterValue.toString(), "/ows:ExceptionReport/ows:Exception/@exceptionCode", dom);
    }
    
    public void testSectionsAll() throws Exception {
        Document dom = getAsDOM(BASEPATH + "?request=GetCapabilities&service=WCS&sections=All");
        checkValidationErrors(dom, WCS11_SCHEMA);
        assertXpathEvaluatesTo("1", "count(//ows:ServiceIdentification)", dom);
        assertXpathEvaluatesTo("1", "count(//ows:ServiceProvider)", dom);
        assertXpathEvaluatesTo("1", "count(//ows:OperationsMetadata)", dom);
        assertXpathEvaluatesTo("1", "count(//wcs:Contents)", dom);
    }
    
    public void testOneSection() throws Exception {
        Document dom = getAsDOM(BASEPATH + "?request=GetCapabilities&service=WCS&sections=ServiceProvider");
        checkValidationErrors(dom, WCS11_SCHEMA);
        assertXpathEvaluatesTo("0", "count(//ows:ServiceIdentification)", dom);
        assertXpathEvaluatesTo("1", "count(//ows:ServiceProvider)", dom);
        assertXpathEvaluatesTo("0", "count(//ows:OperationsMetadata)", dom);
        assertXpathEvaluatesTo("0", "count(//wcs:Contents)", dom);
    }
    
    public void testTwoSection() throws Exception {
        Document dom = getAsDOM(BASEPATH + "?request=GetCapabilities&service=WCS&sections=ServiceProvider,Contents");
        checkValidationErrors(dom, WCS11_SCHEMA);
        assertXpathEvaluatesTo("0", "count(//ows:ServiceIdentification)", dom);
        assertXpathEvaluatesTo("1", "count(//ows:ServiceProvider)", dom);
        assertXpathEvaluatesTo("0", "count(//ows:OperationsMetadata)", dom);
        assertXpathEvaluatesTo("1", "count(//wcs:Contents)", dom);
    }

    
}