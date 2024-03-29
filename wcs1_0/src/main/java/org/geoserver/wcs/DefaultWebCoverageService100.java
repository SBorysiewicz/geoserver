/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wcs;

import static org.vfny.geoserver.wcs.WcsException.WcsExceptionCode.InvalidParameterValue;

import java.awt.Rectangle;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.media.jai.Interpolation;

import net.opengis.gml.CodeType;
import net.opengis.gml.DirectPositionType;
import net.opengis.gml.RectifiedGridType;
import net.opengis.gml.VectorType;
import net.opengis.gml.impl.TimePositionTypeImpl;
import net.opengis.wcs10.AxisSubsetType;
import net.opengis.wcs10.DescribeCoverageType;
import net.opengis.wcs10.DomainSubsetType;
import net.opengis.wcs10.GetCapabilitiesType;
import net.opengis.wcs10.GetCoverageType;
import net.opengis.wcs10.InterpolationMethodType;
import net.opengis.wcs10.IntervalType;
import net.opengis.wcs10.OutputType;
import net.opengis.wcs10.RangeSubsetType;
import net.opengis.wcs10.SpatialSubsetType;
import net.opengis.wcs10.TimeSequenceType;
import net.opengis.wcs10.TypedLiteralType;

import org.eclipse.emf.common.util.EList;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.config.GeoServer;
import org.geoserver.data.util.CoverageUtils;
import org.geoserver.ows.util.RequestUtils;
import org.geoserver.wcs.response.Wcs10CapsTransformer;
import org.geoserver.wcs.response.Wcs10DescribeCoverageTransformer;
import org.geotools.coverage.grid.GeneralGridGeometry;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridEnvelope2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.coverage.grid.io.AbstractGridCoverage2DReader;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.geometry.GeneralEnvelope;
import org.geotools.parameter.DefaultParameterDescriptor;
import org.geotools.referencing.CRS;
import org.geotools.referencing.operation.transform.AffineTransform2D;
import org.geotools.resources.CRSUtilities;
import org.opengis.coverage.grid.GridCoverage;
import org.opengis.coverage.grid.GridEnvelope;
import org.opengis.geometry.Envelope;
import org.opengis.parameter.GeneralParameterDescriptor;
import org.opengis.parameter.GeneralParameterValue;
import org.opengis.parameter.ParameterValue;
import org.opengis.parameter.ParameterValueGroup;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.datum.PixelInCell;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.vfny.geoserver.util.WCSUtils;
import org.vfny.geoserver.wcs.WcsException;
import org.vfny.geoserver.wcs.responses.CoverageResponseDelegate;
import org.vfny.geoserver.wcs.responses.CoverageResponseDelegateFactory;

/**
 * The Default WCS 1.0.0 Service implementation
 * 
 * @author Alessio Fabiani, GeoSolutions
 */
public class DefaultWebCoverageService100 implements WebCoverageService100 {

    private WCSInfo wcs;

    private Catalog catalog;

    private GeoServer geoServer;

	private static final Logger LOGGER = org.geotools.util.logging.Logging.getLogger(DefaultWebCoverageService100.class);

    /**
     * 
     * @param geoServer
     */
    public DefaultWebCoverageService100(GeoServer geoServer) {
        this.wcs = geoServer.getService(WCSInfo.class);
        this.geoServer = geoServer;
        this.catalog = geoServer.getCatalog();
    }

    /**
     * 
     */
    public WCSInfo getServiceInfo() {
        return wcs;
    }

    /**
     * 
     */
    public Wcs10CapsTransformer getCapabilities(GetCapabilitiesType request) {
        // do the version negotiation dance
        List<String> provided = new ArrayList<String>();
        provided.add("1.0.0");
        List<String> accepted = null;
        if (request.getVersion() != null) {
            accepted = new ArrayList<String>();
            accepted.add(request.getVersion());
        }
        String version = RequestUtils.getVersionPreOws(provided, accepted);

        if ("1.0.0".equals(version)) {
            Wcs10CapsTransformer capsTransformer = new Wcs10CapsTransformer(geoServer);
            capsTransformer.setEncoding(Charset.forName((wcs.getGeoServer().getGlobal()
                    .getCharset())));
            return capsTransformer;
        }

        throw new WcsException("Could not understand version:" + version);
    }

