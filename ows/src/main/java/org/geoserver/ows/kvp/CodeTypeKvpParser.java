/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.ows.kvp;

import net.opengis.ows11.CodeType;
import net.opengis.ows11.Ows11Factory;

import org.geoserver.ows.KvpParser;

/**
 * Generic KVP parser for {@link CodeType} objects
 * 
 * @author Andrea Aime - TOPP
 * 
 */
public class CodeTypeKvpParser extends KvpParser {

    public CodeTypeKvpParser(String key) {
        super(key, CodeType.class);
    }

    public CodeTypeKvpParser(String key, String service) {
        super(key, CodeType.class);
        setService(service);
    }

    @Override
    public Object parse(String value) throws Exception {
        CodeType result = Ows11Factory.eINSTANCE.createCodeType();
        result.setValue(value);
        return result;
    }

}
