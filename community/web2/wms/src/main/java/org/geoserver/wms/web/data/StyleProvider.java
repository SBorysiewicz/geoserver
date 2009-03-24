package org.geoserver.wms.web.data;

import java.util.Arrays;
import java.util.List;

import org.apache.wicket.model.IModel;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.web.wicket.GeoServerDataProvider;

public class StyleProvider extends GeoServerDataProvider<StyleInfo> {

    public static Property<StyleInfo> NAME = 
        new BeanProperty<StyleInfo>( "name", "name" );

    public static Property<StyleInfo> REMOVE = 
        new PropertyPlaceholder<StyleInfo>( "remove" );

    static List PROPERTIES = Arrays.asList( NAME, REMOVE );
    
    @Override
    protected List<StyleInfo> getItems() {
        return getCatalog().getStyles();
    }

    @Override
    protected List<Property<StyleInfo>> getProperties() {
        return PROPERTIES;
    }

    public IModel model(Object object) {
        return new StyleDetachableModel( (StyleInfo) object );
    }

}