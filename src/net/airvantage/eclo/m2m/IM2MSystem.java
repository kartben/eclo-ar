package net.airvantage.eclo.m2m;

import java.util.List;

public interface IM2MSystem {

	public interface IValueChangedListener {
		void valueChanged(IM2MSystem system, String path, String newValue);
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

	String getValue(String path);

	void setValue(String path, String newValue);

	List<String> getLast24HrsHistoricalValue(String path);

	void setLast24HrsHistoricalValue(String path, List<String> newValues);

	void addValueChangedListener(IValueChangedListener listener);

	void removeValueChangedListener(IValueChangedListener listener);
}