    /**
     * 
     */
    public Wcs10DescribeCoverageTransformer describeCoverage(DescribeCoverageType request) {
        final String version = request.getVersion();
        if ("1.0.0".equals(version)) {
            Wcs10DescribeCoverageTransformer describeTransformer = new Wcs10DescribeCoverageTransformer(
                    wcs, catalog);
            describeTransformer.setEncoding(Charset.forName((wcs.getGeoServer().getGlobal()
                    .getCharset())));
            return describeTransformer;
        }

        throw new WcsException("Could not understand version:" + version);
    }

    /**
     * 
     */
    public GridCoverage[] getCoverage(final GetCoverageType request) {
        CoverageInfo meta = null;
        GridCoverage2D coverage = null;
        final List<GridCoverage> coverageResults = new ArrayList<GridCoverage>();
        try {

            // acquire coverage info
            meta = catalog.getCoverageByName(request.getSourceCoverage());
            if (meta == null)
                throw new WcsException("Cannot find sourceCoverage on the catalog!");
            
            // first let's run some sanity checks on the inputs
            checkRangeSubset(meta, request.getRangeSubset());
            checkInterpolationMethod(meta, request.getInterpolationMethod());
            checkOutput(meta, request.getOutput());
            
            //
            // PREPARE DOMAIN SUBSET ELEMENT
            //
            final DomainSubsetType domainSubset = request.getDomainSubset();
            // time
            final TimeSequenceType temporalSubset = domainSubset.getTemporalSubset();
            // spatial
            final SpatialSubsetType spatialSubset=domainSubset.getSpatialSubset();
            final EList grids = spatialSubset.getGrid();
            if(grids.size()==0)
            	throw new IllegalArgumentException("Invalid number of Grid for spatial subsetting was set:"+grids.size());
            final RectifiedGridType grid = (RectifiedGridType) grids.get(0);
            final List envelopes = spatialSubset.getEnvelope();
            if(envelopes.size()==0)
            	throw new IllegalArgumentException("Invalid number of Envelope for spatial subsetting was set:"+envelopes.size()); 
            final GeneralEnvelope requestedEnvelope = (GeneralEnvelope) envelopes.get(0);
            
            final OutputType output = request.getOutput();
            if(output==null)
            	throw new IllegalArgumentException("Output type was null");
            final CodeType outputCRS = output.getCrs();
            if (outputCRS == null)
                throw new IllegalArgumentException("Invalid output CRS"); 
            final int dimension = grid.getDimension().intValue();
            // WE SUPPORT 3D DIMENSION ONLY VIA A BAND
            if (dimension == 3) 
          	    throw new WcsException("We support a third dimension only via a specifica Axis in Range", InvalidParameterValue,null);
          

            //
            //	GRAB A READER
            //
            // grab the reader using the default params,
            final AbstractGridCoverage2DReader reader = (AbstractGridCoverage2DReader) meta.getGridCoverageReader(null, WCSUtils.LENIENT_HINT);
            if(reader==null)
            	// cannot instantiate a reader, we should return an empty array
            	return coverageResults.toArray(new GridCoverage2D[] {});
            	
        	
            // get native elements and then play with the the requested ones
            final GeneralEnvelope nativeEnvelope    = reader.getOriginalEnvelope();
            final CoordinateReferenceSystem nativeCRS = nativeEnvelope.getCoordinateReferenceSystem();    

            // get requested crs
            String requestedCRS = outputCRS.getValue();

            // Compute the target crs, the crs that the final coverage will be served into
            final CoordinateReferenceSystem targetCRS;
            if (requestedCRS == null) {
                targetCRS = reader.getOriginalEnvelope().getCoordinateReferenceSystem();
                requestedCRS = CRS.lookupIdentifier(targetCRS, true);
            } else
            	// FORCE LON,LAT!!!!
                targetCRS = CRS.decode(requestedCRS,true);
             
                 

            //
            // PREPARE DESTINATION DIMENSIONS
            //
            final Rectangle destinationSize;
            final AffineTransform2D destinationG2W;
            final GridEnvelope limits = grid.getLimits();
            if (limits != null) {
            	//
            	// we have imposed limits from the request, we just use them as they are
            	//
                final int[] lowers = limits.getLow().getCoordinateValues();
                destinationG2W=null;
                destinationSize = new Rectangle(lowers[0], lowers[1], limits.getSpan(0), limits.getSpan(1));
            } else if (grid.getOffsetVector() != null && grid.getOffsetVector().size() > 0) {
            	//
            	// we have NO imposed limits from the request, we need to create a proper G2W with the RESOLUTION we where given.
            	// Notice that this is specific to WCS 1.0.0 since the request just allow us to specify ResX and ResY
            	//
                final VectorType offsetVector = (VectorType) grid.getOffsetVector().get(0);
                final List offsetValues = offsetVector.getValue();     	
                final double resX=(Double) offsetValues.get(0);
                final double resY=(Double) offsetValues.get(1);
                
                final DirectPositionType origin_ = grid.getOrigin().getPos();
                destinationSize=null;                        
                destinationG2W= new AffineTransform2D(
                	resX, 	0d, 		
                	0d, 	resY,
                	(Double)origin_.getValue().get(0),(Double)origin_.getValue().get(1)
                );
                
                
            }
            else
            	throw new WcsException("Invalid Grid value:"+grid.toString(), InvalidParameterValue,null);
                

            //
            //			ELEVATION SUPPORT VIA A SPECIFIC AXIS ELEVATION
            // 
            
            double[] elevations = null;
            // extract elevation values
            List axisSubset = null;
            if (request.getRangeSubset() != null) {
                axisSubset = request.getRangeSubset().getAxisSubset();
                if (axisSubset.size() > 0) {
                    for (int a=0; a<axisSubset.size(); a++) {
                        AxisSubsetType axis = (AxisSubsetType) axisSubset.get(a);    

                        String axisName = axis.getName();
                        if (axisName.equalsIgnoreCase(WCSUtils.ELEVATION)) {
                            if (axis.getSingleValue().size() > 0) {
                                elevations = new double[axis.getSingleValue().size()];
                                for (int s = 0; s < axis.getSingleValue().size(); s++) {
                                    elevations[s] = Double.parseDouble(((TypedLiteralType) axis.getSingleValue().get(s)).getValue());
                                }
                            } else if (axis.getInterval().size() > 0) {
                                IntervalType interval = (IntervalType) axis.getInterval().get(0);
                                int min = Integer.parseInt(interval.getMin().getValue());
                                int max = Integer.parseInt(interval.getMax().getValue());
                                int res = (interval.getRes() != null ? Integer.parseInt(interval.getRes().getValue()) : 1);

                                elevations = new double[(int) (Math.floor(max - min) / res + 1)];
                                for (int b = 0; b < elevations.length; b++)
                                    elevations[b] = (min + b * res);
                            }
                        }
                    }
                }
            }
//            if(dimension==3&&elevationLevels>0)
//            {
//                // compute the elevation levels, we have elevationLevels values
//                elevations=new double[elevationLevels];
//
//                elevations[0]=requestedEnvelope.getLowerCorner().getOrdinate(2); // TODO put the extrema
//                elevations[elevationLevels-1]=requestedEnvelope.getUpperCorner().getOrdinate(2);
//                if(elevationLevels>2){
//                    final int adjustedLevelsNum=elevationLevels-1;
//                    double step = (elevations[elevationLevels-1]-elevations[0])/adjustedLevelsNum;
//                    for(int i=1;i<adjustedLevelsNum;i++)
//                        elevations[i]=elevations[i-1]+step;
//                }
//            }
            
            
            //
            //			TIME
            //
            final List<Date> timeValues = new LinkedList<Date>();           
            // sequence of timepositions
            if (temporalSubset!=null&&temporalSubset.getTimePosition() != null 
                    &&temporalSubset.getTimePosition().size() > 0) {
            	final EList timePositionLists = temporalSubset.getTimePosition();
                for (Iterator it =timePositionLists.iterator(); it.hasNext(); ) {
                    TimePositionTypeImpl tp = (TimePositionTypeImpl) it.next();
                    timeValues.add((Date) tp.getValue());
                }
            } 
//            else if (temporalSubset!=null&&temporalSubset.getTimePeriod() != null 
//                    &&temporalSubset.getTimePeriod().size() > 0) {
//            	final EList timePeriodLists =temporalSubset.getTimePeriod();
//                for (Iterator it =domainSubset.getTemporalSubset().getTimePeriod().iterator(); it.hasNext(); ) {
//                    TimePeriodType tp = (TimePeriodType) it.next();
//                    Date beginning = (Date)tp.getBeginPosition().getValue();
//                    Date ending = (Date)tp.getEndPosition().getValue();
//                    
//                    timeValues.add(beginning);
//                    timeValues.add(ending);
//                }
//            }        
            
            
            //
            // SETTING COVERAGE READING PARAMS
            //
            // get the group of parameters tha this reader supports
            final ParameterValueGroup readParametersDescriptor = reader.getFormat().getReadParameters();
            GeneralParameterValue[] readParameters = CoverageUtils.getParameters(readParametersDescriptor, meta.getParameters());
            readParameters = (readParameters != null ? readParameters : new GeneralParameterValue[0]);
 
            // read grid geometry
            final GridGeometry2D requestedGridGeometry;
            if(destinationSize!=null)
            	// we have been asked to support a specific raster size, we will se the grid2world accordingly
            	requestedGridGeometry=new GridGeometry2D(
            		new GridEnvelope2D(destinationSize),
            		getHorizontalEnvelope(requestedEnvelope));
            else
            	// we have been asked to support a specific g2w, we will set the raster size accordingly
            	requestedGridGeometry=new GridGeometry2D(
            			PixelInCell.CELL_CENTER,
            			destinationG2W,
                		getHorizontalEnvelope(requestedEnvelope),
                		null);
            // NOTICE that we always have to respect the provided envelope 
            final ParameterValue<GeneralGridGeometry> requestedGridGeometryParam = new DefaultParameterDescriptor<GeneralGridGeometry>(AbstractGridFormat.READ_GRIDGEOMETRY2D.getName().toString(), GeneralGridGeometry.class, null, requestedGridGeometry).createValue();

    

            /*
             * Test if the parameter "TIME" is present in the WMS
             * request, and by the way in the reading parameters. If
             * it is the case, one can adds it to the request. If an
             * exception is thrown, we have nothing to do.
             */
            final List<GeneralParameterDescriptor> parameterDescriptors = readParametersDescriptor.getDescriptor().descriptors();
            ParameterValue time=null;
            boolean hasTime=timeValues.size()>0;
            ParameterValue elevation=null;
            boolean hasElevation=elevations != null && !Double.isNaN(elevations[0]);
            
            if(hasElevation||hasTime){
            	for(GeneralParameterDescriptor pd:parameterDescriptors){

	            	final String code=pd.getName().getCode();
	            	
	            	//
	                // TIME
	            	//
	                if(code.equalsIgnoreCase("TIME")){
	                    time=(ParameterValue) pd.createValue();
	                    time.setValue(timeValues);                }
	                
	                //
	                // ELEVATION
	                //
	                if(code.equalsIgnoreCase(WCSUtils.ELEVATION)){
	                    elevation=(ParameterValue) pd.createValue();
	                    elevation.setValue(elevations[0]);
	                }
	                
	                
	                // leave?
	                if((hasElevation&&elevation!=null&&hasTime&&time!=null)||
	                		!hasElevation&&hasTime&&time!=null||
	                		hasElevation&&elevation!=null&&!hasTime)
	                	break;
	            }
            }
            //
            // add read parameters
            //
            int addedParams=1+(hasTime?1:0)+(hasElevation?1:0);
            // add to the list
            GeneralParameterValue[] readParametersClone = new GeneralParameterValue[readParameters.length+addedParams--];
            System.arraycopy(readParameters, 0,readParametersClone , 0, readParameters.length);
            readParametersClone[readParameters.length+addedParams--]=requestedGridGeometryParam;
            if(hasTime)
            	readParametersClone[readParameters.length+addedParams--]=time;
            if(hasElevation)
            	readParametersClone[readParameters.length+addedParams--]=elevation;            
            readParameters=readParametersClone;
            
            //
            // perform read
            //
            coverage = (GridCoverage2D) reader.read(readParameters);
            if ((coverage == null) || !(coverage instanceof GridCoverage2D)) {
                throw new IOException("The requested coverage could not be found.");
            }

            //
            // Band Select (works on just one field)
            //
            GridCoverage2D bandSelectedCoverage = coverage;
            // ImageIOUtilities.visualize(coverage.getRenderedImage());
            String interpolationType = null;
            if (request.getRangeSubset() != null) {
//                if (request.getRangeSubset().getAxisSubset().size() > 1) {
//                    throw new WcsException("Multi field coverages are not supported yet");
//                }

                interpolationType = request.getInterpolationMethod().getLiteral();

                // extract the band indexes
                axisSubset = request.getRangeSubset().getAxisSubset();
                if (axisSubset.size() > 0) {
                    for (int a=0; a<axisSubset.size(); a++) {
                        AxisSubsetType axis = (AxisSubsetType) axisSubset.get(a);    

                        try {
                            String axisName = axis.getName();
                            if (axisName.equalsIgnoreCase("Band")) {
                                int[] bands = null;
                                if (axis.getSingleValue().size() > 0) {
                                    bands = new int[axis.getSingleValue().size()];
                                    for (int s = 0; s < axis.getSingleValue().size(); s++) {
                                        bands[s] = Integer.parseInt(((TypedLiteralType) axis.getSingleValue().get(s)).getValue()) - 1;
                                    }
                                } else if (axis.getInterval().size() > 0) {
                                    IntervalType interval = (IntervalType) axis.getInterval().get(0);
                                    int min = Integer.parseInt(interval.getMin().getValue());
                                    int max = Integer.parseInt(interval.getMax().getValue());
                                    int res = (interval.getRes() != null ? Integer.parseInt(interval.getRes().getValue()) : 1);

                                    bands = new int[(int) (Math.floor(max - min) / res + 1)];
                                    for (int b = 0; b < bands.length; b++)
                                        bands[b] = (min + b * res) - 1;
                                }

                                // finally execute the band select
                                bandSelectedCoverage = (GridCoverage2D) WCSUtils.bandSelect(coverage,bands);
                            }
                        } catch (Exception e) {
                            // Warning: Axis not found!!!
                            throw new WcsException("Band Select Operation: "
                                    + e.getLocalizedMessage());
                        }
                    }
                }
            }

            //
            // Checking for supported Interpolation Methods
            //
            Interpolation interpolation = Interpolation.getInstance(Interpolation.INTERP_NEAREST);
            if (interpolationType != null) {
                if (interpolationType.equalsIgnoreCase("bilinear")) {
                    interpolation = Interpolation.getInstance(Interpolation.INTERP_BILINEAR);
                } else if (interpolationType.equalsIgnoreCase("bicubic")) {
                    interpolation = Interpolation.getInstance(Interpolation.INTERP_BICUBIC);
                } else if (interpolationType.equalsIgnoreCase("nearest neighbor")) {
                    interpolation = Interpolation.getInstance(Interpolation.INTERP_NEAREST);
                }
            }


            //
            // final step for the requested coverage
            //
            // compute intersection envelope to be used
            final GeneralEnvelope destinationEnvelope = (GeneralEnvelope)getHorizontalEnvelope(computeIntersectionEnvelope(requestedEnvelope, nativeEnvelope));                
            final GridGeometry2D destinationGridGeometry;
            if(destinationSize!=null)
            	destinationGridGeometry=new GridGeometry2D(
            		new GridEnvelope2D(destinationSize),
            		destinationEnvelope);
            else
            	destinationGridGeometry=new GridGeometry2D(
            			PixelInCell.CELL_CENTER,destinationG2W,
                		destinationEnvelope,
                		null); 

            final GridCoverage2D finalCoverage = WCSUtils.resample(bandSelectedCoverage,nativeCRS, targetCRS,destinationGridGeometry, interpolation);
            coverageResults.add(finalCoverage);
        

            return coverageResults.toArray(new GridCoverage2D[] {});
        } catch (Exception e) {
            if (e instanceof WcsException)
                throw (WcsException) e;
            else
                throw new WcsException(e);
        }

    }

