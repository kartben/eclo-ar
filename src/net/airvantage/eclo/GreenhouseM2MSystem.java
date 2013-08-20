package net.airvantage.eclo;

import net.airvantage.eclo.m2m.AbstractM2MSystem;

public class GreenhouseM2MSystem extends AbstractM2MSystem {

	private static final String TEMPERATURE_PATH = "greenhouse.data.temperature";
	private static final String LUMINOSITY_PATH = "greenhouse.data.luminosity";
	private static final String HUMIDITY_PATH = "greenhouse.data.humidity";

	public GreenhouseM2MSystem(String uid) {
		super(uid);
	}

	public float getTemperature() {
		return Float.parseFloat(getValue(TEMPERATURE_PATH));
	}

	public float getLuminosity() {
		return Float.parseFloat(getValue(LUMINOSITY_PATH));
	}

	public float getHumidity() {
		return Float.parseFloat(getValue(HUMIDITY_PATH));
	}
}
