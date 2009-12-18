package org.geoserver.wps;

import junit.framework.Test;

import org.w3c.dom.Document;

public class GetCapabilitiesTest extends WPSTestSupport {

    //read-only test
    public static Test suite() {
        return new OneTimeTestSetup(new GetCapabilitiesTest());
    }
    
    public void testGetBasic() throws Exception { // Standard Test A.4.2.1
        Document d = getAsDOM( "wps?service=wps&request=getcapabilities" );
        print(d);
        basicCapabilitiesTest(d);
    }

    public void testPostBasic() throws Exception { // Standard Test A.4.2.2
        String request = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<wps:GetCapabilities service=\"WPS\" xmlns:ows=\"http://www.opengis.net/ows/1.1\" "
                + "xmlns:wps=\"http://www.opengis.net/wps/1.0.0\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"/>";
        Document d = postAsDOM(root(), request);
        basicCapabilitiesTest(d);
    }
    
    public void testBasicGetAcceptVersions() throws Exception { // Standard Test A.4.2.3
        Document d = getAsDOM( "wps?service=wps&request=getcapabilities&AcceptVersions=1.0.0" );
        print(d);
        basicCapabilitiesTest(d);
    }
    
    public void testBasicGetLanguage() throws Exception { // Standard Test A.4.2.3
        Document d = getAsDOM( "wps?service=wps&request=getcapabilities&language=en-US" );
        print(d);
        basicCapabilitiesTest(d);
    }

    public void testBasicPostAcceptVersions() throws Exception { // Standard Test A.4.2.3
        String request = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<wps:GetCapabilities service=\"WPS\" xmlns:ows=\"http://www.opengis.net/ows/1.1\" "
                + "xmlns:wps=\"http://www.opengis.net/wps/1.0.0\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
                +   "<ows:AcceptVersions>"
                +     "<ows:Version>1.0.0</ows:Version>"
                +   "</ows:AcceptVersions>"
                + "</wps:GetCapabilities>";
        Document d = postAsDOM(root(), request);
        basicCapabilitiesTest(d);
    }

    public void testBasicPostLanguage() throws Exception { // Standard Test A.4.2.3
        String request = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<wps:GetCapabilities service=\"WPS\" xmlns:ows=\"http://www.opengis.net/ows/1.1\" "
                + "language=\"en-US\" "
                + "xmlns:wps=\"http://www.opengis.net/wps/1.0.0\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"/>";
        Document d = postAsDOM(root(), request);
        basicCapabilitiesTest(d);
    }
    
    private void basicCapabilitiesTest(Document d) throws Exception {
        print(d);
        checkValidationErrors(d);
        
        assertEquals( "wps:Capabilities", d.getDocumentElement().getNodeName() );
        int np = d.getElementsByTagName( "wps:Process" ).getLength();
        assertTrue( np > 0 );
    }

    public void testUnsupportedVersionHighPost() throws Exception { // Standard Test A.4.2.5
        String request = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<wps:GetCapabilities service=\"WPS\" xmlns:ows=\"http://www.opengis.net/ows/1.1\" "
                + "xmlns:wps=\"http://www.opengis.net/wps/1.0.0\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
                + "  <ows:AcceptVersions>" // 
                + "    <ows:Version>9.9.9</ows:Version>" //
                + "  </ows:AcceptVersions>" // 
                + "</wps:GetCapabilities>";
        Document dom = postAsDOM(root(), request);
        
        checkValidationErrors(dom);
        checkOws11Exception(dom, "VersionNegotiationFailed");
    }

    public void testUnsupportedVersionLowPost() throws Exception { // Standard Test A.4.2.5
        String request = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<wps:GetCapabilities service=\"WPS\" xmlns:ows=\"http://www.opengis.net/ows/1.1\" "
                + "xmlns:wps=\"http://www.opengis.net/wps/1.0.0\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
                + "  <ows:AcceptVersions>" // 
                + "    <ows:Version>0.0.9</ows:Version>" //
                + "  </ows:AcceptVersions>" // 
                + "</wps:GetCapabilities>";
        Document dom = postAsDOM(root(), request);
        
        checkValidationErrors(dom);
        checkOws11Exception(dom, "VersionNegotiationFailed");
    }
    
    public void testUnsupportedVersionGet() throws Exception { // Standard Test A.4.2.5
        Document dom = getAsDOM(root() + "request=GetCapabilities&service=WPS&acceptVersions=9.9.9,8.8.8");
        
        checkValidationErrors(dom);
        checkOws11Exception(dom, "VersionNegotiationFailed");
    }
    
    public void testSupportedVersionGet() throws Exception { // Standard Test A.4.2.5
        Document dom = getAsDOM(root() + "request=GetCapabilities&service=WPS&acceptVersions=0.5.0,1.0.0");
        assertEquals("wps:Capabilities", dom.getFirstChild().getNodeName());
    }
    
