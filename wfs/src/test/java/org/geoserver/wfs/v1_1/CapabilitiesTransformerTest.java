package org.geoserver.wfs.v1_1;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.opengis.wfs.GetCapabilitiesType;
import net.opengis.wfs.WfsFactory;

import org.geoserver.util.ErrorHandler;
import org.geoserver.util.ReaderUtils;
import org.geoserver.wfs.CapabilitiesTransformer;
import org.geoserver.wfs.WFSTestSupport;
import org.geoserver.wfs.xml.v1_1_0.WFS;

public class CapabilitiesTransformerTest extends WFSTestSupport {

    static Logger logger = org.geotools.util.logging.Logging.getLogger("org.geoserver.wfs.test");

    GetCapabilitiesType request() {
        GetCapabilitiesType type = WfsFactory.eINSTANCE.createGetCapabilitiesType();
        type.setBaseUrl("http://localhost:8080/geoserver");
        return type;
    }
    
    public void test() throws Exception {
        CapabilitiesTransformer tx = new CapabilitiesTransformer.WFS1_1(getWFS(),
                getCatalog());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        tx.transform(request(), output);

        InputStreamReader reader = new InputStreamReader(
                new ByteArrayInputStream(output.toByteArray()));

        ErrorHandler handler = new ErrorHandler(logger, Level.WARNING);
        // use the schema embedded in the web module
        ReaderUtils.validate(reader, handler, WFS.NAMESPACE,
                "../web/src/main/webapp/schemas/wfs/1.1.0/wfs.xsd");

        assertTrue(handler.errors.isEmpty());
        
    }
}
