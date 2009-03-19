/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web.data.table;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.model.Model;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.web.GeoServerSecuredPage;
import org.geoserver.web.data.NewDataPage;
import org.geoserver.web.wicket.WorkspaceChoice;

/**
 * Page listing all the available stores. Follows the usual filter/sort/page
 * approach, provides ways to bulk delete stores and to add new ones
 * 
 * @author Andrea Aime - OpenGeo
 */
public class StorePage extends GeoServerSecuredPage {
    StoreProvider provider = new StoreProvider();

    StorePanel table;

    public StorePage() {
        table = new StorePanel( "table", provider );
        table.setOutputMarkupId(true);
        add(table);

        // the workspaces drop down
        add(workspacesDropDown());
    }

    private DropDownChoice workspacesDropDown() {
        final DropDownChoice workspaces;
        workspaces = new WorkspaceChoice("wsDropDown", new Model(null));
        workspaces.add(new AjaxFormComponentUpdatingBehavior("onchange") {

            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                if(workspaces.getModelObject() != null) {
                    WorkspaceInfo ws = (WorkspaceInfo) workspaces.getModelObject();
                    setResponsePage(new NewDataPage(ws.getName()));
                }
            }
        });
        return workspaces;
    }
}
