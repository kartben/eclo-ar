package net.airvantage.eclo.m2m;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AbstractM2MSystem implements IM2MSystem {

	private SystemDetails _systemDetails = new SystemDetails();

	private Set<IM2MAlert> _alerts = new HashSet<IM2MAlert>();

	private Map<String, String> _data = new HashMap<String, String>();

	private Map<String, List<String>> _data24Hrs = new HashMap<String, List<String>>();

	private List<IValueChangedListener> _valueChangedListeners = new ArrayList<IValueChangedListener>();

	private List<IAlertListener> _alertListeners = new ArrayList<IAlertListener>();

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
	public Set<IM2MAlert> getAlerts() {
		return _alerts;
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
			notifyValueChangedListeners(path, newValue);
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
		if (!_valueChangedListeners.contains(listener))
			_valueChangedListeners.add(listener);
	}

	@Override
	public synchronized void removeValueChangedListener(
			IValueChangedListener listener) {
		_valueChangedListeners.remove(listener);
	}

	protected synchronized void notifyValueChangedListeners(String path,
			String newValue) {
		for (IValueChangedListener listener : _valueChangedListeners) {
			listener.valueChanged(this, path, newValue);
		}
	}

	@Override
	public synchronized void addAlertListener(IAlertListener listener) {
		if (!_alertListeners.contains(listener))
			_alertListeners.add(listener);
	}

	@Override
	public synchronized void removeAlertListener(IAlertListener listener) {
		_alertListeners.remove(listener);
	}

	protected synchronized void notifyAlertListeners(IM2MAlert newAlert) {
		for (IAlertListener listener : _alertListeners) {
			listener.newAlert(this, newAlert);
		}
	}

}
