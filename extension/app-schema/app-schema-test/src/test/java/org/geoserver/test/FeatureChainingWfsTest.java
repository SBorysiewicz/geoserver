/*
 * Copyright (c) 2001 - 2009 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */

package org.geoserver.test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import junit.framework.Test;
import org.geotools.data.complex.AppSchemaDataAccess;
import org.geotools.wfs.v1_1.WFS;
import org.w3c.dom.Document;

/**
 * WFS GetFeature to test integration of {@link AppSchemaDataAccess} with GeoServer.
 * 
 * @author Ben Caradoc-Davies, CSIRO Exploration and Mining
 * @author Rini Angreani, Curtin University of Technology
 */
public class FeatureChainingWfsTest extends AbstractAppSchemaWfsTestSupport {
    final String BASE_URL = "http://localhost:80/geoserver/";

    final String DESCRIBE_FEATURE_TYPE_BASE = BASE_URL
            + "wfs?request=DescribeFeatureType&version=1.1.0&service=WFS&typeName=";

    final String DEFAULT_WFS_SCHEMA_URI = WFS.NAMESPACE
            + " http://localhost:80/geoserver/schemas/wfs/1.1.0/wfs.xsd";

    /**
     * Read-only test so can use one-time setup.
     * 
     * @return
     */
    public static Test suite() {
        return new OneTimeTestSetup(new FeatureChainingWfsTest());
    }

    @Override
    protected NamespaceTestData buildTestData() {
        return new FeatureChainingMockData();
    }

    /**
     * Test whether GetCapabilities returns wfs:WFS_Capabilities.
     */
    public void testGetCapabilities() {
        Document doc = getAsDOM("wfs?request=GetCapabilities");
        LOGGER.info("WFS GetCapabilities response:\n" + prettyString(doc));
        assertEquals("wfs:WFS_Capabilities", doc.getDocumentElement().getNodeName());

        // make sure non-feature types don't appear in FeatureTypeList
        assertXpathCount(5, "//wfs:FeatureType", doc);
        ArrayList<String> featureTypeNames = new ArrayList<String>(4);
        featureTypeNames.add(evaluate("//wfs:FeatureType[1]/wfs:Name", doc));
        featureTypeNames.add(evaluate("//wfs:FeatureType[2]/wfs:Name", doc));
        featureTypeNames.add(evaluate("//wfs:FeatureType[3]/wfs:Name", doc));
        featureTypeNames.add(evaluate("//wfs:FeatureType[4]/wfs:Name", doc));
        featureTypeNames.add(evaluate("//wfs:FeatureType[5]/wfs:Name", doc));
        // Mapped Feture
        assertTrue(featureTypeNames.contains("gsml:MappedFeature"));
        // Geologic Unit
        assertTrue(featureTypeNames.contains("gsml:GeologicUnit"));
        // FirstParentFeature
        assertTrue(featureTypeNames.contains("ex:FirstParentFeature"));
        // SecondParentFeature
        assertTrue(featureTypeNames.contains("ex:SecondParentFeature"));
        // om:Observation
        assertTrue(featureTypeNames.contains("om:Observation"));
    }

