package org.reactome.util.ensembl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This is used to process web service responses from EnsEMBL. This code was copied from AddLinks,
 * org.reactome.addlinks.dataretrieval.ensembl.EnsemblServiceResponseProcessor
 * @author sshorser
 */
public final class EnsemblServiceResponseProcessor
{
	public static final int MAX_TIMES_TO_WAIT = 5;
	// Assume a quota of 10 to start. This will get set properly with every response from the service.
	private static final AtomicInteger numRequestsRemaining = new AtomicInteger(10);

	private Logger logger;
	private int waitMultiplier = 1;
	// This can't be static because each request could have a different timeoutRetries counter.
	private int timeoutRetriesRemaining;

	/**
	 * Constructs a new EnsemblServiceResponseProcessor object with the specified logger
	 * to record information about responses from the EnsEMBL service
	 * @param logger Logger object to record information about processed responses
	 */
	public EnsemblServiceResponseProcessor(Logger logger)
	{
		this.logger =
			logger != null ?
			logger :
			LogManager.getLogger();

		initializeTimeoutRetriesRemaining();
	}

	/**
	 * Constructs a new EnsemblServiceResponseProcessor object with a default logger
	 * to record information about responses from the EnsEMBL service
	 */
	public EnsemblServiceResponseProcessor()
	{
		this(null);
	}

	/**
	 * Processes the HttpResponse from the queried EnsEMBL service, logs relevant information for the end-user, and
	 * returns the important information of the response as an EnsemblServiceResult object
	 * @param urlConnection URLConnection from the EnsEMBL service
	 * @return EnsemblServiceResult object containing the relevant information from the response
	 * @throws IOException
	 */
	public EnsemblServiceResult processResponse(HttpURLConnection urlConnection) throws IOException {
		EnsemblServiceResult result = urlConnection.getHeaderFields().get("Retry-After") != null ?
			processResponseWithRetryAfter(urlConnection) :
			processResponseWhenNotOverQueryQuota(urlConnection);
		processXRateLimitRemaining(urlConnection);

		return result;
	}

	/**
	 * Returns the number of requests that may still be made to the EnsEMBL service in the current time window
	 * @return Number of requests that can still be made until the the time window is refreshed
	 */
	public static int getNumRequestsRemaining()
	{
		return EnsemblServiceResponseProcessor.numRequestsRemaining.get();
	}

	/**
	 * Returns the factor by which the response's recommended wait time is multiplied.  The value starts at 1 and
	 * is incremented each time the response to the request is to wait.  This gives the server some buffer time before
	 * the request is retried.
	 * @return Factor by which to multiply the server's response recommended wait time
	 */
	public int getWaitMultiplier()
	{
		return this.waitMultiplier;
	}

	/**
	 * Increase the factor by which the response's recommended wait time it multiplied by one.
	 */
	private void incrementWaitMultiplier()
	{
		this.waitMultiplier++;
	}

	/**
	 * Returns the number of request retries remaining when the server's response is a gateway timeout (status code
	 * 504)
	 * @return Number of retries remaining
	 */
	public int getTimeoutRetriesRemaining()
	{
		return this.timeoutRetriesRemaining;
	}

	/**
	 * Sets/resets the starting number of request retries permitted when the server's response is a gateway timeout
	 * (status code 504)
	 */
	private void initializeTimeoutRetriesRemaining()
	{
		this.timeoutRetriesRemaining = 3;
	}

	/**
	 * Process the query's URLConnection object when the response contains the header "Retry-After" which is most likely
	 * to happen if too many requests are sent in a fixed time, using up our quota with the service resulting in a
	 * need to wait before more requests can be sent.
	 *
	 * This method will create and return an EnsemblServiceResult object containing the status code of the response,
	 * if it is okay to retry the request {@link #timesWaitedThresholdNotMet()}, and the duration of time to wait
	 * before retrying.
	 *
	 * The response's status message, reason phrase, and headers will also be logged for debugging.
	 * @param urlConnection urlConnection URLConnection from the EnsEMBL service
	 * @return EnsemblServiceResult object with the response status, isOkToRetry, and wait time to retry set
	 */
	EnsemblServiceResult processResponseWithRetryAfter(HttpURLConnection urlConnection) throws IOException {
		logger.debug("Response message: {} ; Headers: {}",
			urlConnection.getResponseMessage(),
			getHeaders(urlConnection)
		);

		EnsemblServiceResult result = this.new EnsemblServiceResult();
		result.setStatus(urlConnection.getResponseCode());
		result.setWaitTime(processWaitTime(urlConnection));
		result.setOkToRetry(timesWaitedThresholdNotMet());

		if (result.isOkToRetry())
		{
			incrementWaitMultiplier();
		}

		return result;
	}

