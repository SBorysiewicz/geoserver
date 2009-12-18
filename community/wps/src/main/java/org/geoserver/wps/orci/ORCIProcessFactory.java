package org.geoserver.wps.orci;

import java.awt.RenderingHints.Key;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.geotools.data.Parameter;
import org.geotools.feature.NameImpl;
import org.geotools.process.Process;
import org.geotools.process.ProcessFactory;
import org.opengis.feature.type.Name;
import org.opengis.util.InternationalString;

public class ORCIProcessFactory implements ProcessFactory {
    public static final String ORCI_NAMESPACE = "orci";
    
    private Map<Name, IORCIProcess> processes = new HashMap<Name, IORCIProcess>();
    
    public ORCIProcessFactory() {
        
    	// build list of processes
        processes.put(new NameImpl(ORCI_NAMESPACE, "Nearest"), new ORCINearestProcess());
        processes.put(new NameImpl(ORCI_NAMESPACE, "Snap"), new ORCISnapProcess());
    }

	public Set<Name> getNames() {
		return Collections.unmodifiableSet(processes.keySet());
	}
    
    void checkName(Name name) {
        if(name == null)
            throw new NullPointerException("Process name cannot be null");
        if(!processes.containsKey(name))
            throw new IllegalArgumentException("Unknown process '" + name + "'");
    }

	public Process create(Name name) {
        checkName(name);
        String clzName = "org.geoserver.wps.orci.ORCI" + name.getLocalPart() + "Process";
        try {
        	Class<?> clz = Class.forName(clzName);
        	return (Process)clz.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Cannot create process for class " + clzName, e);
        }
	}

	public InternationalString getDescription(Name name) {
        checkName(name);
        return processes.get(name).getDescription();
	}

	public InternationalString getTitle(Name name) {
        checkName(name);
        return processes.get(name).getTitle();
	}

	public boolean supportsProgress(Name name) {
        checkName(name);
		return true;
	}

    public String getVersion(Name name) {
        checkName(name);
        return "1.0.0";
    }

	public Map<String, Parameter<?>> getParameterInfo(Name name) {
        checkName(name);
        return processes.get(name).getParameterInfo();
	}

	public Map<String, Parameter<?>> getResultInfo(Name name, Map<String, Object> inputs) throws IllegalArgumentException {
        checkName(name);
        return processes.get(name).getResultInfo(inputs);
	}

	public boolean isAvailable() {
		return true;
	}

	@SuppressWarnings("unchecked")
	public Map<Key, ?> getImplementationHints() {
		return Collections.EMPTY_MAP;
	}

}