    /**
     * Test whether DescribeFeatureType returns xsd:schema, and if the contents are correct. When no
     * type name specified, it should return imports for all name spaces involved. If type name is
     * specified, it should return imports of GML type and the type's top level schema.
     * 
     * @throws IOException
     */
    public void testDescribeFeatureType() throws IOException {
        File dataDir = this.getTestData().getDataDirectoryRoot();

        /**
         * gsml:MappedFeature
         */
        Document doc = getAsDOM("wfs?request=DescribeFeatureType&typename=gsml:MappedFeature");
        LOGGER.info("WFS DescribeFeatureType, typename=gsml:MappedFeature response:\n"
                + prettyString(doc));
        assertEquals("xsd:schema", doc.getDocumentElement().getNodeName());
        // check target name space is encoded and is correct
        assertXpathEvaluatesTo(AbstractAppSchemaMockData.GSML_URI, "//@targetNamespace", doc);
        // make sure the content is only relevant include
        assertXpathCount(1, "//xsd:include", doc);
        // no import to GML since it's already imported inside the included schema
        // otherwise it's invalid to import twice
        assertXpathCount(0, "//xsd:import", doc);
        // GSML schemaLocation
        assertXpathEvaluatesTo(AbstractAppSchemaMockData.GSML_SCHEMA_LOCATION_URL,
                "//xsd:include/@schemaLocation", doc);
        // nothing else
        assertXpathCount(0, "//xsd:complexType", doc);
        assertXpathCount(0, "//xsd:element", doc);

        /**
         * gsml:GeologicUnit
         */
        doc = getAsDOM("wfs?request=DescribeFeatureType&typename=gsml:GeologicUnit");
        LOGGER.info("WFS DescribeFeatureType, typename=gsml:GeologicUnit response:\n"
                + prettyString(doc));
        assertEquals("xsd:schema", doc.getDocumentElement().getNodeName());
        assertXpathEvaluatesTo(AbstractAppSchemaMockData.GSML_URI, "//@targetNamespace", doc);
        assertXpathCount(1, "//xsd:include", doc);
        assertXpathCount(0, "//xsd:import", doc);
        // GSML schemaLocation
        assertXpathEvaluatesTo(AbstractAppSchemaMockData.GSML_SCHEMA_LOCATION_URL,
                "//xsd:include/@schemaLocation", doc);
        // nothing else
        assertXpathCount(0, "//xsd:complexType", doc);
        assertXpathCount(0, "//xsd:element", doc);

        /**
         * ex:FirstParentFeature and ex:SecondParentFeature
         */
        doc = getAsDOM("wfs?request=DescribeFeatureType&typeName=ex:FirstParentFeature,ex:SecondParentFeature");
        LOGGER.info("WFS DescribeFeatureType, typename=ex:FirstParentFeature,"
                + "ex:SecondParentFeature response:\n" + prettyString(doc));
        assertXpathEvaluatesTo(FeatureChainingMockData.EX_URI, "//@targetNamespace", doc);
        assertXpathCount(1, "//xsd:include", doc);
        assertXpathCount(0, "//xsd:import", doc);
        // EX include
        File exSchema = findFile("featureTypes/ex_FirstParentFeature/simpleContent.xsd", dataDir);
        assertNotNull(exSchema);
        assertTrue(exSchema.exists());
        String exSchemaLocation = exSchema.toURI().toString();
        assertXpathEvaluatesTo(exSchemaLocation, "//xsd:include/@schemaLocation", doc);
        // nothing else
        assertXpathCount(0, "//xsd:complexType", doc);
        assertXpathCount(0, "//xsd:element", doc);

        /**
         * Mixed name spaces
         */
        doc = getAsDOM("wfs?request=DescribeFeatureType&typeName=gsml:MappedFeature,ex:FirstParentFeature");
        LOGGER
                .info("WFS DescribeFeatureType, typename=gsml:MappedFeature,ex:FirstParentFeature response:\n"
                        + prettyString(doc));
        testDescribeFeatureTypeImports(doc, exSchemaLocation);

        /**
         * All type names specified, should result the same as above
         */
        doc = getAsDOM("wfs?request=DescribeFeatureType&typeName=gsml:MappedFeature,gsml:GeologicUnit,ex:FirstParentFeature,ex:SecondParentFeature");
        LOGGER
                .info("WFS DescribeFeatureType, typename=gsml:MappedFeature,gsml:GeologicUnit,ex:FirstParentFeature,ex:SecondParentFeature response:\n"
                        + prettyString(doc));
        testDescribeFeatureTypeImports(doc, exSchemaLocation);

        /**
         * No type name specified, should result the same as all type names
         */
        doc = getAsDOM("wfs?request=DescribeFeatureType");
        LOGGER.info("WFS DescribeFeatureType response:\n" + prettyString(doc));
        // FIXME: disabled as workaround for GEOS-3722
        // testDescribeFeatureTypeImports(doc, exSchemaLocation);
    }

    private void testDescribeFeatureTypeImports(Document doc, String exSchemaLocation) {
        assertEquals("xsd:schema", doc.getDocumentElement().getNodeName());
        assertXpathCount(0, "//@targetNamespace", doc);
        assertXpathCount(2, "//xsd:import", doc);
        assertXpathCount(0, "//xsd:include", doc);

        // order is unimportant, and could change, so we don't test the order
        String firstNamespace = evaluate("//xsd:import[1]/@namespace", doc);
        if (firstNamespace.equals(AbstractAppSchemaMockData.GSML_URI)) {
            // GSML import
            assertXpathEvaluatesTo(AbstractAppSchemaMockData.GSML_SCHEMA_LOCATION_URL,
                    "//xsd:import[1]/@schemaLocation", doc);
            // EX import
            assertXpathEvaluatesTo(FeatureChainingMockData.EX_URI, "//xsd:import[2]/@namespace",
                    doc);
            assertXpathEvaluatesTo(exSchemaLocation, "//xsd:import[2]/@schemaLocation", doc);
        } else {
            // EX import
            assertXpathEvaluatesTo(FeatureChainingMockData.EX_URI, "//xsd:import[1]/@namespace",
                    doc);
            assertXpathEvaluatesTo(exSchemaLocation, "//xsd:import[1]/@schemaLocation", doc);
            // GSML import
            assertXpathEvaluatesTo(AbstractAppSchemaMockData.GSML_URI,
                    "//xsd:import[2]/@namespace", doc);
            assertXpathEvaluatesTo(AbstractAppSchemaMockData.GSML_SCHEMA_LOCATION_URL,
                    "//xsd:import[2]/@schemaLocation", doc);
        }
        // nothing else
        assertXpathCount(0, "//xsd:complexType", doc);
        assertXpathCount(0, "//xsd:element", doc);
    }

