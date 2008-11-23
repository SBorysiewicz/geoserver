package org.geoserver.wfs.response;

import java.io.IOException;
import java.io.OutputStream;

import org.geoserver.ows.Response;
import org.geoserver.platform.Operation;
import org.geoserver.platform.ServiceException;
import org.geoserver.wfs.WFS;
import org.geoserver.wfs.WFSException;
import org.geotools.gml3.GML;
import org.geotools.gml3.GMLConfiguration;
import org.geotools.xml.Encoder;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPoint;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

/**
 * Response which handles an individual {@link Geometry} and encodes it as gml.
 * 
 * @author Justin Deoliveira, The Open Planning Project, jdeolive@openplans.org
 *
 */
public class GeometryResponse extends Response {

    /**
     * Service configuration
     */
    private WFS wfs;

    public GeometryResponse(WFS wfs) {
        super( Geometry.class );
        this.wfs = wfs;
    }
    
    public String getMimeType(Object value, Operation operation)
            throws ServiceException {

        return "text/xml; subtype=gml/3.1.1";
    }

    public void write(Object value, OutputStream output, Operation operation)
            throws IOException, ServiceException {
    
        Encoder encoder = new Encoder( new GMLConfiguration() );
        encoder.setEncoding(wfs.getCharSet());
        
        if ( value instanceof Point ) {
            encoder.encode( value, GML.Point, output );
        }
        else if ( value instanceof MultiPoint ) {
            encoder.encode( value, GML.MultiPoint, output );
        }
        else if ( value instanceof LineString ) {
            encoder.encode( value, GML.LineString, output );
        }
        else if ( value instanceof MultiLineString ) {
            encoder.encode( value, GML.MultiLineString, output );
        }
        else if ( value instanceof Polygon ) {
            encoder.encode( value, GML.Polygon, output );
        }
        else if ( value instanceof MultiPolygon ) {
            encoder.encode( value, GML.MultiPolygon, output );
        }
        else {
            throw new WFSException( "Cannot encode geometry of type: " + value.getClass() );
        }
     }
}
