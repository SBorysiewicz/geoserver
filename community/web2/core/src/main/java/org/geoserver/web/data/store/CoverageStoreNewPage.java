/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web.data.store;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.web.data.layer.NewLayerPage;
import org.opengis.coverage.grid.Format;

/**
 * Supports coverage store configuration
 * 
 * @author Andrea Aime
 */
@SuppressWarnings("serial")
public class CoverageStoreNewPage extends AbstractCoverageStorePage {

    /**
     * 
     * @param workspaceId
     *            the {@link WorkspaceInfo#getId() id} of the workspace to attach the new coverage
     *            to
     * @param coverageFactoryName
     *            the {@link Format#getName() name} of the format to create a new raster coverage
     *            for
     */
    public CoverageStoreNewPage(final String workspaceId, final String coverageFactoryName) {
        Catalog catalog = getCatalog();
        final WorkspaceInfo workspace = catalog.getWorkspace(workspaceId);
        CoverageStoreInfo store = catalog.getFactory().createCoverageStore();
        store.setWorkspace(workspace);
        store.setType(coverageFactoryName);
        store.setEnabled(true);

        initUI(store);
    }

    protected void onSave(final CoverageStoreInfo info) {
        getCatalog().save(info);
        setResponsePage(new NewLayerPage(info.getId()));
    }

}