    /**
     * Test whether GetFeature returns wfs:FeatureCollection.
     */
    public void testGetFeature() {
        Document doc = getAsDOM("wfs?request=GetFeature&typename=gsml:MappedFeature");
        LOGGER.info("WFS GetFeature&typename=gsml:MappedFeature response:\n" + prettyString(doc));
        assertEquals("wfs:FeatureCollection", doc.getDocumentElement().getNodeName());
        // non-feature type should return nothing/exception
        doc = getAsDOM("wfs?request=GetFeature&typename=gsml:CompositionPart");
        LOGGER.info("WFS GetFeature&typename=gsml:CompositionPart response, exception expected:\n"
                + prettyString(doc));
        assertEquals("ows:ExceptionReport", doc.getDocumentElement().getNodeName());
    }

    /**
     * Test nesting features of complex types with simple content. Previously the nested features
     * attributes weren't encoded, so this is to ensure that this works. This also tests that a
     * feature type can have multiple FEATURE_LINK to be referred by different types.
     */
    public void testComplexTypeWithSimpleContent() {
        Document doc = getAsDOM("wfs?request=GetFeature&typename=ex:FirstParentFeature");
        LOGGER
                .info("WFS GetFeature&typename=ex:FirstParentFeature response:\n"
                        + prettyString(doc));
        assertXpathCount(2, "//ex:FirstParentFeature", doc);

        // 1
        assertXpathCount(2, "//ex:FirstParentFeature[@gml:id='1']/ex:nestedFeature", doc);
        assertXpathEvaluatesTo(
                "string_one",
                "//ex:FirstParentFeature[@gml:id='1']/ex:nestedFeature[1]/ex:SimpleContent/ex:someAttribute",
                doc);
        assertXpathEvaluatesTo(
                "string_two",
                "//ex:FirstParentFeature[@gml:id='1']/ex:nestedFeature[2]/ex:SimpleContent/ex:someAttribute",
                doc);
        assertXpathCount(
                0,
                "//ex:FirstParentFeature[@gml:id='1']/ex:nestedFeature[2]/ex:SimpleContent/FEATURE_LINK",
                doc);
        // 2
        assertXpathCount(0, "//ex:FirstParentFeature[@gml:id='2']/ex:nestedFeature", doc);

        doc = getAsDOM("wfs?request=GetFeature&typename=ex:SecondParentFeature");
        LOGGER.info("WFS GetFeature&typename=ex:SecondParentFeature response:\n"
                + prettyString(doc));
        assertXpathCount(2, "//ex:SecondParentFeature", doc);

        // 1
        assertXpathCount(0, "//ex:SecondParentFeature[@gml:id='1']/ex:nestedFeature", doc);
        // 2
        assertXpathCount(3, "//ex:SecondParentFeature[@gml:id='2']/ex:nestedFeature", doc);
        assertXpathEvaluatesTo(
                "string_one",
                "//ex:SecondParentFeature[@gml:id='2']/ex:nestedFeature[1]/ex:SimpleContent/ex:someAttribute",
                doc);
        assertXpathEvaluatesTo(
                "string_two",
                "//ex:SecondParentFeature[@gml:id='2']/ex:nestedFeature[2]/ex:SimpleContent/ex:someAttribute",
                doc);
        assertXpathEvaluatesTo(
                "string_three",
                "//ex:SecondParentFeature[@gml:id='2']/ex:nestedFeature[3]/ex:SimpleContent/ex:someAttribute",
                doc);
    }

