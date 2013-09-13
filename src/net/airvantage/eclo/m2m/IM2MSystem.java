package net.airvantage.eclo.m2m;

import java.util.List;
import java.util.Set;

public interface IM2MSystem {

	public interface IValueChangedListener {
		void valueChanged(IM2MSystem system, String path, String newValue);
	}

	public interface IAlertListener {
		void newAlert(IM2MSystem system, IM2MAlert alert);
	}

	public static class SystemDetails {
		public String uid;
		public String name;
		public String state;
		public String activityState;
		public String lastCommDate;
		public String commStatus;
	}

	SystemDetails getSystemDetails();

	Set<IM2MAlert> getAlerts();

	String getValue(String path);

	void setValue(String path, String newValue);

	List<String> getLast24HrsHistoricalValue(String path);

	void setLast24HrsHistoricalValue(String path, List<String> newValues);

	void addValueChangedListener(IValueChangedListener listener);

	void removeValueChangedListener(IValueChangedListener listener);

	void addAlertListener(IAlertListener listener);

	void removeAlertListener(IAlertListener listener);
}