    private static Envelope getHorizontalEnvelope(GeneralEnvelope requestedEnvelope) {
        final CoordinateReferenceSystem nativeCRS = CRS.getHorizontalCRS(requestedEnvelope.getCoordinateReferenceSystem());
        
        GeneralEnvelope horizontalRequestedEnvelope = new GeneralEnvelope(nativeCRS);
        horizontalRequestedEnvelope.setEnvelope(requestedEnvelope.getLowerCorner().getOrdinate(0), requestedEnvelope.getLowerCorner()
                .getOrdinate(1), requestedEnvelope.getUpperCorner().getOrdinate(0), requestedEnvelope.getUpperCorner()
                .getOrdinate(1));

        return horizontalRequestedEnvelope;
    }

    private static GeneralEnvelope computeIntersectionEnvelope(
    		final GeneralEnvelope requestedEnvelope, 
    		final GeneralEnvelope nativeEnvelope) {

        GeneralEnvelope retVal;
        // get the crs for the requested bbox
        final CoordinateReferenceSystem requestCRS = CRS.getHorizontalCRS(requestedEnvelope.getCoordinateReferenceSystem());
        final CoordinateReferenceSystem nativeCRS = CRS.getHorizontalCRS(nativeEnvelope.getCoordinateReferenceSystem());

        try {
            //
            // If this approach succeeds, either the request crs is the same of
            // the coverage crs or the request bbox can be reprojected to that
            // crs
            //        	
            MathTransform destinationToSourceTransform=null;
            // STEP 1: reproject requested BBox to native CRS if needed
            if (!CRS.equalsIgnoreMetadata(requestCRS,nativeCRS))
                destinationToSourceTransform = CRS.findMathTransform(requestCRS,nativeCRS, true);
            // now transform the requested envelope to source crs
            if (destinationToSourceTransform != null && !destinationToSourceTransform.isIdentity())
            {
                retVal = CRS.transform(destinationToSourceTransform,getHorizontalEnvelope(requestedEnvelope));
                retVal.setCoordinateReferenceSystem(nativeCRS);

            }
            else
            {
                //we do not need to do anything, but we do this in order to aboid problems with the envelope checks
                retVal= new GeneralEnvelope(getHorizontalEnvelope(requestedEnvelope));

            }            


            //
            // STEP 2: intersect requested BBox in native CRS with coverage native bbox to get the crop bbox
            //
            // intersect the requested area with the bounds of this
            // layer in native crs
            if (!retVal.intersects(nativeEnvelope,true))
                return null;

            // intersect
            retVal.intersect(nativeEnvelope); 
            retVal.setCoordinateReferenceSystem(nativeCRS);
            return retVal;


        } catch (TransformException te) {
            // something bad happened while trying to transform this
            // envelope. let's try with wgs84
            if(LOGGER.isLoggable(Level.FINE))
                LOGGER.log(Level.FINE,te.getLocalizedMessage(),te);
        } catch (FactoryException fe) {
            // something bad happened while trying to transform this
            // envelope. let's try with wgs84
            if(LOGGER.isLoggable(Level.FINE))
                LOGGER.log(Level.FINE,fe.getLocalizedMessage(),fe);
        }


        try {

            //
            // If we can not reproject the requested envelope to the native CRS,
            // we go back to reproject in the geographic crs of the native
            // coverage since this usually happens for conversions between CRS
            // whose area of definition is different
            //              
            final CoordinateReferenceSystem nativeGeoCRS=CRSUtilities.getStandardGeographicCRS2D(nativeCRS);
            final GeneralEnvelope nativeGeoEnvelope= (GeneralEnvelope) CRS.transform(nativeEnvelope,nativeGeoCRS);
            nativeGeoEnvelope.setCoordinateReferenceSystem(nativeGeoCRS);

            GeneralEnvelope requestedBBOXInNativeGeographicCRS=null;
            // STEP 1 reproject the requested envelope to the coverage geographic bbox
            if(!CRS.equalsIgnoreMetadata(nativeCRS, requestCRS)){
                //try to convert the requested bbox to the coverage geocrs
                final MathTransform requestCRSToCoverageGeographicCRS2D = CRS.findMathTransform(requestCRS, nativeGeoCRS,true);
                if(!requestCRSToCoverageGeographicCRS2D.isIdentity())
                {
                    requestedBBOXInNativeGeographicCRS=CRS.transform(requestCRSToCoverageGeographicCRS2D,requestedEnvelope);
                    requestedBBOXInNativeGeographicCRS.setCoordinateReferenceSystem(nativeCRS);
                }
            }
            if(requestedBBOXInNativeGeographicCRS==null)
                requestedBBOXInNativeGeographicCRS= new GeneralEnvelope(requestCRS);


            // STEP 2 intersection with the geographic bbox for this coverage
            if (!requestedBBOXInNativeGeographicCRS.intersects(nativeEnvelope,true))
                return null;

            // intersect with the coverage native geographic bbox
            // note that for the moment we got to use general envelope since there is no intersection otherwise
            requestedBBOXInNativeGeographicCRS.intersect(nativeGeoEnvelope);
            requestedBBOXInNativeGeographicCRS.setCoordinateReferenceSystem(nativeGeoCRS);

            // now go back to the coverage native CRS in order to compute an approximate requested resolution
            final MathTransform transform = CRS.findMathTransform(nativeGeoCRS,requestCRS, true);
            final GeneralEnvelope approximateRequestedBBox = CRS.transform(transform, requestedBBOXInNativeGeographicCRS);
            approximateRequestedBBox.setCoordinateReferenceSystem(requestCRS);
            return approximateRequestedBBox;


        } catch (TransformException te) {
            // something bad happened while trying to transform this
            // envelope. let's try with wgs84
            if(LOGGER.isLoggable(Level.FINE))
                LOGGER.log(Level.FINE,te.getLocalizedMessage(),te);
        } catch (FactoryException fe) {
            // something bad happened while trying to transform this
            // envelope. let's try with wgs84
            if(LOGGER.isLoggable(Level.FINE))
                LOGGER.log(Level.FINE,fe.getLocalizedMessage(),fe);
        }

        LOGGER.log(Level.INFO,"We did not manage to crop the requested envelope, we fall back onto loading the whole coverage.");
        return null;
    }

//	private static void checkDomainSubset(CoverageInfo meta, DomainSubsetType domainSubset)
//            throws Exception {
//      
//		final GeneralEnvelope requestedEnvelope = (GeneralEnvelope) domainSubset.getSpatialSubset().getEnvelope().get(0);
//        final CoordinateReferenceSystem bboxCRs = requestedEnvelope.getCoordinateReferenceSystem();
//
//        Envelope gridEnvelope = /* meta.getCoverage().getEnvelope() */null;
//        GeneralEnvelope requestedEnvelopeBboxCRS = null;
//        if (bboxCRs instanceof GeographicCRS) {
//            try {
//                final CoordinateOperationFactory cof = CRS.getCoordinateOperationFactory(true);
//                final CoordinateOperation operation = cof.createOperation(gridEnvelope.getCoordinateReferenceSystem(), bboxCRs);
//                requestedEnvelopeBboxCRS = CRS.transform(operation, gridEnvelope);
//            } catch (Exception e) {
//                // this may happen, there is nothing we can do about it, we just
//                // use the back transformed envelope to be more lenient about
//                // which coordinate coorections to make on the longitude axis
//                // should the antimeridian style be used
//            }
//        }
//
//        // check the coordinates, but make sure the case 175,-175 is handled
//        // as valid for the longitude axis in a geographic coordinate system
//        // see section 7.6.2 of the WCS 1.1.1 spec)
//        double[] lower = requestedEnvelope.getLowerCorner().getCoordinate();
//        double[] upper = requestedEnvelope.getUpperCorner().getCoordinate();
//        for (int i = 0; i < lower.length; i++) {
//            if (lower[i] > upper[i]) {
//                final CoordinateSystemAxis axis = bboxCRs.getCoordinateSystem().getAxis(i);
//                // see if the coordinates can be fixed
//                if (bboxCRs instanceof GeographicCRS && axis.getDirection() == AxisDirection.EAST) {
//
//                    if (requestedEnvelopeBboxCRS != null) {
//                        // try to guess which one needs to be fixed
//                        final double envMax = requestedEnvelopeBboxCRS.getMaximum(i);
//                        if (envMax >= lower[i])
//                            upper[i] = upper[i] + (axis.getMaximumValue() - axis.getMinimumValue());
//                        else
//                            lower[i] = lower[i] - (axis.getMaximumValue() - axis.getMinimumValue());
//
//                    } else {
//                        // just fix the upper and hope...
//                        upper[i] = upper[i] + (axis.getMaximumValue() - axis.getMinimumValue());
//                    }
//                }
//
//                // if even after the fix we're in the wrong situation, complain
//                if (lower[i] > upper[i]) {
//                    throw new WcsException(
//                            "illegal bbox, min of dimension " + (i + 1) + ": " + lower[i] + " is " + "greater than max of same dimension: " + upper[i],
//                            WcsExceptionCode.InvalidParameterValue, "BoundingBox");
//                }
//            }
//
//        }
//    }