	/**
	 * Processes the query's URLConnection object when the response does not contain the header "Retry-After".  This
	 * method will attempt to retrieve the content, and return it as the result value in an EnsemblServiceResult
	 * object.
	 *
	 * Certain HTTP response error codes (e.g. 400, 404, 500, 504) will cause the error to be logged and a
	 * EnsemblServiceResult object without content set in the result value to be returned.
	 * @param urlConnection HttpURLConnection from the EnsEMBL service
	 * @return EnsemblServiceResult object with the content set as its result value if the content was obtained
	 */
	EnsemblServiceResult processResponseWhenNotOverQueryQuota(HttpURLConnection urlConnection) throws IOException {
		EnsemblServiceResult result = this.new EnsemblServiceResult();
		result.setStatus(urlConnection.getResponseCode());
		switch (urlConnection.getResponseCode())
		{
			case HttpURLConnection.HTTP_GATEWAY_TIMEOUT:
				logger.error("Request timed out! {} retries remaining", timeoutRetriesRemaining);

				timeoutRetriesRemaining--;
				if (timeoutRetriesRemaining > 0)
				{
					result.setOkToRetry(true);
				}
				else
				{
					logger.error("No more retries remaining.");
					initializeTimeoutRetriesRemaining();
				}
				break;
			case HttpURLConnection.HTTP_OK:
				result.setResult(parseContent(urlConnection));
				break;
			case HttpURLConnection.HTTP_NOT_FOUND:
				logger.error("Response code 404 ('Not found') received: {}",
					urlConnection.getResponseMessage()
				);
				// If we got 404, don't retry.
				break;
			case HttpURLConnection.HTTP_INTERNAL_ERROR:
				logger.error("Error 500 detected! Message: {}", urlConnection.getResponseMessage());
				// If we get 500 error then we should just get  out of here. Maybe throw an exception?
				break;
			case HttpURLConnection.HTTP_BAD_REQUEST:
				logger.trace("Response code was 400 ('Bad request'). Message from server: {}", parseContent(urlConnection));
				break;
			default:
				// Log any other kind of response.
				result.setResult(parseContent(urlConnection));
				logger.info("Unexpected response: {}",
					urlConnection.getResponseMessage()
				);
				break;
		}

		return result;
	}

	/**
	 * Sets the number of requests remaining the server will permit based on the response header
	 * "X-RateLimit-Remaining".
	 *
	 * The number of requests remaining will be logged for debugging if it is a multiple of 1000.  If no
	 * "X-RateLimit-Remaining" header is found in the URLConnection, its absence will be logged along with
	 * the HTTP response code received, the response headers received, and the last known number of requests
	 * remaining.
	 * @param urlConnection URLConnection from the EnsEMBL service
	 */
	void processXRateLimitRemaining(HttpURLConnection urlConnection) throws IOException {
		if (urlConnection.getHeaderFields().get("X-RateLimit-Remaining") != null)
		{
			EnsemblServiceResponseProcessor.numRequestsRemaining.set(
				parseIntegerHeaderValue(urlConnection, "X-RateLimit-Remaining")
			);
			if (EnsemblServiceResponseProcessor.numRequestsRemaining.get() % 1000 == 0)
			{
				logger.debug("{} requests remaining", numRequestsRemaining.get());
			}
		}
		else
		{
			logger.warn(
				"No X-RateLimit-Remaining was returned. This is odd. Response message: {} ; "+
					"Headers returned are: {} " + System.lineSeparator() +
					"Last known value for remaining was {}",

				urlConnection.getResponseMessage(),
				getHeaders(urlConnection),
				EnsemblServiceResponseProcessor.numRequestsRemaining
			);
		}
	}

	/**
	 * Returns the duration to wait before retrying the query.  The wait time is determined from the "Retry-After"
	 * header in the query response and is multiplied by the number of times the query has been requested to wait.
	 * This is to give the increasing server buffer time on each failure requesting the query waits.
	 * @param urlConnection URLConnection from the EnsEMBL service
	 * @return Wait time as Duration object ("Retry-After" value multiplied by the number of times the query has
	 * requested to wait).
	 */
	private Duration processWaitTime(HttpURLConnection urlConnection)
	{
		Duration waitTime = Duration.ofSeconds(parseIntegerHeaderValue(urlConnection,"Retry-After"));

		logger.warn("The server told us to wait, so we will wait for {} * {} before trying again.",
			waitTime, getWaitMultiplier()
		);

		return waitTime.multipliedBy(getWaitMultiplier());
	}

