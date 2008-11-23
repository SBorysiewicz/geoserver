/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.vfny.geoserver.wms.responses.map.svg;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.batik.svggen.SVGGeneratorContext;
import org.apache.batik.svggen.SVGGraphics2D;
import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.geoserver.platform.ServiceException;
import org.geotools.map.MapContext;
import org.geotools.renderer.lite.StreamingRenderer;
import org.vfny.geoserver.global.Service;
import org.vfny.geoserver.global.WMS;
import org.vfny.geoserver.wms.GetMapProducer;
import org.vfny.geoserver.wms.WMSMapContext;
import org.vfny.geoserver.wms.WmsException;
import org.vfny.geoserver.wms.responses.AbstractGetMapProducer;
import org.w3c.dom.Document;

import com.vividsolutions.jts.geom.Envelope;

/**
 * Renders svg using the Batik SVG Toolkit. An SVG context is created for a map
 * and then passed of to {@link org.geotools.renderer.lite.StreamingRenderer}.
 * 
 * @author Justin Deoliveira, The Open Planning Project
 * 
 */
class SVGBatikMapProducer extends AbstractGetMapProducer implements
		GetMapProducer {
	StreamingRenderer renderer;

	WMS wms;

	public SVGBatikMapProducer(String mimeType, String[] outputFormats, WMS wms) {
	    super(mimeType, outputFormats);
		this.wms = wms;
	}

	public void abort() {
		if (renderer != null) {
			renderer.stopRendering();
		}
	}

	public void abort(Service gs) {
		if (renderer != null) {
			renderer.stopRendering();
		}
	}

	public void produceMap() throws WmsException {
		renderer = new StreamingRenderer();

		// optimized data loading was not here, but yet it seems sensible to
		// have it...
		Map rendererParams = new HashMap();
		rendererParams.put("optimizedDataLoadingEnabled", new Boolean(true));
		rendererParams.put("renderingBuffer", new Integer(mapContext
				.getBuffer()));
		renderer.setRendererHints(rendererParams);
		renderer.setContext(mapContext);
	}

	public void writeTo(OutputStream out) throws ServiceException, IOException {
		try {
			MapContext map = renderer.getContext();
			double width = -1;
			double height = -1;

			if (map instanceof WMSMapContext) {
				WMSMapContext wmsMap = (WMSMapContext) map;
				width = wmsMap.getMapWidth();
				height = wmsMap.getMapHeight();
			} else {
				// guess a width and height based on the envelope
				Envelope area = map.getAreaOfInterest();

				if ((area.getHeight() > 0) && (area.getWidth() > 0)) {
					if (area.getHeight() >= area.getWidth()) {
						height = 600;
						width = height * (area.getWidth() / area.getHeight());
					} else {
						width = 800;
						height = width * (area.getHeight() / area.getWidth());
					}
				}
			}

			if ((height == -1) || (width == -1)) {
				throw new IOException("Could not determine map dimensions");
			}

			SVGGeneratorContext context = setupContext();
			SVGGraphics2D g = new SVGGraphics2D(context, true);

			g.setSVGCanvasSize(new Dimension((int) width, (int) height));

			// turn off/on anti aliasing
			if (wms.isSvgAntiAlias()) {
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
						RenderingHints.VALUE_ANTIALIAS_ON);
			} else {
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
						RenderingHints.VALUE_ANTIALIAS_OFF);
			}

			Rectangle r = new Rectangle(g.getSVGCanvasSize());

      renderer.paint(g, r, renderer.getContext().getAreaOfInterest());

			// This method of output does not output the DOCTYPE definiition
			// TODO: make a config option that toggles wether doctype is
			// written out.
			OutputFormat format = new OutputFormat();
			XMLSerializer serializer = new XMLSerializer(
					new OutputStreamWriter(out, "UTF-8"), format);

            // this method does output the DOCTYPE def
             g.stream(new OutputStreamWriter(out,"UTF-8"));
        } catch (Exception e) {
            new IOException().initCause(e);
        } finally {
            // free up memory
            renderer = null;
        }
    }


	private SVGGeneratorContext setupContext()
			throws FactoryConfigurationError, ParserConfigurationException {
		Document document = null;

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

		DocumentBuilder db = dbf.newDocumentBuilder();

		// Create an instance of org.w3c.dom.Document
		String svgNamespaceURI = "http://www.w3.org/2000/svg";
		document = db.getDOMImplementation().createDocument(svgNamespaceURI,
				"svg", null);

		// Set up the context
		return SVGGeneratorContext.createDefault(document);
	}
}
