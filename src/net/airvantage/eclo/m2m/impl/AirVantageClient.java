package net.airvantage.eclo.m2m.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.airvantage.eclo.m2m.IM2MClient;
import net.airvantage.eclo.m2m.IM2MSystem;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.JsonHttpResponseHandler;

public class AirVantageClient implements IM2MClient {
	private final String serverUrl;
	private final String login;
	private final String password;

	private final String appClientId;
	private final String appSecret;

	private String access_token;
	protected JSONObject obj;
	protected JSONArray arr;
	private AsyncHttpClient asyncHttpClient;

	public AirVantageClient(String url, String login, String password,
			String appClientId, String appSecret) {
		this.serverUrl = url;
		this.login = login;
		this.password = password;

		this.appClientId = appClientId;
		this.appSecret = appSecret;

		asyncHttpClient = new AsyncHttpClient();
	}

	public void auth(final IAuthenticationCallback callback) {
		String url = serverUrl + "/oauth/token?grant_type=password&username="
				+ login + "&password=" + password + "&client_id=" + appClientId
				+ "&client_secret=" + appSecret;

		asyncHttpClient.get(url, new JsonHttpResponseHandler() {
			@Override
			public void onSuccess(JSONObject jsonObject) {
				try {
					access_token = jsonObject.getString("access_token");

					if (access_token != null)
						callback.onAuthSuccessful();
				} catch (JSONException e) {
					callback.onAuthFailed();
				}
			}

			@Override
			public void onFailure(Throwable arg0, String arg1) {
				callback.onAuthFailed();
			}
		});

	}

	public void updateSystemDetails(final IM2MSystem system,
			final ICallback callback) {
		String url = serverUrl + "/v1/systems?uid="
				+ system.getSystemDetails().uid + "&access_token="
				+ access_token;

		asyncHttpClient.get(url, new JsonHttpResponseHandler() {
			@Override
			public void onSuccess(JSONObject jsonObject) {
				JSONObject jsonSystem;
				try {
					jsonSystem = jsonObject.getJSONArray("items")
							.getJSONObject(0);
					system.getSystemDetails().name = jsonSystem
							.getString("name");
					system.getSystemDetails().state = jsonSystem
							.getString("state");
					system.getSystemDetails().activityState = jsonSystem
							.getString("activityState");
					system.getSystemDetails().lastCommDate = jsonSystem
							.getString("lastCommDate");
					system.getSystemDetails().commStatus = jsonSystem
							.getString("commStatus");

					callback.onSuccess();
				} catch (JSONException e) {
					callback.onError("Cannot parse systemDetails", e);
				}
			}
		});
	}

	@Override
	public void updateSystemData(final IM2MSystem system, final String[] paths,
			final ICallback callback) {
		String url = serverUrl + "/v1/systems/" + system.getSystemDetails().uid
				+ "/data?ids=" + arrayToCommaSeparatedList(paths)
				+ "&access_token=" + access_token;

		asyncHttpClient.get(url, new JsonHttpResponseHandler() {
			@Override
			public void onSuccess(JSONObject jsonObject) {
				try {
					for (String path : paths)
						system.setValue(path, jsonObject.getJSONArray(path)
								.getJSONObject(0).getString("value"));

					callback.onSuccess();
				} catch (JSONException e) {
					callback.onError("Cannot parse systemData", e);
				}
			}
		});

	}

	@Override
	public void updateSystemLast24HrsData(final IM2MSystem system,
			final String path, final ICallback callback) {
		String url = serverUrl + "/v1/systems/" + system.getSystemDetails().uid
				+ "/data/" + path + "/aggregated?fn=mean&from="
				+ (System.currentTimeMillis() - 24 * 60 * 60 * 1000)
				+ "&access_token=" + access_token;

		asyncHttpClient.get(url, new JsonHttpResponseHandler() {
			@Override
			public void onSuccess(JSONArray jsonArray) {
				List<String> values = new ArrayList<String>(24);
				try {
					for (int i = jsonArray.length() - 1; i >= 0; i--) {
						values.add(jsonArray.getJSONObject(i)
								.getString("value"));
					}
					system.setLast24HrsHistoricalValue(path, values);
					callback.onSuccess();
				} catch (JSONException e) {
					callback.onError("Cannot parse aggregatedData", e);
				}
			}
		});
	}

	public boolean isAuthentified() {
		return access_token != null;
	}

	private static String arrayToCommaSeparatedList(String[] dataPathsArray) {
		StringBuilder sb = new StringBuilder();
		for (String s : dataPathsArray) {
			sb.append(s);
			sb.append(",");
		}
		if (sb.length() > 0)
			sb.deleteCharAt(sb.length() - 1);
		return sb.toString();
	}

	@Override
	public boolean supportsUnsollicitedResponses() {
		return false;
	}

	@Override
	public List<IM2MSystem> getMonitoredSystems() {
		return Collections.emptyList();
	}
}
