// Copyright 2007-2013 metaio GmbH. All rights reserved.
package net.airvantage.eclo;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import net.airvantage.eclo.SensorButton.SENSOR_TYPE;
import net.airvantage.eclo.m2m.IM2MAlert;
import net.airvantage.eclo.m2m.IM2MClient;
import net.airvantage.eclo.m2m.IM2MClient.IAuthenticationCallback;
import net.airvantage.eclo.m2m.IM2MClient.ICallback;
import net.airvantage.eclo.m2m.IM2MSystem;
import net.airvantage.eclo.m2m.IM2MSystem.IAlertListener;
import net.airvantage.eclo.m2m.IM2MSystem.IValueChangedListener;
import net.airvantage.eclo.m2m.impl.AirVantageClient;
import net.airvantage.eclo.transitions.Cubic;
import net.airvantage.eclo.transitions.Elastic;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Typeface;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Size;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;

import com.facebook.FacebookRequestError;
import com.facebook.HttpMethod;
import com.facebook.LoggingBehavior;
import com.facebook.Request;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.Settings;
import com.facebook.UiLifecycleHelper;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.BinaryHttpResponseHandler;
import com.metaio.sdk.ARViewActivity;
import com.metaio.sdk.GestureHandlerAndroid;
import com.metaio.sdk.MetaioDebug;
import com.metaio.sdk.jni.IGeometry;
import com.metaio.sdk.jni.IGeometryVector;
import com.metaio.sdk.jni.IMetaioSDKCallback;
import com.metaio.sdk.jni.ImageStruct;
import com.metaio.sdk.jni.Rotation;
import com.metaio.sdk.jni.TrackingValuesVector;
import com.metaio.sdk.jni.Vector3d;
import com.metaio.tools.SystemInfo;
import com.metaio.tools.io.AssetsManager;