    private static void checkInterpolationMethod(CoverageInfo info,
            InterpolationMethodType interpolationMethod) {
        // check interpolation method
        String interpolation = interpolationMethod.getLiteral();
        if (interpolation != null) {
            boolean interpolationSupported = false;

            if (interpolation.startsWith("nearest")) {
                interpolation = "nearest neighbor";
            }
            if(interpolation.equals("nearest neighbor") || 
                    (info.getDefaultInterpolationMethod() != null && info.getDefaultInterpolationMethod().equalsIgnoreCase(interpolation))){
            	interpolationSupported=true;
            }
            for (String method : info.getInterpolationMethods()) {
                if (interpolation.equalsIgnoreCase(method)) {
                    interpolationSupported = true;
                }
            }

            if (!interpolationSupported)
                throw new WcsException(
                        "The requested Interpolation method is not supported by this Coverage.",
                        InvalidParameterValue, "RangeSubset");
        }
    }

    /**
     * Checks that the elements of the Output part of the request do make sense by comparing them to
     * the coverage metadata
     * 
     * @param info
     * @param rangeSubset
     */
    private static void checkOutput(CoverageInfo meta, OutputType output) {
        if (output == null)
            return;

        // check output format
        String format = output.getFormat().getValue();
        String declaredFormat = getDeclaredFormat(meta.getSupportedFormats(), format);
        if (declaredFormat == null)
            throw new WcsException("format " + format + " is not supported for this coverage",InvalidParameterValue, "format");

        // check requested CRS
        // if (output.getCrs() != null) {
        // String requestedCRS = output.getCrs().getValue();
        // if (getRequestResponseCRS(meta.getRequestCRSs(), requestedCRS) == null &&
        // getRequestResponseCRS(meta.getResponseCRSs(), requestedCRS) == null)
        // throw new WcsException("CRS " + requestedCRS + " is not supported for this coverage",
        // InvalidParameterValue, "CRS");
        // } else {
        // // The requested CRS was not specified ... what to do ???
        // }
    }

