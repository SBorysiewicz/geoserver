package org.geoserver.web.data.layergroup;

import org.apache.wicket.model.LoadableDetachableModel;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.web.GeoServerApplication;

public class LayerGroupEntry {

    LayerInfo layer;
    StyleInfo style;
    int index;
    
    public LayerGroupEntry( LayerInfo layer, StyleInfo style, int index ) {
        this.layer = layer;
        this.style = style;
        this.index = index;
    }
    
    public LoadableDetachableModel toDetachableModel() {
        return new LayerGroupEntryModel( this );
    }
    
    public static class LayerGroupEntryModel extends LoadableDetachableModel {

        String lid;
        String sid;
        int index;
        
        public LayerGroupEntryModel( LayerGroupEntry entry ) {
            lid = entry.layer.getId();
            sid = entry.style.getId();
            index = entry.index;
        }
        
        @Override
        protected Object load() {
            Catalog catalog = GeoServerApplication.get().getCatalog();
            LayerInfo l = catalog.getLayer( lid );
            StyleInfo s = catalog.getStyle( sid );
            return new LayerGroupEntry( l, s, index );
        }
        
    }
}