    public void testSupportedVersionPost() throws Exception { // Standard Test A.4.2.5
        String request = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<wps:GetCapabilities service=\"WPS\" xmlns:ows=\"http://www.opengis.net/ows/1.1\" "
                + "xmlns:wps=\"http://www.opengis.net/wps/1.0.0\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
                + "  <ows:AcceptVersions>" // 
                + "    <ows:Version>0.5.0</ows:Version>" //
                + "    <ows:Version>1.0.0</ows:Version>" //
                + "  </ows:AcceptVersions>" // 
                + "</wps:GetCapabilities>";
        Document dom = postAsDOM(root(), request);
        print( dom );
        assertEquals("wps:Capabilities", dom.getFirstChild().getNodeName());
    }
    
    /* TODO Update Sequence tests
    public void testUpdateSequenceInferiorGet() throws Exception { // Standard Test A.4.2.6
        Document dom = getAsDOM(root() + "request=GetCapabilities&service=WPS&updateSequence=-1");
        basicCapabilitiesTest(dom);
    }
    
    public void testUpdateSequenceInferiorPost() throws Exception { // Standard Test A.4.2.6
        String request = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<wps:GetCapabilities service=\"WPS\" xmlns:ows=\"http://www.opengis.net/ows/1.1\" "
                + "xmlns:wps=\"http://www.opengis.net/wps/1.0.0\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + " updateSequence=\"-1\"/>";
        Document dom = postAsDOM(root(), request);
        basicCapabilitiesTest(dom);
    }
    
    public void testUpdateSequenceEqualsGet() throws Exception { // Standard Test A.4.2.6
        Document dom = getAsDOM(root() + "request=GetCapabilities&service=WPS&updateSequence=0");
        checkValidationErrors(dom);
        final Node root = dom.getFirstChild();
        assertEquals("wps:Capabilities", root.getNodeName());
        assertEquals(0, root.getChildNodes().getLength());
    }
    
    public void testUpdateSequenceEqualsPost() throws Exception { // Standard Test A.4.2.6
        String request = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<wps:GetCapabilities service=\"WPS\" xmlns:ows=\"http://www.opengis.net/ows/1.1\" "
                + "xmlns:wps=\"http://www.opengis.net/wps/1.0.0\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + " updateSequence=\"0\"/>";
        Document dom = postAsDOM(root(), request);
        checkValidationErrors(dom);
        final Node root = dom.getFirstChild();
        assertEquals("wcs:Capabilities", root.getNodeName());
        assertEquals(0, root.getChildNodes().getLength());
    }
    
    public void testUpdateSequenceSuperiorGet() throws Exception { // Standard Test A.4.2.6
        Document dom = getAsDOM(root() + "request=GetCapabilities&service=WPS&updateSequence=1");
        checkValidationErrors(dom);
//        print(dom);
        checkOws11Exception(dom);
    }
    
    public void testUpdateSequenceSuperiorPost() throws Exception { // Standard Test A.4.2.6
        String request = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<wps:GetCapabilities service=\"WPS\" xmlns:ows=\"http://www.opengis.net/ows/1.1\" "
                + "xmlns:wps=\"http://www.opengis.net/wps/1.0.0\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + " updateSequence=\"1\"/>";
        Document dom = postAsDOM(root(), request);
        checkValidationErrors(dom);
//        print(dom);
        checkOws11Exception(dom);
    }
    */
    
    /* TODO Language Negotiation tests
    public void testGetLanguageGood() throws Exception { // Standard Test A.4.2.6
        Document d = getAsDOM( "wps?service=wps&request=getcapabilities&language=en-US" );
        print(d);
        basicCapabilitiesTest(d);
    }
    
    public void testGetLanguageBad() throws Exception { // Standard Test A.4.2.6
        Document d = getAsDOM( "wps?service=wps&request=getcapabilities&language=zz-ZZ" );
        print(d);
        basicCapabilitiesTest(d);
    }

    public void testPostLanguageGod() throws Exception { // Standard Test A.4.2.6
        String request = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<wps:GetCapabilities service=\"WPS\" xmlns:ows=\"http://www.opengis.net/ows/1.1\" "
                + "language=\"en-US\" "
                + "xmlns:wps=\"http://www.opengis.net/wps/1.0.0\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"/>";
        Document d = postAsDOM(root(), request);
        basicCapabilitiesTest(d);
    }

    public void testPostLanguageBad() throws Exception { // Standard Test A.4.2.6
        String request = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<wps:GetCapabilities service=\"WPS\" xmlns:ows=\"http://www.opengis.net/ows/1.1\" "
                + "language=\"zz-ZZ\" "
                + "xmlns:wps=\"http://www.opengis.net/wps/1.0.0\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"/>";
        Document d = postAsDOM(root(), request);
        basicCapabilitiesTest(d);
    }
    */
}
