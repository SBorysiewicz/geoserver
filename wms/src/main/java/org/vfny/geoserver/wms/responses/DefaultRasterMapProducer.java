/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org.  All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.vfny.geoserver.wms.responses;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.RenderedImage;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.media.jai.ImageLayout;
import javax.media.jai.Interpolation;
import javax.media.jai.InterpolationBicubic2;
import javax.media.jai.InterpolationBilinear;
import javax.media.jai.InterpolationNearest;
import javax.media.jai.JAI;
import javax.media.jai.LookupTableJAI;
import javax.media.jai.operator.LookupDescriptor;

import org.geoserver.platform.ServiceException;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.MapLayer;
import org.geotools.renderer.RenderListener;
import org.geotools.renderer.shape.ShapefileRenderer;
import org.geotools.styling.PointSymbolizer;
import org.geotools.styling.Style;
import org.geotools.styling.visitor.DuplicatingStyleVisitor;
import org.opengis.feature.simple.SimpleFeature;
import org.vfny.geoserver.config.WMSConfig;
import org.vfny.geoserver.global.WMS;
import org.vfny.geoserver.wms.RasterMapProducer;
import org.vfny.geoserver.wms.WmsException;
import org.vfny.geoserver.wms.requests.GetMapRequest;
import org.vfny.geoserver.wms.responses.map.metatile.MetatileMapProducer;
import org.vfny.geoserver.wms.responses.palette.InverseColorMapOp;

/**
 * Abstract base class for GetMapProducers that relies in LiteRenderer for
 * creating the raster map and then outputs it in the format they specializes
 * in.
 * 
 * <p>
 * This class does the job of producing a BufferedImage using geotools
 * LiteRenderer, so it should be enough for a subclass to implement
 * {@linkPlain #formatImageOutputStream(String, BufferedImage, OutputStream)}
 * </p>
 * 
 * <p>
 * Generates a map using the geotools jai rendering classes. Uses the Lite
 * renderer, loading the data on the fly, which is quite nice. Thanks Andrea and
 * Gabriel. The word is that we should eventually switch over to
 * StyledMapRenderer and do some fancy stuff with caching layers, but I think we
 * are a ways off with its maturity to try that yet. So Lite treats us quite
 * well, as it is stateless and therefore loads up nice and fast.
 * </p>
 * 
 * <p>
 * </p>
 * 
 * @author Chris Holmes, TOPP
 * @author Simone Giannecchini, GeoSolutions
 * @version $Id$
 */
