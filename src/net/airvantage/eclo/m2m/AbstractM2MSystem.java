package net.airvantage.eclo.m2m;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observer;

public class AbstractM2MSystem implements IM2MSystem {

	private SystemDetails _systemDetails = new SystemDetails();

	private Map<String, String> _data = new HashMap<String, String>();

	private Map<String, List<String>> _data24Hrs = new HashMap<String, List<String>>();

	private List<IValueChangedListener> listeners = new ArrayList<IValueChangedListener>();

	@SuppressWarnings("unused")
	private AbstractM2MSystem() {
	}

	public AbstractM2MSystem(String uid) {
		_systemDetails.uid = uid;
	}

	@Override
	public SystemDetails getSystemDetails() {
		return _systemDetails;
	}

	@Override
	public String getValue(String path) {
		return _data.get(path);
	}

	@Override
	public void setValue(String path, String newValue) {
		String oldValue = _data.get(path);
		_data.put(path, newValue);

		if (oldValue != null && !oldValue.equals(newValue) || oldValue == null)
			notifyListeners(path, newValue);
	}
	
	@Override
	public List<String> getLast24HrsHistoricalValue(String path) {
		return _data24Hrs.get(path);
	}
	
	@Override
	public void setLast24HrsHistoricalValue(String path, List<String> newValues) {
		List<String> oldValues = _data24Hrs.get(path);
		_data24Hrs.put(path, newValues);
			
		// notify listeners
		
	}

	@Override
	public synchronized void addValueChangedListener(
			IValueChangedListener listener) {
		if (!listeners.contains(listener))
			listeners.add(listener);
	}

	@Override
	public synchronized void removeValueChangedListener(
			IValueChangedListener listener) {
		listeners.remove(listener);
	}

	protected synchronized void notifyListeners(String path, String newValue) {
		for (IValueChangedListener listener : listeners) {
			listener.valueChanged(this, path, newValue);
		}
	}

}
