package net.airvantage.eclo;

import com.metaio.sdk.jni.IGeometry;
import com.metaio.sdk.jni.Vector3d;

public class SensorButton {

	public enum SENSOR_TYPE {
		TEMPERATURE("°C"), LUMINOSITY("lx"), HUMIDITY("%");

		String unit;

		private SENSOR_TYPE(String unit) {
			this.unit = unit;
		}
	};

	SENSOR_TYPE type;
	String name; // name that can be used to recognize touched geometry

	enum DIRECTION {
		BUTTON_TO_CHART, CHART_TO_BUTTON
	};

	public SensorButton(String name, SENSOR_TYPE type) {
		this.name = name;
		this.type = type;
	}

	SensorButton.DIRECTION direction = SensorButton.DIRECTION.BUTTON_TO_CHART;

	String texturePath;
	String chartPath;
	boolean texturesNeedRefresh;
	IGeometry model;
	IGeometry chartModel;
	float initScale = .7f;
	volatile Vector3d initPosition;
	Vector3d defaultPosition;

	float targetScale = .7f;
	Vector3d targetPosition;

	int timeLinePos = -1;
}