/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2008, Open Source Geospatial Foundation (OSGeo)
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
package org.geotools.xacml.geoxacml.cond;

import java.net.URISyntaxException;
import java.util.List;

import org.geotools.xacml.geoxacml.attr.GeometryAttribute;

import com.sun.xacml.EvaluationCtx;
import com.sun.xacml.attr.AttributeValue;
import com.sun.xacml.cond.EvaluationResult;
import com.vividsolutions.jts.geom.Geometry;

/**
 * Calculates the convex hull
 * 
 * @author Christian Mueller
 * 
 */
public class GeometryConvexHull extends GeometryConstructFunction {

    public static final String NAME = NAME_PREFIX + "geometry-convex-hull";

    public GeometryConvexHull() {
        super(NAME, 0, new String[] { GeometryAttribute.identifier },
                new boolean[] { false, false }, GeometryAttribute.identifier, false);
    }

    public EvaluationResult evaluate(List inputs, EvaluationCtx context) {

        AttributeValue[] argValues = new AttributeValue[inputs.size()];
        EvaluationResult result = evalArgs(inputs, context, argValues);
        if (result != null)
            return result;

        GeometryAttribute geomAttr = (GeometryAttribute) (argValues[0]);

        Geometry resultGeom = null;

        try {
            resultGeom = geomAttr.getGeometry().convexHull();
        } catch (Throwable t) {
            return exceptionError(t);
        }

        GeometryAttribute resultAttr = null;

        try {
            resultAttr = new GeometryAttribute(resultGeom, geomAttr.getSrsName(), null, null, null);
        } catch (URISyntaxException e) {
            // should not happend
        }
        return new EvaluationResult(resultAttr);

    }

}