public class MainActivity extends ARViewActivity implements
		OnSharedPreferenceChangeListener, IValueChangedListener, IAlertListener {
	private static final String SWITCH_BUTTON = "switch-button";

	private static final String FACEBOOK_BUTTON = "facebook-button";

	private class AssetsExtracter extends AsyncTask<Integer, Integer, Boolean> {

		@Override
		protected void onPreExecute() {
			// mProgress.setVisibility(View.VISIBLE);
		}

		@Override
		protected Boolean doInBackground(Integer... params) {
			try {
				AssetsManager.extractAllAssets(getApplicationContext(), true);
			} catch (IOException e) {
				MetaioDebug.printStackTrace(Log.ERROR, e);
				return false;
			}

			return true;
		}

		@Override
		protected void onPostExecute(Boolean result) {
		}
	}

	private final class ImagesGenerationTask extends TimerTask {
		private int count = 0;

		@Override
		public void run() {

			for (String cosName : new String[] { "COS1", "COS2" })

			{
				final GreenhouseM2MSystem system = mGreenhousesByCosName
						.get(cosName);

				if (system == null)
					continue;

				// TODO do this elsewhere (another task?)
				m2mClient.updateSystemAlerts(system, new ICallback() {
					@Override
					public void onSuccess() {

					}

					@Override
					public void onError(String errorDetails, Throwable t) {
						// TODO log
					}
				});

				m2mClient.updateSystemData(
						system,
						mButtonsByGreenhouse.get(system).keySet()
								.toArray(new String[0]), new ICallback() {

							@Override
							public void onSuccess() {
							}

							@Override
							public void onError(String errorDetails, Throwable t) {
								Log.d(getPackageName(), errorDetails, t);
							}
						});

				// only update historical charts once every 20 calls
				if ((count++ % 20) != 0)
					return;

				for (final String path : mButtonsByGreenhouse.get(system)
						.keySet()) {
					m2mClient.updateSystemLast24HrsData(system, path,
							new ICallback() {
								@Override
								public void onSuccess() {
									updateHistoricalGraph(mButtonsByGreenhouse
											.get(system).get(path), system
											.getLast24HrsHistoricalValue(path));
								}

								@Override
								public void onError(String errorDetails,
										Throwable t) {
									// TODO log
								}
							});
				}

			}
		}

		/**
		 * Update button's chart path with a chart for aggData
		 */
		private String updateHistoricalGraph(final SensorButton button,
				List<String> aggData) {
			double[] minMax = computeMinMax(aggData);

			StringBuilder chartURL = new StringBuilder();
			chartURL.append("http://chart.googleapis.com/chart");
			chartURL.append("?chf=bg,s,FFFFFF00");
			// chartURL.append("&chxr=0,10,30");
			chartURL.append("&chxs=0,ffffff,16,0,lt,000000");
			chartURL.append("&chxt=y");
			chartURL.append("&chs=500x200");
			chartURL.append("&cht=lc");
			chartURL.append("&chco=AA0033");
			chartURL.append("&chd=t:");
			for (String val : aggData) {
				chartURL.append(("null".equals(val)) ? "_" : val);
				chartURL.append(",");
			}
			chartURL.deleteCharAt(chartURL.length() - 1);
			chartURL.append("&chls=3,3,5");
			chartURL.append("&chxr=0," + minMax[0] + "," + minMax[1]);
			chartURL.append("&chds=" + minMax[0] + "," + minMax[1]);
			// chartURL.append("&chds=a");
			chartURL.append("&chm=B,FFCC3317,0,0,0");

			Log.d("chart", chartURL.toString());

			final String chartPath = getCacheDir() + "/"
					+ System.currentTimeMillis() + ".png";

			AsyncHttpClient httpClient = new AsyncHttpClient();
			httpClient.get(chartURL.toString(),
					new BinaryHttpResponseHandler() {
						@Override
						public void onSuccess(byte[] data) {
							BufferedOutputStream bos;
							try {
								bos = new BufferedOutputStream(
										new FileOutputStream(
												new File(chartPath)));
								bos.write(data);
								bos.close();

								button.chartPath = chartPath;
								button.chartNeedsRefresh = true;
							} catch (FileNotFoundException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					});

			return null;
		}

		private double[] computeMinMax(List<String> aggData) {
			double[] minMax = new double[] { Double.MIN_VALUE, Double.MAX_VALUE };

			for (String d : aggData) {
				if ("null".equals(d))
					continue;

				double n = Double.parseDouble(d);
				if (minMax[0] == Double.MIN_VALUE)
					minMax[0] = n;
				else
					minMax[0] = Math.min(minMax[0], n);
				if (minMax[1] == Double.MAX_VALUE)
					minMax[1] = n;
				else
					minMax[1] = Math.max(minMax[1], n);
			}

			double range = minMax[1] - minMax[0];
			double med = minMax[0] + range / 2;

			final double RATIO = 0.6;

			minMax[0] = med - range * RATIO - 1;
			minMax[1] = med + range * RATIO + 1;

			return minMax;
		}
	}

	private static final int BUTTON_ANIMATION_DURATION = 25;

	private static final String MEGAPHONE = "megaphone";

	// interesting vectors
	final Vector3d VECTOR_GREENHOUSE_ZENITH = new Vector3d(130, 160, 80);

	String trackingConfigFile;

	private MetaioSDKCallbackHandler mCallbackHandler;

	private Paint mPaint;

	private Timer mButtonsGenerationHandler;

	private AssetsExtracter mTask;

	private Map<IM2MSystem, Map<String, SensorButton>> mButtonsByGreenhouse = new HashMap<IM2MSystem, Map<String, SensorButton>>();

	private Map<String, GreenhouseM2MSystem> mGreenhousesByCosName = new HashMap<String, GreenhouseM2MSystem>();

	private IM2MClient m2mClient;

	private String mActiveCosName;

	private IGeometry mSwitchModel;

	private UiLifecycleHelper uiHelper;

	private SensorButton getButtonByName(Collection<SensorButton> buttons,
			String name) {
		for (SensorButton button : buttons) {
			if (name.equals(button.name))
				return button;
		}
		return null;
	}

	protected void startCamera() {
		// Select the back facing camera by default
		final int cameraIndex = SystemInfo
				.getCameraIndex(CameraInfo.CAMERA_FACING_BACK);
		// mCameraResolution = metaioSDK.startCamera(cameraIndex, 960, 720);
		mCameraResolution = metaioSDK.startCamera(cameraIndex, 640, 480);
	}

	public static class SettingsFragment extends PreferenceFragment {
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);

			// Load the preferences from an XML resource
			addPreferencesFromResource(R.xml.prefs);
		}
	}

	private void onSessionStateChange(Session session, SessionState state,
			Exception exception) {
		if (state.isOpened()) {
			Log.i("MainActivity", "Logged in...");
		} else if (state.isClosed()) {
			Log.i("MainActivity", "Logged out...");
		}
	}

	private Session.StatusCallback callback = new Session.StatusCallback() {
		@Override
		public void call(Session session, SessionState state,
				Exception exception) {
			onSessionStateChange(session, state, exception);
		}
	};

	private IGeometry facebookButtonModel;

	public Bitmap screenshot;

	private MediaPlayer mMediaPlayer;

	private Typeface mTypeFace;

	private TextToSpeech mTTS;

	private IGeometry mMegaphoneModel;

	private boolean mMegaphoneHasRung = false;

	private GestureHandlerAndroid mGestureHandler;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		try {
			PackageInfo info = getPackageManager().getPackageInfo(
					this.getPackageName(), PackageManager.GET_SIGNATURES);
			for (Signature signature : info.signatures) {
				MessageDigest md = MessageDigest.getInstance("SHA");
				md.update(signature.toByteArray());
				Log.d("KeyHash:",
						Base64.encodeToString(md.digest(), Base64.DEFAULT));
			}
		} catch (NameNotFoundException e) {
		} catch (NoSuchAlgorithmException e) {
		}

		// Facebook session
		Settings.addLoggingBehavior(LoggingBehavior.REQUESTS);
		uiHelper = new UiLifecycleHelper(this, callback);
		uiHelper.onCreate(savedInstanceState);

		// Enable metaio SDK log messages based on build configuration
		MetaioDebug.enableLogging(BuildConfig.DEBUG);

		// extract all the assets
		mTask = new AssetsExtracter();
		mTask.execute(0);

		mCallbackHandler = new MetaioSDKCallbackHandler();
		mPaint = new Paint();
		mPaint.setAntiAlias(true);
		mPaint.setSubpixelText(true);
		mTypeFace = Typeface.createFromAsset(getAssets(),
				"digital_counter_7.ttf");

		PreferenceManager.getDefaultSharedPreferences(MainActivity.this)
				.registerOnSharedPreferenceChangeListener(MainActivity.this);

		// Display the fragment as the main content.
		getFragmentManager().beginTransaction()
				.replace(android.R.id.content, new SettingsFragment()).commit();

		m2mClient = createM2MClient();
		m2mClient.auth(new IAuthenticationCallback() {
			@Override
			public void onAuthSuccessful() {
				initGreenhouses();
			}

			@Override
			public void onAuthFailed() {
				new AlertDialog.Builder(MainActivity.this)
						.setTitle("Auth. error").setMessage("Cannot login")
						.show();

			}
		});

		mTTS = new TextToSpeech(this, new OnInitListener() {
			@Override
			public void onInit(int status) {
				mTTS.setLanguage(Locale.US);
			}
		});

		// gesture recognition
		// mGestureHandler = new GestureHandlerAndroid(metaioSDK,
		// GestureHandler.GESTURE_DRAG);
		// mGestureHandler.registerCallback(new IGestureHandlerCallback() {
		// private IGeometry geometry;
		//
		// @Override
		// public void onGestureEvent(EGESTURE_STATE gesture_state,
		// IGeometryVector geometries, int groupID) {
		// if (gesture_state == EGESTURE_STATE.EGE_TRANSLATING_END) {
		// Collection<SensorButton> buttons = mButtonsByGreenhouse
		// .get(mGreenhousesByCosName.get(mActiveCosName))
		// .values();
		// SensorButton button;
		// button = getButtonByName(buttons, geometry.getName()
		// .substring(0, geometry.getName().indexOf("-chart")));
		// button.targetPosition = geometry.getTranslation();
		// button.direction = SensorButton.DIRECTION.CHART_TO_BUTTON;
		// button.initScale = 2f;
		// button.targetScale = 0.7f;
		// button.timeLinePos = 0;
		// }
		// }
		//
		// @Override
		// public void onGeometryPicked(ETOUCH_STATE state, IGeometry geometry)
		// {
		// if (state == ETOUCH_STATE.ETS_TOUCH_DOWN)
		// this.geometry = geometry;
		// }
		//
		// });

	}

	private IM2MClient createM2MClient() {
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);

		IM2MClient client;

		client = new AirVantageClient("https://edge.m2mop.net/api",
				prefs.getString(getResources().getString(R.string.pref_login),
						""), prefs.getString(
						getResources().getString(R.string.pref_password), ""),
				getResources().getString(R.string.airvantage_clientId),
				getResources().getString(R.string.airvantage_clientSecret));

		// client = new MqttClient("tcp://m2m.eclipse.org:1883");

		return client;

	}

	private void initGreenhouses() {
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);

		synchronized (mGreenhousesByCosName) {
			for (GreenhouseM2MSystem gh : mGreenhousesByCosName.values()) {
				gh.removeValueChangedListener(this);
			}
			mGreenhousesByCosName.clear();

			for (Map<String, SensorButton> buttonsMap : mButtonsByGreenhouse
					.values()) {
				for (SensorButton b : buttonsMap.values()) {
					if (b.chartModel != null) {
						b.chartModel.setVisible(false);
						b.chartModel.delete();
					}

					if (b.model != null) {
						b.model.setVisible(false);
						b.model.delete();
					}
				}

			}

			mButtonsByGreenhouse.clear();

			for (int i = 1; i <= 2; i++) {
				final GreenhouseM2MSystem greenhouseM2MSystem = new GreenhouseM2MSystem(
						prefs.getString("system-COS" + i,
								"9dd4264bf5dd491da73791dce4275b3b"));
				m2mClient.updateSystemDetails(greenhouseM2MSystem,
						new ICallback() {
							@Override
							public void onSuccess() {
								Log.d("ECLO",
										"System details updated for "
												+ greenhouseM2MSystem
														.getSystemDetails().uid);
							}

							@Override
							public void onError(String errorDetails, Throwable t) {
								Log.d("ECLO",
										"System details update failed for "
												+ greenhouseM2MSystem
														.getSystemDetails().uid);
							}
						});
				mGreenhousesByCosName.put("COS" + i, greenhouseM2MSystem);
				greenhouseM2MSystem.addValueChangedListener(this);
				greenhouseM2MSystem.addAlertListener(this);
				Map<String, SensorButton> buttonsMap = new HashMap<String, SensorButton>();

				for (int j = 1; j <= 3; j++) {
					String path = prefs.getString("system-COS" + i + "-sensor"
							+ j + "-path", null);
					String type = prefs.getString("system-COS" + i + "-sensor"
							+ j + "-type", "GENERIC");

					Log.d("", path + "-" + type);

					if (path != null) {
						buttonsMap.put(path, new SensorButton("button-" + j,
								SENSOR_TYPE.valueOf(type)));
					}
					IGeometryVector g = metaioSDK.getLoadedGeometries();
					for (int ii = 0; ii < g.size(); ii++) {
						Log.d("geometry", g.toString() + " -- "
								+ g.get(ii).getName());
					}

				}

				mButtonsByGreenhouse.put(greenhouseM2MSystem, buttonsMap);
			}

			if (m2mClient.supportsUnsollicitedResponses()) {
				m2mClient.getMonitoredSystems().clear();
				m2mClient.getMonitoredSystems().addAll(
						mGreenhousesByCosName.values());
			}
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (!m2mClient.isAuthentified()) {
			// try to authentify
			m2mClient.auth(new IAuthenticationCallback() {
				@Override
				public void onAuthSuccessful() {
					_doResume();
				}

				@Override
				public void onAuthFailed() {
					Intent intent = new Intent(getBaseContext(),
							LoginActivity.class);
					startActivityForResult(intent, 2);
				}
			});
		} else {
			_doResume();
		}

	}

	private void _doResume() {
		mButtonsGenerationHandler = new Timer();
		TimerTask task = new ImagesGenerationTask();
		mButtonsGenerationHandler.schedule(task, 0L, 1000L);

		// For scenarios where the main activity is launched
		// and user
		// session is not null, the session state change
		// notification
		// may not be triggered. Trigger it if it's
		// open/closed.
		Session session = Session.getActiveSession();
		if (session != null && (session.isOpened() || session.isClosed())) {
			onSessionStateChange(session, session.getState(), null);
		}

		uiHelper.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (mButtonsGenerationHandler != null)
			mButtonsGenerationHandler.cancel();
		uiHelper.onPause();
	}

	@Override
	protected void onDestroy() {
		// Close the Text to Speech Library
		if (mTTS != null) {
			mTTS.stop();
			mTTS.shutdown();
		}

		super.onDestroy();
		PreferenceManager.getDefaultSharedPreferences(this)
				.unregisterOnSharedPreferenceChangeListener(this);
		uiHelper.onDestroy();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		uiHelper.onSaveInstanceState(outState);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.settings, menu);
		// Intent i = new Intent(this, SettingsActivity.class);
		// startActivityForResult(i, 1);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		SharedPreferences prefs;
		switch (item.getItemId()) {

		case R.id.menu_settings:
			Intent i = new Intent(this, SettingsActivity.class);
			startActivityForResult(i, 1);
			prefs = PreferenceManager.getDefaultSharedPreferences(this);
			prefs.registerOnSharedPreferenceChangeListener(this);

			break;

		}

		return true;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == 1) {
			Log.d(getPackageName(), "Settings result");
			return;
		}
		if (requestCode == 2) {
			Log.d(getPackageName(), "Login result");
			m2mClient = createM2MClient();
			if (!m2mClient.isAuthentified()) {
				finish();
			}
			// TODO remove
			initGreenhouses();
			return;
		}

		uiHelper.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	protected int getGUILayout() {
		return R.layout.main;
	}

	@Override
	public void onDrawFrame() {
		super.onDrawFrame();

		if (metaioSDK != null) {

			if (Session.getActiveSession().isOpened()) {
				if (facebookButtonModel == null) {
					facebookButtonModel = metaioSDK
							.createGeometryFromImage(AssetsManager
									.getAssetPath("facebook.png"));
					facebookButtonModel.setName(FACEBOOK_BUTTON);
					facebookButtonModel.setScale(.7f);
					// facebookButtonModel.setRotation(new Rotation(new
					// Vector3d(
					// -(float) (Math.PI / 2), 0f, 0f)));
					// facebookButtonModel.setTranslation(new Vector3d(-60f, 0,
					// 60f));

					facebookButtonModel.setTranslation(new Vector3d(190f, 30,
							150f));
				}
				facebookButtonModel.setVisible(true);
			} else if (facebookButtonModel != null)
				facebookButtonModel.setVisible(false);

			if (mActiveCosName == null || "empty".equals(mActiveCosName))
				return;

			int offset = 0;
			GreenhouseM2MSystem activeSystem = mGreenhousesByCosName
					.get(mActiveCosName);

			// check alerts
			if (activeSystem.getAlerts().isEmpty()) {
				mMegaphoneModel.setVisible(false);
				mMegaphoneHasRung = false;
			}

			if (!activeSystem.getAlerts().isEmpty()
					&& !mMegaphoneModel.isVisible()) {
				mMegaphoneModel.setVisible(true);

				if (!mMegaphoneHasRung) {
					mTTS.speak(activeSystem.getAlerts().iterator().next()
							.getMessage()
					/* + ". You can acknowledge by pressing the megaphone." */,
							TextToSpeech.QUEUE_FLUSH, null);
					mMegaphoneHasRung = true;
				}
			}

			Map<String, SensorButton> buttons = mButtonsByGreenhouse
					.get(activeSystem);
			for (SensorButton button : buttons.values()) {
				if (button.model == null) {

					if (button.texturePath != null) {
						// create new geometry
						IGeometry model;
						model = metaioSDK
								.createGeometryFromImage(button.texturePath);
						model.setName(button.name);
						model.setScale(.7f, false);
						button.initPosition = new Vector3d(30 + offset, 160, 0);
						button.defaultPosition = button.initPosition;
						model.setTranslation(button.initPosition, false);
						MetaioDebug
								.log("The image has been loaded successfully");
						button.model = model;
					}
				} else {
					// update texture with new image
					if (button.textureNeedsRefresh) {
						// MetaioDebug.log("refresh texture for button "
						// + button.name);
						button.model.setTexture(button.texturePath);
						button.textureNeedsRefresh = false;
					}
				}

				if (button.chartModel == null) {
					if (button.chartPath != null) {
						IGeometry model;
						model = metaioSDK
								.createGeometryFromImage(button.chartPath);
						if (model != null) {
							model.setName(button.name + "-chart");
							model.setScale(1f);
							model.setTranslation(button.initPosition);
							model.setVisible(false);
							// mGestureHandler.addObject(model, 1);

							button.chartModel = model;
						}
					}
				} else {
					if (button.chartNeedsRefresh) {
						button.chartModel.setTexture(button.chartPath);
						button.chartNeedsRefresh = false;
					}
				}

				animate(buttons.values(), button);

				offset += 75;
			}

			// get all detected poses/targets
			TrackingValuesVector poses = metaioSDK.getTrackingValues();
			if (poses.size() != 0) {
				for (SensorButton button : buttons.values()) {
					if (button.model != null)
						button.model.setCoordinateSystemID(poses.get(0)
								.getCoordinateSystemID());
				}
				if (mSwitchModel != null) {
					mSwitchModel.setCoordinateSystemID(poses.get(0)
							.getCoordinateSystemID());
				}
			}
		}
	}

	private void animate(Collection<SensorButton> buttons, SensorButton button) {
		if (button.chartModel == null)
			return;

		if (button.timeLinePos == 0
				&& button.direction == SensorButton.DIRECTION.BUTTON_TO_CHART) {
			button.chartModel.setVisible(true);
			// all other buttons should be transparent
			button.model.setTransparency(0.001f);
			for (SensorButton b : buttons) {
				if (b != button) {
					b.model.setTransparency(0.6f);
				}
			}
		}

		if (button.timeLinePos != -1
				&& button.timeLinePos <= BUTTON_ANIMATION_DURATION) {

			float s = Elastic.easeIn(button.timeLinePos, button.initScale,
					(button.targetScale - button.initScale),
					BUTTON_ANIMATION_DURATION);

			float x = Elastic
					.easeIn(button.timeLinePos, button.initPosition.getX(),
							(button.targetPosition.getX() - button.initPosition
									.getX()), BUTTON_ANIMATION_DURATION);

			float y = Elastic
					.easeIn(button.timeLinePos, button.initPosition.getY(),
							(button.targetPosition.getY() - button.initPosition
									.getY()), BUTTON_ANIMATION_DURATION);

			float z = Cubic
					.easeIn(button.timeLinePos, button.initPosition.getZ(),
							(button.targetPosition.getZ() - button.initPosition
									.getZ()), BUTTON_ANIMATION_DURATION);

			button.timeLinePos++;

			button.chartModel.setScale(s, false);
			button.chartModel.setTranslation(new Vector3d(x, y, z), false);

		}

		if (button.timeLinePos >= BUTTON_ANIMATION_DURATION
				&& button.direction == SensorButton.DIRECTION.CHART_TO_BUTTON) {
			button.direction = SensorButton.DIRECTION.BUTTON_TO_CHART;
			button.chartModel.setVisible(false);
			// all buttons should be opaque
			for (SensorButton b : buttons)
				b.model.setTransparency(0.001f);
		}

	}

	public void onButtonClick(View v) {
		finish();
	}

	@Override
	protected void loadContents() {
		try {
			// switch sound preparation
			mMediaPlayer = new MediaPlayer();
			FileInputStream fis = new FileInputStream(
					AssetsManager.getAssetPath("switch.wav"));
			mMediaPlayer.setDataSource(fis.getFD());
			mMediaPlayer.prepare();
			fis.close();

			// Load desired tracking data for planar marker tracking
			trackingConfigFile = AssetsManager
					.getAssetPath("TrackingData_Marker.xml");

			boolean result = metaioSDK
					.setTrackingConfiguration(trackingConfigFile);
			MetaioDebug.log("Markerless tracking data loaded: " + result);

			String switchModelFile = AssetsManager.getAssetPath("switch.obj");
			if (switchModelFile != null) {
				// Loading 3D geometry
				mSwitchModel = metaioSDK.createGeometry(switchModelFile);
				if (mSwitchModel != null) {
					mSwitchModel.setName(SWITCH_BUTTON);
					// Set geometry properties
					mSwitchModel.setScale(new Vector3d(100f, 100f, 100f));
					mSwitchModel.setTranslation(new Vector3d(50, 20, 150));
					mSwitchModel.setRotation(new Rotation(0f, 0f, 0f));
					// mSwitchModel.setVisible(false);

				} else
					MetaioDebug.log(Log.ERROR, "Error loading geometry: "
							+ switchModelFile);
			}

			String megaphoneModelFile = AssetsManager
					.getAssetPath("megaphone.obj");
			if (megaphoneModelFile != null) {
				mMegaphoneModel = metaioSDK.createGeometry(megaphoneModelFile);
				if (mMegaphoneModel != null) {
					mMegaphoneModel.setName(MEGAPHONE);
					// Set geometry properties
					mMegaphoneModel.setScale(new Vector3d(50f, 50f, 50f));
					mMegaphoneModel.setTranslation(new Vector3d(160, 250, 150));
					mMegaphoneModel.setRotation(new Rotation(0f,
							(float) Math.PI / 2, 0f));
					mMegaphoneModel.setVisible(false);

				} else
					MetaioDebug.log(Log.ERROR, "Error loading geometry: "
							+ megaphoneModelFile);
			}

		} catch (Exception e) {

		}
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		super.onTouch(v, event);
		// mGestureHandler.onTouch(v, event);
		return true;
	}

	@Override
	protected void onGeometryTouched(final IGeometry geometry) {
		final GreenhouseM2MSystem currentSystem = mGreenhousesByCosName
				.get(mActiveCosName);

		if (MEGAPHONE.equals(geometry.getName())) {
			// acknowledge the alerts
			m2mClient.acknowledgeAlert(currentSystem, currentSystem.getAlerts()
					.iterator().next(), new ICallback() {

				@Override
				public void onSuccess() {
					if (currentSystem.getAlerts().isEmpty())
						mMegaphoneModel.setVisible(false);
					mMegaphoneHasRung = false;

				}

				@Override
				public void onError(String errorDetails, Throwable t) {

				}
			});

		}

		if (SWITCH_BUTTON.equals(geometry.getName())) {
			m2mClient.sendData(currentSystem, "toggleRoof",
					"{ \"state\": true}", new ICallback() {
						@Override
						public void onSuccess() {
							mMediaPlayer.start();
							geometry.setRotation(new Rotation(0f, 0f,
									(float) (Math.PI)), true);
						}

						@Override
						public void onError(String errorDetails, Throwable t) {
							Log.d(getPackageName(), errorDetails, t);
						}
					});

			return;
		}

		if (FACEBOOK_BUTTON.equals(geometry.getName())) {
			onClickFacebookShare();
			return;
		}

		Collection<SensorButton> buttons = mButtonsByGreenhouse.get(
				mGreenhousesByCosName.get(mActiveCosName)).values();
		SensorButton button;
		if (geometry.getName().endsWith("-chart")) {
			button = getButtonByName(
					buttons,
					geometry.getName().substring(0,
							geometry.getName().indexOf("-chart")));
			button.direction = SensorButton.DIRECTION.CHART_TO_BUTTON;
			button.initScale = 2f;
			button.targetScale = 0.7f;
			button.timeLinePos = 0;
		} else {
			button = getButtonByName(buttons, geometry.getName());
			onClickSensorButton(button);
			button.timeLinePos = 0;
		}

	}

	private void onClickSensorButton(SensorButton b) {
		b.direction = SensorButton.DIRECTION.BUTTON_TO_CHART;
		b.initScale = 0.7f;
		b.targetScale = 2f;
		b.targetPosition = VECTOR_GREENHOUSE_ZENITH;
	}

	private void onClickFacebookShare() {
		Runnable r = new Runnable() {

			@Override
			public void run() {
				Session session = Session.getActiveSession();

				if (session != null) {
					// Check for publish permissions
					List<String> permissions = session.getPermissions();
					if (!permissions.contains("publish_actions")) {
						Session.NewPermissionsRequest newPermissionsRequest = new Session.NewPermissionsRequest(
								MainActivity.this,
								Arrays.asList("publish_actions"));
						session.requestNewPublishPermissions(newPermissionsRequest);
					}
					// create a greenhouse object
					JSONObject params = new JSONObject();
					// params.putString("type", "checkmyeclo:greenhouse");
					try {
						GreenhouseM2MSystem activeSystem = mGreenhousesByCosName
								.get(mActiveCosName);

						params.put("app_id", "559736607426632");
						params.put("title",
								activeSystem.getSystemDetails().name);
						params.put("image",
								"http://airvantage.github.io/resources/img/tutorials/eclo.png");
						params.put("description", "");

						JSONObject data = new JSONObject();

						DecimalFormat df = new DecimalFormat("###.##",
								new DecimalFormatSymbols(Locale.ENGLISH));

						data.put("temperature",
								df.format(activeSystem.getTemperature()));
						data.put("luminosity",
								df.format(activeSystem.getLuminosity()));
						data.put("humidity",
								df.format(activeSystem.getHumidity()));

						params.put("data", data);

						Bundle objectParams = new Bundle();
						objectParams.putString("object", params.toString());

						Request request = new Request(
								Session.getActiveSession(),
								"me/objects/checkmyeclo:greenhouse",
								objectParams, HttpMethod.POST);

						Response response = request.executeAndWait();
						Log.d(getPackageName(), response.toString());
						String ghID = (String) response.getGraphObject()
								.getProperty("id");

						metaioSDK.requestScreenshot();

						while (screenshot == null) {
							try {
								Thread.sleep(20);
							} catch (InterruptedException e) {
							}
						}

						// Set up image upload request parameters
						Bundle imageParams = new Bundle();
						imageParams.putParcelable("file", screenshot);

						// Set up the image upload request callback
						Request.Callback imageCallback = new Request.Callback() {

							@Override
							public void onCompleted(Response response) {
								// Log any response error
								FacebookRequestError error = response
										.getError();
							}
						};

						Request imageRequest = new Request(
								Session.getActiveSession(),
								"me/staging_resources", imageParams,
								HttpMethod.POST, imageCallback);

						Response imageResponse = imageRequest.executeAndWait();

						Bundle p = new Bundle();
						p.putString("greenhouse", ghID);
						p.putString("image[0][url]", imageResponse
								.getGraphObject().getProperty("uri").toString());
						p.putBoolean("image[0][user_generated]", true);

						request = new Request(Session.getActiveSession(),
								"me/checkmyeclo:check", p, HttpMethod.POST);

						Log.d(getPackageName(), request.toString());
						response = request.executeAndWait();
						System.out.println(response.toString());

						screenshot = null;

					} catch (JSONException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		};

		Thread t = new Thread(r);
		t.start();

	}

	@Override
	protected IMetaioSDKCallback getMetaioSDKCallbackHandler() {
		return mCallbackHandler;
	}

	final class MetaioSDKCallbackHandler extends IMetaioSDKCallback {

		@Override
		public void onSDKReady() {
			// show GUI
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					mGUIView.setVisibility(View.VISIBLE);
				}
			});
		}

		@Override
		public void onTrackingEvent(TrackingValuesVector trackingValues) {
			super.onTrackingEvent(trackingValues);

			if (!trackingValues.isEmpty()) {
				mActiveCosName = trackingValues.get(0).getCosName();
			}
		}

		@Override
		public void onScreenshotImage(ImageStruct image) {
			screenshot = image.getBitmap();
			image.release();
		}

	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if (getResources().getString(R.string.pref_login).equals(key)
				|| getResources().getString(R.string.pref_password).equals(key)) {
			m2mClient = createM2MClient();
		}

		if (key.startsWith("system-COS"))
			initGreenhouses();

	}

	/***** HACK for CAMERA POOR QUALITY ******/

	private Size getOptimalPreviewSize(List<Size> sizes, int w, int h) {
		final double ASPECT_TOLERANCE = 0.05;
		double targetRatio = (double) w / h;
		if (sizes == null)
			return null;

		Size optimalSize = null;
		double minDiff = Double.MAX_VALUE;

		int targetHeight = h;

		// Try to find an size match aspect ratio and size
		for (Size size : sizes) {
			double ratio = (double) size.width / size.height;
			if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
				continue;
			if (Math.abs(size.height - targetHeight) < minDiff) {
				optimalSize = size;
				minDiff = Math.abs(size.height - targetHeight);
			}
		}

		// Cannot find the one match the aspect ratio, ignore the requirement
		if (optimalSize == null) {
			minDiff = Double.MAX_VALUE;
			for (Size size : sizes) {
				if (Math.abs(size.height - targetHeight) < minDiff) {
					optimalSize = size;
					minDiff = Math.abs(size.height - targetHeight);
				}
			}
		}
		return optimalSize;
	}

	@Override
	public void onSurfaceChanged(int width, int height) {
		super.onSurfaceChanged(width, height);
		// if (IMetaioSDKAndroid.getCamera(this) != null) {
		// Parameters p = IMetaioSDKAndroid.getCamera(this).getParameters();
		// List<Size> sizes = p.getSupportedPreviewSizes();
		// Size opt = getOptimalPreviewSize(sizes, width, height);
		// metaioSDK.resizeRenderer(opt.width, opt.height);
		// }
	}

	@Override
	public void valueChanged(IM2MSystem system, String path, String newValue) {
		SensorButton button = mButtonsByGreenhouse.get(system).get(path);

		if (button == null) {
			// this is a value for which we don't have a visual button

			// TODO if path == switch then update the switch asynchronously
			// instead?
			return;
		}

		if (button.texturePath != null) {
			File file = new File(button.texturePath);
			if (file.exists()) {
				// TODO!!!!
				// file.delete();
			}
		}
		button.texturePath = createTexture(button.type, newValue
				+ button.type.unit);
		button.textureNeedsRefresh = true;

		// get historical agg data
		if (button.chartPath != null) {
			File file = new File(button.chartPath);
			if (file.exists()) {
				file.delete();
			}
		}
	}

	@Override
	public void newAlert(IM2MSystem system, IM2MAlert alert) {
		// TODO Auto-generated method stub

	}

	private String createTexture(SENSOR_TYPE type, String value) {
		try {
			long start = System.currentTimeMillis();

			Log.d(getPackageName(), type.toString() + value);

			final String texturepath = getCacheDir() + "/"
					+ (type.toString() + value).hashCode() + ".png";

			if (new File(texturepath).exists())
				return texturepath;

			// TODO garbage collect old images (in a dedicated thread?)

			// Load background image and make a mutable copy
			Bitmap backgroundImage = BitmapFactory.decodeFile(AssetsManager
					.getAssetPath(type.toString().toLowerCase() + ".png"));
			Bitmap image = backgroundImage.copy(Bitmap.Config.ARGB_8888, true);
			backgroundImage.recycle();
			backgroundImage = null;

			Canvas c = new Canvas(image);

			mPaint.setColor(Color.WHITE);
			mPaint.setTextSize(140 / 3);
			// mPaint.setTypeface(Typeface.DEFAULT);
			mPaint.setTypeface(mTypeFace);

			// Draw title string
			if (value != null && value.length() > 0) {
				String n = value.trim();

				final int maxWidth = 10000;

				int i = mPaint.breakText(n, true, maxWidth, null);
				mPaint.setTextAlign(Align.CENTER);

				c.drawText(n.substring(0, i), (int) (c.getWidth() / 2),
						(int) (c.getHeight() * .7), mPaint);

			}

			try {
				FileOutputStream out = new FileOutputStream(texturepath);
				image.compress(Bitmap.CompressFormat.PNG, 90, out);
				image.recycle();
				image = null;
				Log.d(getPackageName(),
						"Time elapsed: " + (System.currentTimeMillis() - start));

				return texturepath;
			} catch (Exception e) {
				MetaioDebug.log("Failed to save texture file");
				e.printStackTrace();
			}

		} catch (Exception e) {
			MetaioDebug.log(Log.ERROR,
					"Error creating billboard texture: " + e.getMessage());
			MetaioDebug.printStackTrace(Log.ERROR, e);
			return null;
		}
		return null;
	}
}