public abstract class DefaultRasterMapProducer extends
		AbstractRasterMapProducer implements RasterMapProducer {
	private final static Interpolation NN_INTERPOLATION = new InterpolationNearest();

	private final static Interpolation BIL_INTERPOLATION = new InterpolationBilinear();

	private final static Interpolation BIC_INTERPOLATION = new InterpolationBicubic2(
			0);

	// antialiasing settings, no antialias, only text, full antialias
	private final static String AA_NONE = "NONE";

	private final static String AA_TEXT = "TEXT";

	private final static String AA_FULL = "FULL";

	private final static List AA_SETTINGS = Arrays.asList(new String[] {
			AA_NONE, AA_TEXT, AA_FULL });

	/**
	 * The lookup table used for data type transformation (it's really the identity one)
	 */
	private static LookupTableJAI IDENTITY_TABLE = new LookupTableJAI(getTable());
    private static byte[] getTable() {
        byte[] arr = new byte[256];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = (byte) i;
        }
        return arr;
    }

	/** WMS Service configuration * */
	private WMS wms;

	/** A logger for this class. */
	private static final Logger LOGGER = org.geotools.util.logging.Logging.getLogger("org.vfny.geoserver.responses.wms.map");

	/** Which format to encode the image in if one is not supplied */
	private static final String DEFAULT_MAP_FORMAT = "image/png";
	
	/** The Watermark Painter instance **/
	private WatermarkPainter wmPainter;


	/**
	 * 
	 */
	public DefaultRasterMapProducer() {
		this(DEFAULT_MAP_FORMAT, null);
	}

	/**
	 * 
	 */
	public DefaultRasterMapProducer(WMS wms) {
		this(DEFAULT_MAP_FORMAT, wms);
	}

	/**
	 * @param the mime type to be written down as an HTTP header when a map of this format is generated
	 */
	public DefaultRasterMapProducer(String mime, WMS wms) {
		super(mime);
		this.wms = wms;
	}

    public DefaultRasterMapProducer(String mime, String[] outputFormats, WMS wms) {
        super(mime, outputFormats);
        this.wms = wms;
    }

    /**
	 * Writes the image to the client.
	 * 
	 * @param out
	 *            The output stream to write to.
	 * 
	 * @throws org.vfny.geoserver.ServiceException
	 *             DOCUMENT ME!
	 * @throws java.io.IOException
	 *             DOCUMENT ME!
	 */
	public void writeTo(OutputStream out)
			throws ServiceException, java.io.IOException {
		formatImageOutputStream(this.image, out);
	}

	/**
	 * Performs the execute request using geotools rendering.
	 * 
	 * @param map
	 *            The information on the types requested.
	 * 
	 * @throws WmsException
	 *             For any problems.
	 */
	public void produceMap() throws WmsException {

		final int width = mapContext.getMapWidth();
		final int height = mapContext.getMapHeight();

		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.fine(new StringBuffer("setting up ").append(width).append(
					"x").append(height).append(" image").toString());
		}

		// extra antialias setting
		final GetMapRequest request = mapContext.getRequest();
        String antialias = (String) request.getFormatOptions().get("antialias");
		if (antialias != null)
			antialias = antialias.toUpperCase();

		// figure out a palette for buffered image creation
		IndexColorModel palette = null;
		final InverseColorMapOp paletteInverter = mapContext
				.getPaletteInverter();
		final boolean transparent = mapContext.isTransparent();
        final Color bgColor = mapContext.getBgColor();
        if (paletteInverter != null && AA_NONE.equals(antialias)) {
			palette = paletteInverter.getIcm();
		} else if (AA_NONE.equals(antialias)) {
			PaletteExtractor pe = new PaletteExtractor(transparent ? null : bgColor);
			MapLayer[] layers = mapContext.getLayers();
			for (int i = 0; i < layers.length; i++) {
				pe.visit(layers[i].getStyle());
				if (!pe.canComputePalette())
					break;
			}
			if (pe.canComputePalette())
				palette = pe.getPalette();
		}

		// we use the alpha channel if the image is transparent or if the meta tiler
		// is enabled, since apparently the Crop operation inside the meta-tiler
		// generates striped images in that case (see GEOS-
		boolean useAlpha = transparent || MetatileMapProducer.isRequestTiled(request, this);
        final RenderedImage preparedImage = prepareImage(width, height, palette, useAlpha);
        final Map hintsMap = new HashMap();

        final Graphics2D graphic = ImageUtils.prepareTransparency(transparent, bgColor,
                preparedImage, hintsMap);

		// set up the antialias hints
		if (AA_NONE.equals(antialias)) {
			hintsMap.put(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_OFF);
			if (preparedImage.getColorModel() instanceof IndexColorModel) {
				// otherwise we end up with dithered colors where the match is
				// not 100%
				hintsMap.put(RenderingHints.KEY_DITHERING,
						RenderingHints.VALUE_DITHER_DISABLE);
			}
		} else if (AA_TEXT.equals(antialias)) {
			hintsMap.put(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_OFF);
			hintsMap.put(RenderingHints.KEY_TEXT_ANTIALIASING,
					RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		} else {
			if (antialias != null && !AA_FULL.equals(antialias)) {
				LOGGER.warning("Unrecognized antialias setting '" + antialias
						+ "', valid values are " + AA_SETTINGS);
			}
			hintsMap.put(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
		}

		// turn off/on interpolation rendering hint
		if ((wms != null)
				&& WMSConfig.INT_NEAREST.equals(wms.getAllowInterpolation())) {
			hintsMap.put(JAI.KEY_INTERPOLATION, NN_INTERPOLATION);
			hintsMap.put(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		} else if ((wms != null)
				&& WMSConfig.INT_BIlINEAR.equals(wms.getAllowInterpolation())) {
			hintsMap.put(JAI.KEY_INTERPOLATION, BIL_INTERPOLATION);
			hintsMap.put(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		} else if ((wms != null)
				&& WMSConfig.INT_BICUBIC.equals(wms.getAllowInterpolation())) {
			hintsMap.put(JAI.KEY_INTERPOLATION, BIC_INTERPOLATION);
			hintsMap.put(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		}
		// line look better with this hint, they are less blurred
		hintsMap.put(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

		// make sure the hints are set before we start rendering the map
		graphic.setRenderingHints(hintsMap);

		Rectangle paintArea = new Rectangle(width, height);
		RenderingHints hints = new RenderingHints(hintsMap);
		renderer = new ShapefileRenderer();
		renderer.setContext(mapContext);
		renderer.setJava2DHints(hints);
		// shapefile renderer won't log rendering errors, sigh, we have to do it manually
		if(renderer instanceof ShapefileRenderer && LOGGER.isLoggable(Level.FINE)) {
		    renderer.addRenderListener(new RenderListener() {
            
                public void featureRenderer(SimpleFeature feature) {
                }
            
                public void errorOccurred(Exception e) {
                    LOGGER.log(Level.FINE, "Rendering error occurred", e);
                }
            
            });
		}

		// setup the renderer hints
		Map rendererParams = new HashMap();
		rendererParams.put("optimizedDataLoadingEnabled", new Boolean(true));
		rendererParams.put("renderingBuffer", new Integer(mapContext
				.getBuffer()));
		rendererParams.put("maxFiltersToSendToDatastore", new Integer(20));
		rendererParams.put(ShapefileRenderer.SCALE_COMPUTATION_METHOD_KEY,
				ShapefileRenderer.SCALE_OGC);
		if(AA_NONE.equals(antialias)) {
		    rendererParams.put(ShapefileRenderer.TEXT_RENDERING_KEY, 
		            ShapefileRenderer.TEXT_RENDERING_STRING);
		} else {
		    rendererParams.put(ShapefileRenderer.TEXT_RENDERING_KEY, 
                    ShapefileRenderer.TEXT_RENDERING_OUTLINE);
		}

        boolean kmplacemark = false;
        if (mapContext.getRequest().getFormatOptions().get("kmplacemark") != null)
            kmplacemark = ((Boolean) mapContext.getRequest().getFormatOptions()
                    .get("kmplacemark")).booleanValue();
        if (kmplacemark) {
            // create a StyleVisitor that copies a style, but removes the
            // PointSymbolizers and TextSymbolizers
            DuplicatingStyleVisitor dupVisitor = new DuplicatingStyleVisitor() {
                public void visit(PointSymbolizer ps) {
                    pages.push(null);
                }

                public void visit(org.geotools.styling.TextSymbolizer ts) {
                    pages.push(null);
                }
            };

            // Remove PointSymbolizers and TextSymbolizers from the
            // layers' Styles to prevent their rendering on the
            // raster image. Both are better served with the
            // placemarks.
            MapLayer[] layers = mapContext.getLayers();
            for (int i = 0; i < layers.length; i++) {
                Style style = layers[i].getStyle();
                style.accept(dupVisitor);
                Style copy = (Style) dupVisitor.getCopy();
                layers[i].setStyle(copy);
            }
        }
		renderer.setRendererHints(rendererParams);
		
		// if abort already requested bail out
		if (this.abortRequested) {
			graphic.dispose();
			return;
		}

		// finally render the image
		final ReferencedEnvelope dataArea = mapContext.getAreaOfInterest();
		renderer.paint(graphic, paintArea, dataArea);
		
        // apply watermarking
        try {
            if (wmPainter != null)
                this.wmPainter.paint(graphic, paintArea);
        } catch (Exception e) {
            throw new WmsException("Problem occurred while trying to watermark data", "", e);
        } 

		
		graphic.dispose();
		if (!this.abortRequested) {
            if(palette != null && palette.getMapSize() < 256)
                this.image = optimizeSampleModel(preparedImage);
            else 
                this.image = preparedImage;
		}
	}
	
    /**
     * Set the Watermark Painter.
     * 
     * @param wmPainter
     *            the wmPainter to set
     */
    public void setWmPainter(WatermarkPainter wmPainter) {
        this.wmPainter = wmPainter;
    }

    /**
     * Sets up a {@link BufferedImage#TYPE_4BYTE_ABGR} if the paletteInverter is
     * not provided, or a indexed image otherwise. Subclasses may override this
     * method should they need a special kind of image
     * 
     * @param width
     * @param height
     * @param paletteInverter
     * @return
     */
	protected RenderedImage prepareImage(int width, int height,
			IndexColorModel palette, boolean transparent) {
	    return ImageUtils.createImage(width, height, palette, transparent);
	}

	/**
	 * @param originalImage
	 * @return
	 */
	protected RenderedImage forceIndexed8Bitmask(RenderedImage originalImage) {
        return ImageUtils.forceIndexed8Bitmask(originalImage, mapContext.getPaletteInverter());
	}
    
	/**
	 * This takes an image with an indexed color model that uses
	 * less than 256 colors and has a 8bit sample model, and transforms it to
	 * one that has the optimal sample model (for example, 1bit if the palette only has 2 colors)
	 * @param source
	 * @return
	 */
    private RenderedImage optimizeSampleModel(RenderedImage source) {
        int w = source.getWidth();
        int h = source.getHeight();
        ImageLayout layout = new ImageLayout();
        layout.setColorModel(source.getColorModel());
        layout.setSampleModel(source.getColorModel().createCompatibleSampleModel(w, h));
        // if I don't force tiling off with this setting an exception is thrown
        // when writing the image out...
        layout.setTileWidth(w);
        layout.setTileHeight(h);
        RenderingHints hints = new RenderingHints(JAI.KEY_IMAGE_LAYOUT, layout);
        return LookupDescriptor.create(source, IDENTITY_TABLE, hints);
    }

}