    /**
     * Checks if the supported format string list contains the specified format, doing a case
     * insensitive search. If found the declared output format name is returned, otherwise null is
     * returned.
     * 
     * @param supportedFormats
     * @param format
     * @return
     */
    private static String getDeclaredFormat(List<String> supportedFormats, String format) {
        // supported formats may be setup using old style formats, first scan
        // the configured list
        for (String sf:supportedFormats) {
            if (sf.equalsIgnoreCase(format.trim())) {
                return sf;
            } else {
                CoverageResponseDelegate delegate = CoverageResponseDelegateFactory.encoderFor(sf);
                if (delegate != null && delegate.canProduce(format))
                    return sf;
            }
        }
        return null;
    }

    /**
     * Checks that the elements of the RangeSubset part of the request do make sense by comparing
     * them to the coverage metadata
     * 
     * @param info
     * @param rangeSubset
     */
    private static void checkRangeSubset(CoverageInfo info, RangeSubsetType rangeSubset) {
        // quick escape if no range subset has been specified (it's legal)
        if (rangeSubset == null)
            return;

        // check axis
        if (rangeSubset.getAxisSubset().size() > 1) {
            throw new WcsException("Multi axis coverages are not supported yet",
                    InvalidParameterValue, "RangeSubset");
        } else if (rangeSubset.getAxisSubset().size() == 0)
            return;

        for (int a=0; a<rangeSubset.getAxisSubset().size(); a++) {
            AxisSubsetType axisSubset = (AxisSubsetType) rangeSubset.getAxisSubset().get(a);
            
            if (axisSubset.getName().equalsIgnoreCase("Band")) {
                // prepare a support structure to quickly get the band index of a key
                // (and remember we replaced spaces with underscores in the keys to
                // avoid issues with the kvp parsing of indentifiers that include spaces)

                // check indexes
                int[] bands = null;
                if (axisSubset.getSingleValue().size() > 0) {
                    bands = new int[1];
                    bands[0] = Integer.parseInt(((TypedLiteralType) axisSubset.getSingleValue().get(0)).getValue());
                } else if (axisSubset.getInterval().size() > 0) {
                    IntervalType interval = (IntervalType) axisSubset.getInterval().get(0);
                    int min = Integer.parseInt(interval.getMin().getValue());
                    int max = Integer.parseInt(interval.getMax().getValue());
                    int res = (interval.getRes() != null ? Integer.parseInt(interval.getRes().getValue()) : 1);

                    bands = new int[(max - min) / res];
                    for (int b = 0; b < bands.length; b++)
                        bands[b] = min + (b * res);
                }

                if (bands == null)
                    throw new WcsException("Invalid values for axis " + axisSubset.getName(),
                            InvalidParameterValue, "AxisSubset");
            } else if (axisSubset.getName().equalsIgnoreCase(WCSUtils.ELEVATION)) {
                double[] elevations = null;
                if (axisSubset.getSingleValue().size() > 0) {
                    elevations = new double[axisSubset.getSingleValue().size()];
                    for (int s = 0; s < axisSubset.getSingleValue().size(); s++) {
                        elevations[s] = Double.parseDouble(((TypedLiteralType) axisSubset.getSingleValue().get(s)).getValue());
                    }
                } else if (axisSubset.getInterval().size() > 0) {
                    IntervalType interval = (IntervalType) axisSubset.getInterval().get(0);
                    int min = Integer.parseInt(interval.getMin().getValue());
                    int max = Integer.parseInt(interval.getMax().getValue());
                    int res = (interval.getRes() != null ? Integer.parseInt(interval.getRes().getValue()) : 1);

                    elevations = new double[(int) (Math.floor(max - min) / res + 1)];
                    for (int b = 0; b < elevations.length; b++)
                        elevations[b] = (min + b * res);
                }
            }
        }
    }

}