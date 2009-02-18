package org.geoserver.wcs.web;

import org.geoserver.data.test.MockData;
import org.geoserver.web.GeoServerWicketTestSupport;
import org.geoserver.wcs.test.CoverageTestSupport;

import javax.xml.namespace.QName;

public abstract class GeoServerWicketCoverageTestSupport extends GeoServerWicketTestSupport {
    @Override
    protected void populateDataDirectory(MockData dataDirectory) throws Exception {
        dataDirectory.addWellKnownCoverageTypes();
    }
}