    /**
     * Test content of GetFeature response.
     */
    public void testGetFeatureContent() throws Exception {
        Document doc = getAsDOM("wfs?request=GetFeature&typename=gsml:MappedFeature");
        LOGGER.info("WFS GetFeature&typename=gsml:MappedFeature response:\n" + prettyString(doc));
        assertXpathEvaluatesTo("4", "/wfs:FeatureCollection/@numberOfFeatures", doc);
        assertXpathCount(4, "//gsml:MappedFeature", doc);

        String schemaLocation = evaluate("/wfs:FeatureCollection/@xsi:schemaLocation", doc);
        String gsmlLocation = AbstractAppSchemaMockData.GSML_URI + " "
                + AbstractAppSchemaMockData.GSML_SCHEMA_LOCATION_URL;
        if (schemaLocation.startsWith(AbstractAppSchemaMockData.GSML_URI)) {
            // GSML schema location was encoded first
            assertEquals(schemaLocation, gsmlLocation + " " + DEFAULT_WFS_SCHEMA_URI);
        } else {
            // WFS schema location was encoded first
            assertEquals(schemaLocation, DEFAULT_WFS_SCHEMA_URI + " " + gsmlLocation);
        }

        // mf1
        {
            String id = "mf1";
            assertXpathEvaluatesTo(id, "//gsml:MappedFeature[1]/@gml:id", doc);
            assertXpathEvaluatesTo("GUNTHORPE FORMATION", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gml:name", doc);
            assertXpathEvaluatesTo("-1.2 52.5 -1.2 52.6 -1.1 52.6 -1.1 52.5 -1.2 52.5",
                    "//gsml:MappedFeature[@gml:id='" + id + "']/gsml:shape//gml:posList", doc);
            // specification gu.25699
            assertXpathEvaluatesTo("gu.25699", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/@gml:id", doc);
            // description
            assertXpathEvaluatesTo("Olivine basalt, tuff, microgabbro, minor sedimentary rocks",
                    "//gsml:MappedFeature[@gml:id='" + id + "']/gsml:specification"
                            + "/gsml:GeologicUnit/gml:description", doc);
            // name
            assertXpathCount(2, "//gsml:MappedFeature[@gml:id='" + id + "']/gsml:specification"
                    + "/gsml:GeologicUnit/gml:name", doc);
            assertXpathEvaluatesTo("Yaugher Volcanic Group", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification"
                    + "/gsml:GeologicUnit/gml:name[@codeSpace='urn:ietf:rfc:2141']", doc);
            assertXpathEvaluatesTo("Yaugher Volcanic Group", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gml:name[1]", doc);
            assertXpathEvaluatesTo("urn:ietf:rfc:2141", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gml:name[1]/@codeSpace", doc);
            assertXpathEvaluatesTo("-Py", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gml:name[2]", doc);
            // feature link shouldn't appear as it's not in the schema
            assertXpathCount(0, "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/FEATURE_LINK", doc);
            // occurence [sic]
            assertXpathCount(1, "//gsml:MappedFeature[@gml:id='" + id + "']/gsml:specification"
                    + "/gsml:GeologicUnit/gsml:occurence", doc);
            assertXpathEvaluatesTo("", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification" + "/gsml:GeologicUnit/gsml:occurence[1]", doc);
            assertXpathEvaluatesTo("urn:cgi:feature:MappedFeature:mf1",
                    "//gsml:MappedFeature[@gml:id='" + id + "']/gsml:specification"
                            + "/gsml:GeologicUnit/gsml:occurence/@xlink:href", doc);
            // exposureColor
            assertXpathCount(1, "//gsml:MappedFeature[@gml:id='" + id + "']/gsml:specification"
                    + "/gsml:GeologicUnit/gsml:exposureColor", doc);
            assertXpathEvaluatesTo("Blue", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gsml:exposureColor"
                    + "/gsml:CGI_TermValue/gsml:value", doc);
            assertXpathEvaluatesTo("some:uri", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gsml:exposureColor/gsml:CGI_TermValue/gsml:value/@codeSpace", doc);
            // feature link shouldn't appear as it's not in the schema
            assertXpathCount(0, "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gsml:exposureColor"
                    + "/gsml:CGI_TermValue/FEATURE_LINK", doc);
            // outcropCharacter
            assertXpathCount(1, "//gsml:MappedFeature[@gml:id='" + id + "']/gsml:specification"
                    + "/gsml:GeologicUnit/gsml:outcropCharacter", doc);
            assertXpathEvaluatesTo("x", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gsml:outcropCharacter"
                    + "/gsml:CGI_TermValue/gsml:value", doc);
            // feature link shouldn't appear as it's not in the schema
            assertXpathCount(0, "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gsml:outcropCharacter"
                    + "/gsml:CGI_TermValue/FEATURE_LINK", doc);
            // composition
            assertXpathCount(1, "//gsml:MappedFeature[@gml:id='" + id + "']/gsml:specification"
                    + "/gsml:GeologicUnit/gsml:composition", doc);
            assertXpathEvaluatesTo("significant", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gsml:composition"
                    + "/gsml:CompositionPart/gsml:proportion/gsml:CGI_TermValue/gsml:value", doc);
            assertXpathEvaluatesTo("interbedded component", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gsml:composition"
                    + "/gsml:CompositionPart/gsml:role", doc);
            // feature link shouldn't appear as it's not in the schema
            assertXpathCount(0, "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gsml:composition"
                    + "/gsml:CompositionPart/gsml:role/FEATURE_LINK", doc);
            // lithology
            assertXpathCount(0, "//gsml:MappedFeature[@gml:id='" + id + "']/gsml:specification"
                    + "/gsml:GeologicUnit/gsml:composition/gsml:CompositionPart/gsml:lithology",
                    doc);
            // feature link shouldn't appear as it's not in the schema
            assertXpathCount(0, "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gsml:composition"
                    + "/gsml:CompositionPart/gsml:lithology/FEATURE_LINK", doc);
        }

        // mf2
        {
            String id = "mf2";
            assertXpathEvaluatesTo(id, "//gsml:MappedFeature[2]/@gml:id", doc);
            assertXpathEvaluatesTo("MERCIA MUDSTONE GROUP", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gml:name", doc);
            assertXpathEvaluatesTo("-1.3 52.5 -1.3 52.6 -1.2 52.6 -1.2 52.5 -1.3 52.5",
                    "//gsml:MappedFeature[@gml:id='" + id + "']/gsml:shape//gml:posList", doc);
            // gu.25678
            assertXpathEvaluatesTo("gu.25678", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/@gml:id", doc);
            // name
            assertXpathCount(2, "//gsml:MappedFeature[@gml:id='" + id + "']/gsml:specification"
                    + "/gsml:GeologicUnit/gml:name", doc);
            assertXpathEvaluatesTo("Yaugher Volcanic Group", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification"
                    + "/gsml:GeologicUnit/gml:name[@codeSpace='urn:ietf:rfc:2141']", doc);
            assertXpathEvaluatesTo("Yaugher Volcanic Group", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gml:name[1]", doc);
            assertXpathEvaluatesTo("urn:ietf:rfc:2141", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gml:name[1]/@codeSpace", doc);
            assertXpathEvaluatesTo("-Py", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gml:name[2]", doc);
            assertXpathCount(0, "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/FEATURE_LINK", doc);
            // occurence [sic]
            assertXpathCount(2, "//gsml:MappedFeature[@gml:id='" + id + "']/gsml:specification"
                    + "/gsml:GeologicUnit/gsml:occurence", doc);
            assertXpathEvaluatesTo("", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification" + "/gsml:GeologicUnit/gsml:occurence[1]", doc);
            assertXpathEvaluatesTo("urn:cgi:feature:MappedFeature:mf2",
                    "//gsml:MappedFeature[@gml:id='" + id + "']/gsml:specification"
                            + "/gsml:GeologicUnit/gsml:occurence[1]/@xlink:href", doc);
            assertXpathEvaluatesTo("", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification" + "/gsml:GeologicUnit/gsml:occurence[2]", doc);
            assertXpathEvaluatesTo("urn:cgi:feature:MappedFeature:mf3",
                    "//gsml:MappedFeature[@gml:id='" + id + "']/gsml:specification"
                            + "/gsml:GeologicUnit/gsml:occurence[2]/@xlink:href", doc);
            // description
            assertXpathEvaluatesTo("Olivine basalt, tuff, microgabbro, minor sedimentary rocks",
                    "//gsml:MappedFeature[@gml:id='" + id + "']/gsml:specification"
                            + "/gsml:GeologicUnit/gml:description", doc);
            // exposureColor
            assertXpathCount(2, "//gsml:MappedFeature[@gml:id='" + id + "']/gsml:specification"
                    + "/gsml:GeologicUnit/gsml:exposureColor", doc);
            assertXpathEvaluatesTo("Yellow", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gsml:exposureColor[1]"
                    + "/gsml:CGI_TermValue/gsml:value", doc);
            assertXpathEvaluatesTo("some:uri", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gsml:exposureColor[1]/gsml:CGI_TermValue/gsml:value/@codeSpace", doc);
            assertXpathEvaluatesTo("Blue", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gsml:exposureColor[2]"
                    + "/gsml:CGI_TermValue/gsml:value", doc);
            assertXpathEvaluatesTo("some:uri", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gsml:exposureColor[2]/gsml:CGI_TermValue/gsml:value/@codeSpace", doc);
            assertXpathCount(0, "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gsml:exposureColor"
                    + "/gsml:CGI_TermValue/FEATURE_LINK", doc);
            // outcropCharacter
            assertXpathCount(2, "//gsml:MappedFeature[@gml:id='" + id + "']/gsml:specification"
                    + "/gsml:GeologicUnit/gsml:outcropCharacter", doc);
            assertXpathEvaluatesTo("y", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gsml:outcropCharacter[1]"
                    + "/gsml:CGI_TermValue/gsml:value", doc);
            assertXpathEvaluatesTo("x", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gsml:outcropCharacter[2]"
                    + "/gsml:CGI_TermValue/gsml:value", doc);
            assertXpathCount(0, "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gsml:outcropCharacter"
                    + "/gsml:CGI_TermValue/FEATURE_LINK", doc);
            // composition
            assertXpathCount(2, "//gsml:MappedFeature[@gml:id='" + id + "']/gsml:specification"
                    + "/gsml:GeologicUnit/gsml:composition", doc);
            assertXpathEvaluatesTo("significant", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gsml:composition[1]"
                    + "/gsml:CompositionPart/gsml:proportion/gsml:CGI_TermValue/gsml:value", doc);
            assertXpathEvaluatesTo("interbedded component", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification"
                    + "/gsml:GeologicUnit[@gml:id='gu.25678']/gsml:composition[1]"
                    + "/gsml:CompositionPart/gsml:role", doc);
            assertXpathCount(0, "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gsml:composition[1]"
                    + "/gsml:CompositionPart/gsml:role/FEATURE_LINK", doc);
            assertXpathEvaluatesTo("minor", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gsml:composition[2]"
                    + "/gsml:CompositionPart/gsml:proportion/gsml:CGI_TermValue/gsml:value", doc);
            assertXpathEvaluatesTo("interbedded component", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gsml:composition[2]"
                    + "/gsml:CompositionPart/gsml:role", doc);
            assertXpathCount(0, "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gsml:composition[2]"
                    + "/gsml:CompositionPart/gsml:role/FEATURE_LINK", doc);

            // lithology
            assertXpathCount(0, "//gsml:MappedFeature[@gml:id='" + id + "']/gsml:specification"
                    + "/gsml:GeologicUnit/gsml:composition/gsml:CompositionPart/gsml:lithology",
                    doc);
            assertXpathCount(0, "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gsml:composition"
                    + "/gsml:CompositionPart/gsml:lithology/FEATURE_LINK", doc);
        }

        // mf3
        {
            String id = "mf3";
            assertXpathEvaluatesTo(id, "//gsml:MappedFeature[3]/@gml:id", doc);
            assertXpathEvaluatesTo("CLIFTON FORMATION", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gml:name", doc);
            assertXpathEvaluatesTo("-1.2 52.5 -1.2 52.6 -1.1 52.6 -1.1 52.5 -1.2 52.5",
                    "//gsml:MappedFeature[@gml:id='" + id + "']/gsml:shape//gml:posList", doc);
            // gu.25678
            assertXpathEvaluatesTo("#gu.25678", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/@xlink:href", doc);
            // make sure nothing else is encoded
            assertXpathCount(0, "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gml:name", doc);
        }

        // mf4
        {
            String id = "mf4";
            assertXpathEvaluatesTo(id, "//gsml:MappedFeature[4]/@gml:id", doc);
            assertXpathEvaluatesTo("MURRADUC BASALT", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gml:name", doc);
            assertXpathEvaluatesTo("-1.3 52.5 -1.3 52.6 -1.2 52.6 -1.2 52.5 -1.3 52.5",
                    "//gsml:MappedFeature[@gml:id='" + id + "']/gsml:shape//gml:posList", doc);
            // gu.25682
            assertXpathEvaluatesTo("gu.25682", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/@gml:id", doc);
            // description
            assertXpathEvaluatesTo("Olivine basalt", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gml:description", doc);
            // name
            assertXpathCount(2, "//gsml:MappedFeature[@gml:id='" + id + "']/gsml:specification"
                    + "/gsml:GeologicUnit/gml:name", doc);
            assertXpathEvaluatesTo("New Group", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification"
                    + "/gsml:GeologicUnit/gml:name[@codeSpace='urn:ietf:rfc:2141']", doc);
            assertXpathEvaluatesTo("New Group", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gml:name[1]", doc);
            assertXpathEvaluatesTo("urn:ietf:rfc:2141", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gml:name[1]/@codeSpace", doc);
            assertXpathEvaluatesTo("-Xy", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gml:name[2]", doc);
            assertXpathCount(0, "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/FEATURE_LINK", doc);
            // occurence [sic]
            assertXpathCount(1, "//gsml:MappedFeature[@gml:id='" + id + "']/gsml:specification"
                    + "/gsml:GeologicUnit/gsml:occurence", doc);
            assertXpathEvaluatesTo("", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification" + "/gsml:GeologicUnit/gsml:occurence[1]", doc);
            assertXpathEvaluatesTo("urn:cgi:feature:MappedFeature:mf4",
                    "//gsml:MappedFeature[@gml:id='" + id + "']/gsml:specification"
                            + "/gsml:GeologicUnit/gsml:occurence/@xlink:href", doc);
            // exposureColor
            assertXpathCount(1, "//gsml:MappedFeature[@gml:id='" + id + "']/gsml:specification"
                    + "/gsml:GeologicUnit/gsml:exposureColor", doc);
            assertXpathEvaluatesTo("some:uri", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gsml:exposureColor/gsml:CGI_TermValue/gsml:value/@codeSpace", doc);
            assertXpathEvaluatesTo("Red", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gsml:exposureColor"
                    + "/gsml:CGI_TermValue/gsml:value", doc);
            assertXpathCount(0, "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gsml:exposureColor"
                    + "/gsml:CGI_TermValue/FEATURE_LINK", doc);
            // outcropCharacter
            assertXpathCount(1, "//gsml:MappedFeature[@gml:id='" + id + "']/gsml:specification"
                    + "/gsml:GeologicUnit/gsml:outcropCharacter", doc);
            assertXpathEvaluatesTo("z", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gsml:outcropCharacter"
                    + "/gsml:CGI_TermValue/gsml:value", doc);
            assertXpathCount(0, "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gsml:outcropCharacter"
                    + "/gsml:CGI_TermValue/FEATURE_LINK", doc);
            // composition
            assertXpathCount(1, "//gsml:MappedFeature[@gml:id='" + id + "']/gsml:specification"
                    + "/gsml:GeologicUnit/gsml:composition", doc);
            assertXpathEvaluatesTo("significant", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gsml:composition"
                    + "/gsml:CompositionPart/gsml:proportion/gsml:CGI_TermValue/gsml:value", doc);
            assertXpathEvaluatesTo("interbedded component", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gsml:composition"
                    + "/gsml:CompositionPart/gsml:role", doc);
            assertXpathCount(0, "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gsml:composition"
                    + "/gsml:CompositionPart/gsml:role/FEATURE_LINK", doc);
            // lithology
            assertXpathCount(2, "//gsml:MappedFeature[@gml:id='" + id + "']/gsml:specification"
                    + "/gsml:GeologicUnit/gsml:composition/gsml:CompositionPart/gsml:lithology",
                    doc);
            assertXpathCount(3, "//gsml:MappedFeature[@gml:id='" + id + "']/gsml:specification"
                    + "/gsml:GeologicUnit/gsml:composition/gsml:CompositionPart/gsml:lithology[1]"
                    + "/gsml:ControlledConcept/gml:name", doc);
            assertXpathEvaluatesTo("name_a", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit"
                    + "/gsml:composition/gsml:CompositionPart/gsml:lithology[1]"
                    + "/gsml:ControlledConcept/gml:name[1]", doc);
            assertXpathEvaluatesTo("name_b", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit"
                    + "/gsml:composition/gsml:CompositionPart/gsml:lithology[1]"
                    + "/gsml:ControlledConcept/gml:name[2]", doc);
            assertXpathEvaluatesTo("name_c", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit"
                    + "/gsml:composition/gsml:CompositionPart/gsml:lithology[1]"
                    + "/gsml:ControlledConcept/gml:name[3]", doc);
            assertXpathCount(0, "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gsml:composition"
                    + "/gsml:CompositionPart/gsml:lithology[1]/FEATURE_LINK", doc);
            assertXpathCount(1, "//gsml:MappedFeature[@gml:id='" + id + "']/gsml:specification"
                    + "/gsml:GeologicUnit/gsml:composition/gsml:CompositionPart/gsml:lithology[2]"
                    + "/gsml:ControlledConcept/gml:name", doc);
            assertXpathEvaluatesTo("name_2", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit"
                    + "/gsml:composition/gsml:CompositionPart/gsml:lithology[2]"
                    + "/gsml:ControlledConcept/gml:name", doc);
            assertXpathCount(0, "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/gsml:composition"
                    + "/gsml:CompositionPart/gsml:lithology[2]/FEATURE_LINK", doc);
        }
        
        // check for duplicate gml:id
        assertXpathCount(1, "//gsml:GeologicUnit[@gml:id='gu.25678']", doc);

    }

    /**
     * Implementation for tests expected to get mf4 only.
     * 
     * @param xml
     */
    private void checkGetMf4Only(String xml) {
        Document doc = postAsDOM("wfs", xml);
        LOGGER.info("WFS filter GetFeature response:\n" + prettyString(doc));
        assertEquals("wfs:FeatureCollection", doc.getDocumentElement().getNodeName());
        assertXpathEvaluatesTo("1", "/wfs:FeatureCollection/@numberOfFeatures", doc);
        assertXpathCount(1, "//gsml:MappedFeature", doc);
        // mf4
        {
            String id = "mf4";
            assertXpathEvaluatesTo(id, "//gsml:MappedFeature[1]/@gml:id", doc);
            assertXpathEvaluatesTo("MURRADUC BASALT", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gml:name", doc);
            // gu.25682
            assertXpathEvaluatesTo("gu.25682", "//gsml:MappedFeature[@gml:id='" + id
                    + "']/gsml:specification/gsml:GeologicUnit/@gml:id", doc);
        }
    }
    
    /**
     * Test if we can get mf4 by its name.
     */
    public void testGetFeaturePropertyFilter() {
        String xml = //
        "<wfs:GetFeature " //
                + "service=\"WFS\" " //
                + "version=\"1.1.0\" " //
                + "xmlns:cdf=\"http://www.opengis.net/cite/data\" " //
                + "xmlns:ogc=\"http://www.opengis.net/ogc\" " //
                + "xmlns:wfs=\"http://www.opengis.net/wfs\" " //
                + "xmlns:gml=\"http://www.opengis.net/gml\" " //
                + "xmlns:gsml=\"" + AbstractAppSchemaMockData.GSML_URI + "\" " //
                + ">" //
                + "    <wfs:Query typeName=\"gsml:MappedFeature\">" //
                + "        <ogc:Filter>" //
                + "            <ogc:PropertyIsEqualTo>" //
                + "                <ogc:PropertyName>gml:name</ogc:PropertyName>" //
                + "                <ogc:Literal>MURRADUC BASALT</ogc:Literal>" //
                + "            </ogc:PropertyIsEqualTo>" //
                + "        </ogc:Filter>" //
                + "    </wfs:Query> " //
                + "</wfs:GetFeature>";
        checkGetMf4Only(xml);
    }

    /**
     * Test if we can get mf4 with a FeatureId fid filter.
     */
    public void testGetFeatureWithFeatureIdFilter() {
        String xml = //
        "<wfs:GetFeature " //
                + "service=\"WFS\" " //
                + "version=\"1.1.0\" " //
                + "xmlns:cdf=\"http://www.opengis.net/cite/data\" " //
                + "xmlns:ogc=\"http://www.opengis.net/ogc\" " //
                + "xmlns:wfs=\"http://www.opengis.net/wfs\" " //
                + "xmlns:gml=\"http://www.opengis.net/gml\" " //
                + "xmlns:gsml=\"" + AbstractAppSchemaMockData.GSML_URI + "\" " //
                + ">" //
                + "    <wfs:Query typeName=\"gsml:MappedFeature\">" //
                + "        <ogc:Filter>" //
                + "            <ogc:FeatureId fid=\"mf4\"/>" //
                + "        </ogc:Filter>" //
                + "    </wfs:Query> " //
                + "</wfs:GetFeature>";
        checkGetMf4Only(xml);
    }

    /**
     * Test if we can get mf4 with a GmlObjectId gml:id filter.
     */
    public void testGetFeatureWithGmlObjectIdFilter() {
        String xml = //
        "<wfs:GetFeature " //
                + "service=\"WFS\" " //
                + "version=\"1.1.0\" " //
                + "xmlns:cdf=\"http://www.opengis.net/cite/data\" " //
                + "xmlns:ogc=\"http://www.opengis.net/ogc\" " //
                + "xmlns:wfs=\"http://www.opengis.net/wfs\" " //
                + "xmlns:gml=\"http://www.opengis.net/gml\" " //
                + "xmlns:gsml=\"" + AbstractAppSchemaMockData.GSML_URI + "\" " //
                + ">" //
                + "    <wfs:Query typeName=\"gsml:MappedFeature\">" //
                + "        <ogc:Filter>" //
                + "            <ogc:GmlObjectId gml:id=\"mf4\"/>" //
                + "        </ogc:Filter>" //
                + "    </wfs:Query> " //
                + "</wfs:GetFeature>";
        checkGetMf4Only(xml);
    }
    
    /**
     * Test anyType as complex attributes. Previously the anyType attribute wouldn't be encoded at
     * all.
     */
    public void testAnyType() {
        final String OBSERVATION_ID_PREFIX = "observation:";
        Document doc = getAsDOM("wfs?request=GetFeature&typename=om:Observation");
        LOGGER.info("WFS GetFeature&typename=om:Observation response:\n" + prettyString(doc));
        // om:result in om:Observation is of anyType, and in this case I put in a mapped feature in
        // it
        // Because I'm lazy, I used the same properties file for observation where mf1 as observation
        // contains mf1 as MappedFeature in result attribute
        assertXpathEvaluatesTo("4", "/wfs:FeatureCollection/@numberOfFeatures", doc);
        assertXpathCount(4, "//om:Observation", doc);
        String id = "mf1";
        assertXpathEvaluatesTo(OBSERVATION_ID_PREFIX + id, "//om:Observation[1]/@gml:id", doc);
        assertXpathEvaluatesTo(id, "//om:Observation[1]/om:result/gsml:MappedFeature/@gml:id", doc);

        id = "mf2";
        assertXpathEvaluatesTo(OBSERVATION_ID_PREFIX + id, "//om:Observation[2]/@gml:id", doc);
        assertXpathEvaluatesTo(id, "//om:Observation[2]/om:result/gsml:MappedFeature/@gml:id", doc);

        id = "mf3";
        assertXpathEvaluatesTo(OBSERVATION_ID_PREFIX + id, "//om:Observation[3]/@gml:id", doc);
        assertXpathEvaluatesTo(id, "//om:Observation[3]/om:result/gsml:MappedFeature/@gml:id", doc);

        id = "mf4";
        assertXpathEvaluatesTo(OBSERVATION_ID_PREFIX + id, "//om:Observation[4]/@gml:id", doc);
        assertXpathEvaluatesTo(id, "//om:Observation[4]/om:result/gsml:MappedFeature/@gml:id", doc);
    }
    
    /**
     * Making sure attributes that are encoded as xlink:href can still be queried in filters.
     */
    public void testFilteringXlinkHref() {
        String xml = //
        "<wfs:GetFeature " //
                + "service=\"WFS\" " //
                + "version=\"1.1.0\" " //
                + "xmlns:cdf=\"http://www.opengis.net/cite/data\" " //
                + "xmlns:ogc=\"http://www.opengis.net/ogc\" " //
                + "xmlns:wfs=\"http://www.opengis.net/wfs\" " //
                + "xmlns:gml=\"http://www.opengis.net/gml\" " //
                + "xmlns:gsml=\"" + AbstractAppSchemaMockData.GSML_URI + "\" " //
                + ">" //
                + "    <wfs:Query typeName=\"gsml:MappedFeature\">" //
                + "        <ogc:Filter>" //
                + "            <ogc:PropertyIsEqualTo>" //
                + "                <ogc:PropertyName>gsml:specification/gsml:GeologicUnit/gml:name[1]</ogc:PropertyName>" //
                + "                <ogc:Literal>Yaugher Volcanic Group</ogc:Literal>" //
                + "            </ogc:PropertyIsEqualTo>" //
                + "        </ogc:Filter>" //
                + "    </wfs:Query> " //
                + "</wfs:GetFeature>";
        Document doc = postAsDOM("wfs", xml);
        LOGGER.info("WFS filter GetFeature response:\n" + prettyString(doc));
        assertEquals("wfs:FeatureCollection", doc.getDocumentElement().getNodeName());
        // there should be 3: 
        // - mf1/gu.25699
        // - mf2/gu.25678
        // - mf3/gu.25678 which is encoded as xlink:href
        assertXpathEvaluatesTo("3", "/wfs:FeatureCollection/@numberOfFeatures", doc);
        assertXpathCount(3, "//gsml:MappedFeature", doc);
    }

}
