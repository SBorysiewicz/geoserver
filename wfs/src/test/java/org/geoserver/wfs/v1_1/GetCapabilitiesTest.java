package org.geoserver.wfs.v1_1;

import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

import junit.framework.Test;

import org.geoserver.data.test.MockData;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.wfs.WFSGetFeatureOutputFormat;
import org.geoserver.wfs.WFSTestSupport;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class GetCapabilitiesTest extends WFSTestSupport {
    
    /**
     * This is a READ ONLY TEST so we can use one time setup
     */
    public static Test suite() {
        return new OneTimeTestSetup(new GetCapabilitiesTest());
    }
    
    @Override
    protected void populateDataDirectory(MockData dataDirectory) throws Exception {
        super.populateDataDirectory(dataDirectory);
        dataDirectory.disableDataStore(MockData.CITE_PREFIX);
    }

    public void testGet() throws Exception {
        Document doc = getAsDOM("wfs?service=WFS&request=getCapabilities&version=1.1.0");

        assertEquals("wfs:WFS_Capabilities", doc.getDocumentElement()
                .getNodeName());
        assertEquals("1.1.0", doc.getDocumentElement().getAttribute("version"));
    }

    public void testPost() throws Exception {

        String xml = "<GetCapabilities service=\"WFS\" "
                + " xmlns=\"http://www.opengis.net/wfs\" "
                + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + " xsi:schemaLocation=\"http://www.opengis.net/wfs "
                + " http://schemas.opengis.net/wfs/1.1.0/wfs.xsd\"/>";

        Document doc = postAsDOM("wfs", xml);
        assertEquals("wfs:WFS_Capabilities", doc.getDocumentElement()
                .getNodeName());
        assertEquals("1.1.0", doc.getDocumentElement().getAttribute("version"));
    }

    public void testPostNoSchemaLocation() throws Exception {
        String xml = "<GetCapabilities service=\"WFS\" "
                + " xmlns=\"http://www.opengis.net/wfs\" "
                + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" />";

        Document doc = postAsDOM("wfs", xml);
        assertEquals("wfs:WFS_Capabilities", doc.getDocumentElement()
                .getNodeName());
        assertEquals("1.1.0", doc.getDocumentElement().getAttribute("version"));
    }
    
    public void testOutputFormats() throws Exception {
        Document doc = getAsDOM("wfs?service=WFS&request=getCapabilities&version=1.1.0");
        
        Element outputFormats = getFirstElementByTagName(doc, "OutputFormats");
        NodeList formats = outputFormats.getElementsByTagName("Format");
        
        TreeSet s1 = new TreeSet();
        for ( int i = 0; i < formats.getLength(); i++ ) {
            String format = formats.item(i).getFirstChild().getNodeValue();
            s1.add( format );
        }
        
        List extensions = GeoServerExtensions.extensions( WFSGetFeatureOutputFormat.class );
        
        TreeSet s2 = new TreeSet();
        for ( Iterator e = extensions.iterator(); e.hasNext(); ) {
            WFSGetFeatureOutputFormat extension = (WFSGetFeatureOutputFormat) e.next();
            s2.addAll( extension.getOutputFormats() );
        }
        
        assertEquals( s1, s2 );
    }

}
