/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web.data.store;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

import org.apache.wicket.markup.html.form.Form;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogFactory;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.web.data.layer.NewLayerPage;
import org.geotools.data.DataAccess;
import org.geotools.data.DataStoreFactorySpi;
import org.geotools.data.DataAccessFactory.Param;
import org.geotools.util.NullProgressListener;
import org.vfny.geoserver.util.DataStoreUtils;

/**
 * Provides a form to configure a new geotools {@link DataAccess}
 * 
 * @author Gabriel Roldan
 */
public class DataAccessNewPage extends AbstractDataAccessPage {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new datastore configuration page to create a new datastore of the given type
     * 
     * @param the
     *            workspace to attach the new datastore to, like in {@link WorkspaceInfo#getId()}
     * 
     * @param dataStoreFactDisplayName
     *            the type of datastore to create, given by its factory display name
     */
    public DataAccessNewPage(final String workspaceId, final String dataStoreFactDisplayName) {
        super();
        if (workspaceId == null) {
            throw new NullPointerException("workspaceId can't be null");
        }
        if (null == getCatalog().getWorkspace(workspaceId)) {
            throw new IllegalArgumentException("Workspace not found. Id: '" + workspaceId + "'");
        }
        this.workspaceId = workspaceId;

        final DataStoreFactorySpi dsFact = DataStoreUtils.aquireFactory(dataStoreFactDisplayName);
        if (dsFact == null) {
            throw new IllegalArgumentException("Can't locate a datastore factory named '"
                    + dataStoreFactDisplayName + "'");
        }

        // pre-populate map with default values

        Param[] parametersInfo = dsFact.getParametersInfo();
        for (int i = 0; i < parametersInfo.length; i++) {
            Serializable value;
            final Param param = parametersInfo[i];
            if (param.sample == null || param.sample instanceof Serializable) {
                value = (Serializable) param.sample;
            } else {
                value = String.valueOf(param.sample);
            }

            // as for GEOS-2080, we need to pre-populate the namespace parameter
            // value with the namespace uri from the parent 'folder'
            if ("namespace".equals(param.key) && value == null) {
                final Catalog catalog = getCatalog();
                final WorkspaceInfo ws = catalog.getWorkspace(workspaceId);
                final String nsPrefix = ws.getName();
                final NamespaceInfo nsInfo = catalog.getNamespaceByPrefix(nsPrefix);
                if (nsInfo == null) {
                    throw new IllegalStateException("No matching namespace for workspace "
                            + workspaceId);
                }
                final String nsUri = nsInfo.getURI();
                value = nsUri;
            }

            parametersMap.put(param.key, value);
        }
        parametersMap.put(DATASTORE_NAME_PROPERTY_NAME, null);
        parametersMap.put(DATASTORE_DESCRIPTION_PROPERTY_NAME, null);
        parametersMap.put(DATASTORE_ENABLED_PROPERTY_NAME, Boolean.TRUE);

        initUI(dsFact, true);
    }

    /**
     * Callback method called when the submit button have been hit and the parameters validation has
     * succeed.
     * 
     * @param paramsForm
     *            the form to report any error to
     * @see AbstractDataAccessPage#onSaveDataStore(Form)
     */
    protected final void onSaveDataStore(final Form paramsForm) {
        final Catalog catalog = getCatalog();
        final Map<String, Serializable> dsParams = parametersMap;

        DataStoreInfo dataStoreInfo;

        // dataStoreId already validated, so its safe to use
        final String dataStoreUniqueName = (String) dsParams.get(DATASTORE_NAME_PROPERTY_NAME);
        final String description = (String) dsParams.get(DATASTORE_DESCRIPTION_PROPERTY_NAME);
        final Boolean enabled = (Boolean) dsParams.get(DATASTORE_ENABLED_PROPERTY_NAME);

        final WorkspaceInfo workspace = catalog.getWorkspace(workspaceId);

        CatalogFactory factory = catalog.getFactory();
        dataStoreInfo = factory.createDataStore();
        dataStoreInfo.setName(dataStoreUniqueName);
        dataStoreInfo.setWorkspace(workspace);
        dataStoreInfo.setDescription(description);
        dataStoreInfo.setEnabled(enabled.booleanValue());

        Map<String, Serializable> connectionParameters;
        connectionParameters = dataStoreInfo.getConnectionParameters();
        connectionParameters.clear();
        connectionParameters.putAll(dsParams);
        connectionParameters.remove(DATASTORE_NAME_PROPERTY_NAME);
        connectionParameters.remove(DATASTORE_DESCRIPTION_PROPERTY_NAME);
        connectionParameters.remove(DATASTORE_ENABLED_PROPERTY_NAME);

        try {
            dataStoreInfo.getDataStore(new NullProgressListener());
        } catch (IOException e) {
            paramsForm.error("Error creating data store, check the parameters. Error message: "
                    + e.getMessage());
            return;
        }
        try {
            catalog.add(dataStoreInfo);
        } catch (Exception e) {
            paramsForm.error("Error creating data store with the provided parameters: "
                    + e.getMessage());
            return;
        }
        setResponsePage(new NewLayerPage(dataStoreInfo.getId()));
    }

}