	/**
	 * Returns <code>true</code> if the number of times the query the object for this class has been told to wait is
	 * less than the threshold (set to a maximum of 5); <code>false</code> is returned otherwise and the maximum
	 * number of times waiting being reached is logged.
	 *
	 * @return <code>true</code> if the number of times query has been told to wait is less than the threshold;
	 * <code>false</code> otherwise.
	 */
	private boolean timesWaitedThresholdNotMet()
	{
		// If we get told to wait >= 5 times, let's just take the hint and stop trying.
		if (getWaitMultiplier() >= MAX_TIMES_TO_WAIT)
		{
			logger.error(
				"I've already waited {} times and I'm STILL getting told to wait. This will be the LAST attempt.",
				this.waitMultiplier
			);

			return false;
		}

		return true;
	}

	/**
	 * Parses and returns the content from the URLConnection object passed.  Content is parsed as UTF-8.  A stacktrace
	 * is printed to STDERR and an empty String returned if an exception occurs during parsing of the response content.
	 * @param urlConnection URLConnection from the EnsEMBL service
	 * @return Content as String from the URLConnection object passed.  An empty String if an exception occurs during
	 * parsing of the content.
	 */
	private String parseContent(HttpURLConnection urlConnection)
	{
		try
		{
			return new BufferedReader(new InputStreamReader(urlConnection.getInputStream(), StandardCharsets.UTF_8))
				.lines()
				.collect(Collectors.joining(System.lineSeparator()));
		}
		catch (IOException e)
		{
			e.printStackTrace();
			return "";
		}
	}

	/**
	 * Returns the value of a header, which is an integer, as an integer.
	 * @param urlConnection URLConnection from the EnsEMBL service
	 * @param header Specific header for which to get the value (which should be an integer)
	 * @return Integer value of the header passed for the URLConnection object passed
	 */
	private static int parseIntegerHeaderValue(URLConnection urlConnection, String header) throws NumberFormatException
	{
		return Integer.parseInt(urlConnection.getHeaderFields().get(header).get(0));
	}

	/**
	 * Returns the headers of the URLConnection object as a list of Strings
	 * @param urlConnection URLConnection from the EnsEMBL service
	 * @return List of Strings representing the headers from the passed URLConnection object
	 */
	private List<String> getHeaders(URLConnection urlConnection)
	{
		List<String> headerNamesAndValues = new ArrayList<>();
		for (String headerName : urlConnection.getHeaderFields().keySet()) {
			String headerValues = urlConnection.getHeaderFields().get(headerName).stream().collect(Collectors.joining(", "));
			headerNamesAndValues.add(headerName + ": " + headerValues);
		}
		return headerNamesAndValues;
	}

	/**
	 * Contains the relevant information of the HTTP response from a query to EnsEMBL's service (e.g. status of the
	 * response, if the request should be retried, time to wait before retrying, content of the response if successful)
	 */
	public class EnsemblServiceResult
	{
		private Duration waitTime;
		private String result;
		private boolean okToRetry = false;
		private int status;

		/**
		 * Retrieves the Duration object describing the amount of time to wait before retrying the request to the
		 * EnsEMBL service
		 * @return Time to wait before retrying request (as a Duration object)
		 */
		public Duration getWaitTime()
		{
			if (this.waitTime == null)
			{
				return Duration.ZERO;
			}

			return this.waitTime;
		}

		/**
		 * Sets the duration object describing the amount of time to wait before retrying the request to the EnsEMBL
		 * service
		 * @param waitTime Wait time as Duration object
		 */
		public void setWaitTime(Duration waitTime)
		{
			this.waitTime = waitTime;
		}

		/**
		 * Retrieves the content of the response from the EnsEMBL service
		 * @return Content of the response as a String (empty String if no content)
		 */
		public String getResult()
		{
			if (this.result == null)
			{
				return StringUtils.EMPTY;
			}

			return this.result;
		}

		/**
		 * Sets the content of the response from the EnsEMBL service
		 * @param result Content of the response as a String (empty String if no content)
		 */
		public void setResult(String result)
		{
			this.result = result;
		}

		/**
		 * Retrieves if it is permitted to retry the request to the EnsEMBL service
		 * @return true if okay to retry the request; false otherwise
		 */
		public boolean isOkToRetry()
		{
			return this.okToRetry;
		}

		/**
		 * Sets if it is permitted to retry to the request to the EnsEMBL service
		 * @param okToRetry true if okay to retry the request; false otherwise
		 */
		public void setOkToRetry(boolean okToRetry)
		{
			this.okToRetry = okToRetry;
		}

		/**
		 * Retrieves the <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Status">HTTP status code</a> of
		 * the response from the EnsEMBL service
		 * @return Response status code from the EnsEMBL service
		 */
		public int getStatus()
		{
			return this.status;
		}

		/**
		 * Sets the <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Status">HTTP status code</a> of
		 * the response from the EnsEMBL service
		 * @param status Response status code from the EnsEMBL service
		 */
		public void setStatus(int status)
		{
			this.status = status;
		}
	}
}