package net.airvantage.eclo.m2m;

import java.util.List;

/**
 * Allows to update the info of an {@link IM2MSystem} asynchronously
 * 
 */
public interface IM2MClient {

	public interface ICallback {
		void onSuccess();

		void onError(String errorDetails, Throwable t);
	}

	public interface IAuthenticationCallback {
		void onAuthSuccessful();

		void onAuthFailed();
	}

	void auth(IAuthenticationCallback authCallback);

	boolean isAuthentified();

	/**
	 * Explicitly request the SystemDetails of <code>system</code> to be updated
	 * 
	 * @param system
	 *            A system whose uid is not empty
	 * @param callback
	 *            callback to be called on success/failure of the request. Note:
	 *            this only acknowledges that the request was properly processed
	 *            but does not guarantee anything about the actual update
	 */
	void updateSystemDetails(final IM2MSystem system, final ICallback callback);

	/**
	 * Explicitly request some data of a <code>system</code> to be updated
	 * 
	 * @param system
	 *            A system whose uid is not empty
	 * @param paths
	 *            paths to update
	 * @param callback
	 *            callback to be called on success/failure of the request. Note:
	 *            this only acknowledges that the request was properly processed
	 *            but does not guarantee anything about the actual update
	 */
	void updateSystemData(final IM2MSystem system, final String[] paths,
			final ICallback callback);

	/**
	 * Explicitly request historical data of a <code>system</code> to be updated
	 * 
	 * @param system
	 *            A system whose uid is not empty
	 * @param path
	 *            path to update
	 * @param callback
	 *            callback to be called on success/failure of the request. Note:
	 *            this only acknowledges that the request was properly processed
	 *            but does not guarantee anything about the actual update
	 */
	void updateSystemLast24HrsData(final IM2MSystem system, final String path,
			final ICallback callback);

	void sendData(final IM2MSystem system, final String path, String value, final ICallback callback);
	
	
	boolean supportsUnsollicitedResponses();

	/**
	 * An optional list of {@link IM2MSystem}s that are monitored in case the
	 * {@link IM2MClient} receives unsolicited responses relevant to those
	 * 
	 * @see IM2MClient#supportsUnsollicitedResponses()
	 * 
	 * @return
	 */
	List<IM2MSystem> getMonitoredSystems();

}
