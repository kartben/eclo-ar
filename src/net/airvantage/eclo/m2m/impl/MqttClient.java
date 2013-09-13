package net.airvantage.eclo.m2m.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.airvantage.eclo.m2m.IM2MAlert;
import net.airvantage.eclo.m2m.IM2MClient;
import net.airvantage.eclo.m2m.IM2MSystem;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;

import android.util.Log;

public class MqttClient implements IM2MClient, MqttCallback {

	private org.eclipse.paho.client.mqttv3.MqttClient mqttClient;

	private String nameSpace = "/benjamin-test";

	private List<IM2MSystem> monitoredSystems = new ArrayList<IM2MSystem>();

	public MqttClient(String host) {
		try {
			this.mqttClient = new org.eclipse.paho.client.mqttv3.MqttClient(
					host,
					org.eclipse.paho.client.mqttv3.MqttClient
							.generateClientId(), null);

			this.mqttClient.setCallback(this);
			this.mqttClient.connect();
			this.mqttClient.subscribe(getNameSpace() + "/#");

		} catch (MqttException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void auth(IAuthenticationCallback authCallback) {
		// nothing
		authCallback.onAuthSuccessful();
	}

	@Override
	public boolean isAuthentified() {
		return true;
	}

	@Override
	public void updateSystemDetails(IM2MSystem system, ICallback callback) {
		// TODO Auto-generated method stub

	}

	@Override
	public void updateSystemData(IM2MSystem system, String[] paths,
			ICallback callback) {
		// TODO Auto-generated method stub

	}

	@Override
	public void updateSystemLast24HrsData(IM2MSystem system, String path,
			ICallback callback) {
		// TODO Auto-generated method stub

	}

	@Override
	public void updateSystemAlerts(IM2MSystem system, ICallback callback) {
		// TODO Auto-generated method stub

	}

	@Override
	public void acknowledgeAlert(IM2MSystem currentSystem, IM2MAlert im2mAlert,
			ICallback iCallback) {
		// TODO Auto-generated method stub

	}

	@Override
	public void sendData(IM2MSystem system, String path, String value,
			ICallback callback) {
		try {
			mqttClient.publish(nameSpace + "/" + system.getSystemDetails().uid
					+ "/" + path, value.getBytes(), 0, false);
			callback.onSuccess();
		} catch (MqttPersistenceException e) {
			callback.onError(e.getMessage(), e);
		} catch (MqttException e) {
			callback.onError(e.getMessage(), e);
		}
	}

	@Override
	public void connectionLost(Throwable t) {
		Log.d("MqttClient", t.getMessage(), t);
	}

	@Override
	public void deliveryComplete(IMqttDeliveryToken t) {
		Log.d("MqttClient", "deliveryComplete");
	}

	@Override
	public void messageArrived(String topic, MqttMessage message)
			throws Exception {
		String payload = new String(message.getPayload());

		Pattern pattern = Pattern.compile(nameSpace + "/(.+)/(.+)");
		Matcher matcher = pattern.matcher(topic);

		if (matcher.matches()) {
			String systemUid = matcher.group(1);
			String dataPath = matcher.group(2);

			for (IM2MSystem s : getMonitoredSystems()) {
				if (systemUid.equals(s.getSystemDetails().uid)) {
					s.setValue(dataPath, payload);
				}
			}
		}

		// Log.d("MqttClient", topic + " payload: " + payload);
	}

	public String getNameSpace() {
		return nameSpace;
	}

	@Override
	public boolean supportsUnsollicitedResponses() {
		return true;
	}

	public List<IM2MSystem> getMonitoredSystems() {
		return monitoredSystems;
	}

}
