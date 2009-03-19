package org.geoserver.web.data.workspace;

import static org.geoserver.web.data.workspace.WorkspaceProvider.NAME;
import static org.geoserver.web.data.workspace.WorkspaceProvider.REMOVE;

import java.util.List;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.model.IModel;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogBuilder;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.web.GeoServerSecuredPage;
import org.geoserver.web.wicket.ConfirmationAjaxLink;
import org.geoserver.web.wicket.GeoServerTablePanel;
import org.geoserver.web.wicket.SimpleAjaxLink;
import org.geoserver.web.wicket.GeoServerDataProvider.Property;

public class WorkspacePage extends GeoServerSecuredPage {
    WorkspaceProvider provider = new WorkspaceProvider();
    
    public WorkspacePage() {
        
        add(new GeoServerTablePanel<WorkspaceInfo>("table", provider) {
            @Override
            protected Component getComponentForProperty(String id, IModel itemModel,
                    Property<WorkspaceInfo> property) {
                if ( property == NAME ) {
                    return workspaceLink(id, itemModel);
                }
                if ( property == REMOVE ) {
                    return removeWorkspaceLink(id, itemModel);
                }
                
                throw new IllegalArgumentException("No such property "+ property.getName());
            }
        });
        
        BookmarkablePageLink newLink = new BookmarkablePageLink( "new", WorkspaceNewPage.class);
        add( newLink );
    }
    
    Component workspaceLink(String id, final IModel itemModel) {
        return new SimpleAjaxLink(id, WorkspaceProvider.NAME.getModel(itemModel)) {
            @Override
            public void onClick(AjaxRequestTarget target) {
                String wsid = getModelObjectAsString();
                WorkspaceInfo ws = getCatalog().getWorkspace( wsid );
                setResponsePage( new WorkspaceEditPage( ws ) );
            }
        };
    }
    
    Component removeWorkspaceLink(String id, final IModel itemModel ) {
        final WorkspaceInfo workspace = (WorkspaceInfo) itemModel.getObject();
        StringBuilder sb = new StringBuilder();
        sb.append( "Are sure you sure want to remove workspace " ).append( workspace.getName() );
        sb.append( ". Doing so will remove every store that is part of the workspace." );
        
        return new ConfirmationAjaxLink( id, null, "remove", sb.toString() ) {
            @Override
            protected void onClick(AjaxRequestTarget target) {
                CatalogBuilder cb = new CatalogBuilder( getCatalog() );
                cb.removeWorkspace( workspace, true );
                setResponsePage(WorkspacePage.this);
            }
        };
    }
}