package net.airvantage.eclo.m2m.impl;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import net.airvantage.eclo.m2m.IM2MAlert;
import net.airvantage.eclo.m2m.IM2MClient;
import net.airvantage.eclo.m2m.IM2MSystem;

import org.apache.http.entity.StringEntity;
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
				+ URLEncoder.encode(login) + "&password="
				+ URLEncoder.encode(password) + "&client_id=" + appClientId
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
			public void onFailure(Throwable e, JSONObject errorResponse) {
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
				+ (System.currentTimeMillis() - 23 * 60 * 60 * 1000) + "&to="
				+ (System.currentTimeMillis() + 1 * 60 * 60 * 1000)
				+ "&access_token=" + access_token;

		asyncHttpClient.get(url, new JsonHttpResponseHandler() {
			@Override
			public void onSuccess(JSONArray jsonArray) {
				List<String> values = new ArrayList<String>(24);
				try {
					for (int i = 0; i < jsonArray.length(); i++) {
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

	@Override
	public void updateSystemAlerts(final IM2MSystem system,
			final ICallback callback) {
		String url = serverUrl + "/v1/alerts?" + "target="
				+ system.getSystemDetails().uid
				// + "&company=e006046f296c4842bad3db04103f11aa"
				+ "&access_token=" + access_token;

		asyncHttpClient.get(url, new JsonHttpResponseHandler() {
			@Override
			public void onSuccess(JSONObject jsonObject) {
				try {
					List<IM2MAlert> activeAlerts = new ArrayList<IM2MAlert>();
					JSONArray items = jsonObject.getJSONArray("items");
					for (int i = 0; i < items.length(); i++) {
						JSONObject alert = items.getJSONObject(i);

						if (system.getSystemDetails().uid.equals(alert
								.getString("target"))
								&& alert.isNull("acknowledgedAt")) {
							// add this alert to the system
							String alertUid = alert.getString("uid");
							String alertDate = alert.getString("date");
							String alertName = alert.getJSONObject("rule")
									.getString("name");
							String alertMessage = alert.getJSONObject("rule")
									.getString("message");

							IM2MAlert a = new M2MAlert(alertUid, new Date(Long
									.parseLong(alertDate)), alertName,
									alertMessage);
							activeAlerts.add(a);
						}
					}

					for (IM2MAlert a : system.getAlerts()) {
						if (!activeAlerts.contains(a)) {
							system.getAlerts().remove(a);
						}
					}

					for (IM2MAlert a : activeAlerts) {
						system.getAlerts().remove(a);
						system.getAlerts().add(a);
					}

					callback.onSuccess();
				} catch (JSONException e) {
					callback.onError("Cannot parse aggregatedData", e);
				}
			}
		});
	}

	@Override
	public void acknowledgeAlert(final IM2MSystem system,
			final IM2MAlert alert, final ICallback callback) {
		String url = serverUrl + "/v1/alerts/" + alert.getUid()
				+ "/acknowledge?access_token=" + access_token;

		asyncHttpClient.post(url, new JsonHttpResponseHandler() {
			@Override
			public void onSuccess(JSONObject jsonObject) {
				system.getAlerts().remove(alert);
				callback.onSuccess();
			}
		});
	}

	@Override
	public void sendData(final IM2MSystem system, final String path,
			final String value, final ICallback callback) {
		String url = serverUrl + "/v1/operations/systems/command"
				+ "?access_token=" + access_token;

		try {
			JSONObject params = new JSONObject();
			JSONArray uidsArray = new JSONArray();
			uidsArray.put(system.getSystemDetails().uid);
			JSONObject uids = new JSONObject();
			uids.put("uids", uidsArray);

			params.put("systems", uids);
			params.put("commandId", path);
			params.put("parameters", new JSONObject(value));

			asyncHttpClient.post(null, url,
					new StringEntity(params.toString()), "application/json",
					new JsonHttpResponseHandler() {
						@Override
						public void onSuccess(JSONObject jsonObject) {
							if (jsonObject.isNull("operation"))
								callback.onError("No operation scheduled",
										new Exception());
							else
								callback.onSuccess();
						}

						@Override
						public void onFailure(Throwable error, String content) {
							callback.onError(content, error);
						}
					});
		} catch (JSONException e) {
			callback.onError("Cannot construct request params", e);
		} catch (UnsupportedEncodingException e) {
			callback.onError("Cannot construct request params", e);
		}
	}

	public boolean isAuthentified() {
		return access_token != null;
	}

	@Override
	public boolean supportsUnsollicitedResponses() {
		return false;
	}

	@Override
	public List<IM2MSystem> getMonitoredSystems() {
		return Collections.emptyList();
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

}
