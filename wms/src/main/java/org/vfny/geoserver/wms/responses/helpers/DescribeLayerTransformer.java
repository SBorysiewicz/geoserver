/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org.  All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.vfny.geoserver.wms.responses.helpers;

import java.util.Iterator;
import java.util.List;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;

import org.geoserver.ows.util.ResponseUtils;
import org.geotools.xml.transform.TransformerBase;
import org.geotools.xml.transform.Translator;
import org.vfny.geoserver.global.MapLayerInfo;
import org.vfny.geoserver.wms.requests.DescribeLayerRequest;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.AttributesImpl;


/**
 * <code>org.geotools.xml.transform.TransformerBase</code> specialized in
 * producing a WMS DescribeLayer response.
 *
 * @author Gabriel Roldan, Axios Engineering
 * @version $Id$
 */
public class DescribeLayerTransformer extends TransformerBase {
    /** The base url upon URLs which point to 'me' should be based. */
    private String serverBaseUrl;

    /**
     * Creates a new DescribeLayerTransformer object.
     * 
     * @param serverBaseUrl
     *                the server base url which to append the
     *                "schemas/wms/1.1.1/WMS_DescribeLayerResponse.dtd" dtd location to for the
     *                response. If proxified, shall be resolved before calling this constructor and
     *                give it the actual URL to use. No "proxification" will be performed by this
     *                transformer.
     */
    public DescribeLayerTransformer(final String serverBaseUrl) {
        super();

        if (serverBaseUrl == null) {
            throw new NullPointerException("serverBaseUrl");
        }

        this.serverBaseUrl = serverBaseUrl;
    }

    /**
     * Creates and returns a Translator specialized in producing
     * a DescribeLayer response document.
     *
     * @param handler the content handler to send sax events to.
     *
     * @return a new <code>DescribeLayerTranslator</code>
     */
    public Translator createTranslator(ContentHandler handler) {
        return new DescribeLayerTranslator(handler);
    }

    /**
     * Gets the <code>Transformer</code> created by the overriden method in
     * the superclass and adds it the DOCTYPE token pointing to the
     * DescribeLayer DTD on this server instance.
     *
     * <p>
     * The DTD is set at the fixed location given by the <code>schemaBaseUrl</code>
     * passed to the constructor <code>+ "wms/1.1.1/WMS_DescribeLayerResponse.dtd</code>.
     * </p>
     *
     * @return a Transformer propoerly configured to produce DescribeLayer responses.
     *
     * @throws TransformerException if it is thrown by <code>super.createTransformer()</code>
     */
    public Transformer createTransformer() throws TransformerException {
        Transformer transformer = super.createTransformer();
        String dtdUrl = ResponseUtils.appendPath(serverBaseUrl,
                "schemas/wms/1.1.1/WMS_DescribeLayerResponse.dtd");
        transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, dtdUrl);

        return transformer;
    }

    /**
     * Sends SAX events to produce a DescribeLayer response document.
     *
     * @author Gabriel Roldan, Axios Engineering
     * @version $Id$
     */
    private class DescribeLayerTranslator extends TranslatorSupport {
        /**
         * Creates a new DescribeLayerTranslator object.
         *
         * @param handler DOCUMENT ME!
         */
        public DescribeLayerTranslator(ContentHandler handler) {
            super(handler, null, null);
        }

        /**
         * Encode the object.
         *
         * @param o The {@link DescribeLayerRequest} to encode a DescribeLayer response for
         *
         * @throws IllegalArgumentException if the Object is not encodeable.
         */
        public void encode(Object o) throws IllegalArgumentException {
            if (!(o instanceof DescribeLayerRequest)) {
                throw new IllegalArgumentException();
            }

            DescribeLayerRequest req = (DescribeLayerRequest) o;

            AttributesImpl versionAtt = new AttributesImpl();
            final String requestVersion = req.getVersion();
            if(requestVersion == null){
                throw new NullPointerException("requestVersion");
            }

            versionAtt.addAttribute("", "version", "version", "", requestVersion);

            start("WMS_DescribeLayerResponse", versionAtt);

            handleLayers(req);

            end("WMS_DescribeLayerResponse");
        }

        /**
         * As currently GeoServer does not have support for nested layers, this
         * method declares a <code>LayerDescription</code> element for each
         * featuretype requested.
         *
         * @param req
         */
        private void handleLayers(DescribeLayerRequest req) {
            MapLayerInfo layer;

            final List layers = req.getLayers();
            

            AttributesImpl queryAtts = new AttributesImpl();
            queryAtts.addAttribute("", "typeName", "typeName", "", "");

            for (Iterator it = layers.iterator(); it.hasNext();) {
                layer = (MapLayerInfo) it.next();

                AttributesImpl layerAtts = new AttributesImpl();
                layerAtts.addAttribute("", "name", "name", "", "");
                String owsUrl;
                String owsType;
                if (MapLayerInfo.TYPE_VECTOR == layer.getType()) {
                    // REVISIT: not sure why we need WfsDispatcher, "wfs?" should suffice imho
                    owsUrl = ResponseUtils.appendPath(serverBaseUrl, "wfs/WfsDispatcher?");
                    owsType = "WFS";
                    layerAtts.addAttribute("", "wfs", "wfs", "", owsUrl);
                } else if (MapLayerInfo.TYPE_RASTER == layer.getType()) {
                    owsUrl = ResponseUtils.appendPath(serverBaseUrl, "wcs?");
                    owsType = "WCS";
                } else {
                    // non vector nor raster layer, LayerDescription will not contain these
                    // attributes
                    owsUrl = owsType = null;
                }

                if (owsType != null && owsUrl != null) {
                    // the layer is describable only if its vector or raster based
                    // in our case that meand directly associated to a resourceInfo (ie, no base
                    // map)
                    layerAtts.addAttribute("", "owsURL", "owsURL", "", owsUrl);
                    layerAtts.addAttribute("", "owsType", "owsType", "", owsType);
                }

                layerAtts.setAttribute(0, "", "name", "name", "", layer.getName());
                start("LayerDescription", layerAtts);

                queryAtts.setAttribute(0, "", "typeName", "typeName", "", layer.getName());
                element("Query", null, queryAtts);

                end("LayerDescription");
            }
        }
    }
}