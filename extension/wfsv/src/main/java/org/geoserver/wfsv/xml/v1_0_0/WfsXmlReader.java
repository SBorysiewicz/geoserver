/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.wfsv.xml.v1_0_0;

import java.io.Reader;
import java.util.Iterator;
import java.util.Map;

import javax.xml.namespace.QName;

import org.geoserver.ows.XmlRequestReader;
import org.geoserver.wfs.WFS;
import org.geoserver.wfs.WFSException;
import org.geotools.util.Version;
import org.geotools.xml.Parser;
import org.vfny.geoserver.global.NameSpaceInfo;
import org.xml.sax.InputSource;

/**
 * Readers for plain WFS queries that need to use versioning extended elements
 * @author Andrea Aime - TOPP
 *
 */
public class WfsXmlReader extends XmlRequestReader {
    private WFS wfs;

    private WFSVConfiguration configuration;

    public WfsXmlReader(String element, WFS wfs, WFSVConfiguration configuration) {
        super(new QName(org.geoserver.wfs.xml.v1_0_0.WFS.NAMESPACE, element), new Version("1.0.0"),
                "wfsv");
        this.wfs = wfs;
        this.configuration = configuration;
    }

    public Object read(Object request, Reader reader, Map kvp) throws Exception {
        // TODO: make this configurable?
        configuration.getProperties().add(Parser.Properties.PARSE_UNKNOWN_ELEMENTS);

        // check the strict flag to determine if we should validate or not
        Boolean strict = (Boolean) kvp.get("strict");
        if ( strict == null ) {
            strict = Boolean.FALSE;
        } else if(wfs.getCiteConformanceHacks()) {
            strict = Boolean.TRUE;
        }

        Parser parser = new Parser(configuration);
        parser.setValidating(strict);

        // "inject" namespace mappings
        NameSpaceInfo[] namespaces = configuration.getCatalog().getNameSpaces();
        for (int i = 0; i < namespaces.length; i++) {
            if (namespaces[i].isDefault())
                continue;

            parser.getNamespaces().declarePrefix(namespaces[i].getPrefix(), namespaces[i].getURI());
        }

        // set the input source with the correct encoding
        InputSource source = new InputSource(reader);
        source.setEncoding(wfs.getCharSet().name());

        Object parsed = parser.parse(source);

        // valid request? this should definitley be a configuration option
        // TODO: HACK, disabling validation for transaction
        if (!"Transaction".equalsIgnoreCase(getElement().getLocalPart())) {
            if (!parser.getValidationErrors().isEmpty()) {
                WFSException exception = new WFSException("Invalid request",
                        "InvalidParameterValue");

                for (Iterator e = parser.getValidationErrors().iterator(); e.hasNext();) {
                    Exception error = (Exception) e.next();
                    exception.getExceptionText().add(error.getLocalizedMessage());
                }

                throw exception;
            }
        }

        return parsed;
    }
}
