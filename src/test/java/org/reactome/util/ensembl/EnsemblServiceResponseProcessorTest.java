package org.reactome.util.ensembl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.reactome.util.ensembl.EnsemblServiceResponseProcessor.MAX_TIMES_TO_WAIT;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.reactome.util.ensembl.EnsemblServiceResponseProcessor.EnsemblServiceResult;

@TestInstance(Lifecycle.PER_CLASS)
public class EnsemblServiceResponseProcessorTest {
	private final int TOO_MANY_REQUESTS_STATUS_CODE = 429;
	private final String TOO_MANY_REQUESTS_REASON_PHRASE = "Too Many Requests";
	private final int RETRY_AFTER_VALUE = 5;
	private final int X_RATE_LIMIT_REMAINING_VALUE = 123;
	private final String DUMMY_RESPONSE_CONTENT = "Dummy Content";

	@Mock
	private HttpURLConnection urlConnection;

	@Mock
	private Logger logger;

	private EnsemblServiceResponseProcessor ensemblServiceResponseProcessor;

	@BeforeAll
	public void setUp() {
		MockitoAnnotations.initMocks(this);
	}

	@BeforeEach
	public void createEnsemblServiceResponseProcessor() {
		ensemblServiceResponseProcessor = new EnsemblServiceResponseProcessor(logger);
	}

	@Test
	public void correctEnsemblServiceResultAfterSingleResponseWithRetryAfter() throws IOException {
		mockResponseWithRetryHeader(TOO_MANY_REQUESTS_STATUS_CODE, TOO_MANY_REQUESTS_REASON_PHRASE);

		EnsemblServiceResult result = ensemblServiceResponseProcessor.processResponseWithRetryAfter(urlConnection);

		assertThat("Incorrect status code", result.getStatus(), is(equalTo(TOO_MANY_REQUESTS_STATUS_CODE)));
		assertThat("isOkayToRetry is false", result.isOkToRetry(), is(true));
		assertThat("Wait time is unexpected",
			result.getWaitTime(), is(equalTo(Duration.ofSeconds(RETRY_AFTER_VALUE)))
		);
	}

	@Test
	public void correctEnsemblServiceResultAfterMaximumNumberOfResponsesWithRetryAfter() throws IOException {
		mockResponseWithRetryHeader(TOO_MANY_REQUESTS_STATUS_CODE, TOO_MANY_REQUESTS_REASON_PHRASE);

		// First call the method up to the maximum number of times
		for (int i = 1; i < MAX_TIMES_TO_WAIT; i++) {
			ensemblServiceResponseProcessor.processResponseWithRetryAfter(urlConnection);
		}

		final int expectedMultiplierAfterMaximumNumberOfResponsesWithRetry = MAX_TIMES_TO_WAIT;
		final int expectedWaitTime = RETRY_AFTER_VALUE * expectedMultiplierAfterMaximumNumberOfResponsesWithRetry;

		EnsemblServiceResult result = ensemblServiceResponseProcessor.processResponseWithRetryAfter(urlConnection);
		assertThat("Incorrect status code", result.getStatus(), is(equalTo(TOO_MANY_REQUESTS_STATUS_CODE)));
		assertThat("isOkayToRetry is true", result.isOkToRetry(), is(false));
		assertThat("Wait time is unexpected", result.getWaitTime(),
			is(equalTo(Duration.ofSeconds(expectedWaitTime))));
	}

	@Test
	public void correctEnsemblServiceResultAfterOkayResponse() throws IOException {
		final int okayStatusCode = HttpURLConnection.HTTP_OK;
		final String okayReasonPhrase = "OK";

		mockResponse(okayStatusCode, okayReasonPhrase);
		mockResponseEntityWithContent();

		EnsemblServiceResult result = ensemblServiceResponseProcessor.processResponseWhenNotOverQueryQuota(urlConnection);

		assertThat("Incorrect status code", result.getStatus(), is(equalTo(okayStatusCode)));
		assertThat("Result content is unexpected", result.getResult(), is(equalTo(DUMMY_RESPONSE_CONTENT)));
		assertThat("isOkayToRetry is true", result.isOkToRetry(), is(equalTo(false)));
		assertThat("Wait time is not zero", result.getWaitTime(), is(equalTo(Duration.ZERO)));
	}

	@Test
	public void correctEnsemblServiceResultAfterGatewayTimeoutResponse() throws IOException {
		final int gatewayTimeoutStatusCode = HttpURLConnection.HTTP_GATEWAY_TIMEOUT;
		final String gatewayTimeoutReasonPhrase = "Gateway Time-out";

		mockResponse(gatewayTimeoutStatusCode, gatewayTimeoutReasonPhrase);

		EnsemblServiceResult result = ensemblServiceResponseProcessor.processResponseWhenNotOverQueryQuota(urlConnection);

		assertThat("Incorrect status code", result.getStatus(), is(equalTo(gatewayTimeoutStatusCode)));
		assertThat("Non-empty result", result.getResult(), is(equalTo(StringUtils.EMPTY)));
		assertThat("isOkayToRetry is false", result.isOkToRetry(), is(equalTo(true)));
		assertThat("Wait time is not zero", result.getWaitTime(), is(equalTo(Duration.ZERO)));
	}

	@Test
	public void correctEnsemblServiceResultAfterGatewayTimeoutResponseAndMaximumTimeoutRetriesAttempted() throws IOException {
		final int gatewayTimeoutStatusCode = HttpURLConnection.HTTP_GATEWAY_TIMEOUT;
		final String gatewayTimeoutReasonPhrase = "Gateway Time-out";
		final int allowedTimeoutRetries = ensemblServiceResponseProcessor.getTimeoutRetriesRemaining();

		mockResponse(gatewayTimeoutStatusCode, gatewayTimeoutReasonPhrase);

		// Use all allowed timeout retries
		for (int i = 1; i < allowedTimeoutRetries; i++) {
			ensemblServiceResponseProcessor.processResponseWhenNotOverQueryQuota(urlConnection);
		}

		EnsemblServiceResult result = ensemblServiceResponseProcessor.processResponseWhenNotOverQueryQuota(urlConnection);
		assertThat("Incorrect status code", result.getStatus(), is(equalTo(gatewayTimeoutStatusCode)));
		assertThat("Non-empty result", result.getResult(), is(equalTo(StringUtils.EMPTY)));
		assertThat("isOkayToRetry is true", result.isOkToRetry(), is(equalTo(false)));
		assertThat("Wait time is not zero", result.getWaitTime(), is(equalTo(Duration.ZERO)));

		assertThat("Timeout retries not reset",
			ensemblServiceResponseProcessor.getTimeoutRetriesRemaining(), is(equalTo(allowedTimeoutRetries)));
	}

	@Test
	public void correctEnsemblServiceResultAfterNotFoundResponse() throws IOException {
		final int notFoundStatusCode = HttpURLConnection.HTTP_NOT_FOUND;
		final String notFoundReasonPhrase = "Not Found";

		mockResponse(notFoundStatusCode, notFoundReasonPhrase);

		EnsemblServiceResult result = ensemblServiceResponseProcessor.processResponseWhenNotOverQueryQuota(urlConnection);

		assertThat("Incorrect status code", result.getStatus(), is(equalTo(notFoundStatusCode)));
		assertThat("Non-empty result", result.getResult(), is(equalTo(StringUtils.EMPTY)));
		assertThat("isOkayToRetry is true", result.isOkToRetry(), is(equalTo(false)));
		assertThat("Wait time is not zero", result.getWaitTime(), is(equalTo(Duration.ZERO)));
	}

	@Test
	public void correctEnsemblServiceResultAfterInternalServerErrorResponse() throws IOException {
		final int internalServerErrorStatusCode = HttpURLConnection.HTTP_INTERNAL_ERROR;
		final String internalServerErrorReasonPhrase = "Internal Server Error";

		mockResponse(internalServerErrorStatusCode, internalServerErrorReasonPhrase);

		EnsemblServiceResult result = ensemblServiceResponseProcessor.processResponseWhenNotOverQueryQuota(urlConnection);

		assertThat("Incorrect status code", result.getStatus(), is(equalTo(internalServerErrorStatusCode)));
		assertThat("Non-empty result", result.getResult(), is(equalTo(StringUtils.EMPTY)));
		assertThat("isOkayToRetry is true", result.isOkToRetry(), is(equalTo(false)));
		assertThat("Wait time is not zero", result.getWaitTime(), is(equalTo(Duration.ZERO)));
	}

	@Test
	public void correctEnsemblServiceResultAfterBadRequestResponse() throws IOException {
		final int badRequestStatusCode = HttpURLConnection.HTTP_BAD_REQUEST;
		final String badRequestReasonPhrase = "Bad Request";

		mockResponse(badRequestStatusCode, badRequestReasonPhrase);
		mockResponseEntityWithContent();

		EnsemblServiceResult result = ensemblServiceResponseProcessor.processResponseWhenNotOverQueryQuota(urlConnection);

		assertThat("Incorrect status code", result.getStatus(), is(equalTo(badRequestStatusCode)));
		assertThat("Non-empty result", result.getResult(), is(equalTo(StringUtils.EMPTY)));
		assertThat("isOkayToRetry is true", result.isOkToRetry(), is(equalTo(false)));
		assertThat("Wait time is not zero", result.getWaitTime(), is(equalTo(Duration.ZERO)));
	}

	@Test
	public void correctEnsemblServiceResultAfterUnexpectedResponse() throws IOException {
		final int movedPermanentlyStatusCode = HttpURLConnection.HTTP_MOVED_PERM;
		final String movedPermanentlyReasonPhrase = "Moved Permanently";

		mockResponse(movedPermanentlyStatusCode, movedPermanentlyReasonPhrase);
		mockResponseEntityWithContent();

		EnsemblServiceResult result = ensemblServiceResponseProcessor.processResponseWhenNotOverQueryQuota(urlConnection);

		assertThat("Incorrect status code", result.getStatus(), is(equalTo(movedPermanentlyStatusCode)));
		assertThat("Result content is unexpected", result.getResult(), is(equalTo(DUMMY_RESPONSE_CONTENT)));
		assertThat("isOkayToRetry is true", result.isOkToRetry(), is(equalTo(false)));
		assertThat("Wait time is not zero", result.getWaitTime(), is(equalTo(Duration.ZERO)));
	}

	@Test
	public void correctNumberOfRequestsRemainingSetAfterResponseWithXRateLimit() throws IOException {
		final int okayStatusCode = HttpURLConnection.HTTP_OK;
		final String okayReasonPhrase = "OK";

		mockResponse(okayStatusCode, okayReasonPhrase);
		mockXRateLimitHeader();

		ensemblServiceResponseProcessor.processXRateLimitRemaining(urlConnection);

		assertThat(
			EnsemblServiceResponseProcessor.getNumRequestsRemaining(), is(equalTo(X_RATE_LIMIT_REMAINING_VALUE))
		);
	}

	private void mockResponseWithRetryHeader(int statusCode, String reasonPhrase) throws IOException {
		mockResponse(statusCode, reasonPhrase);
		mockRetryHeader();
	}

	private void mockResponse(int statusCode, String reasonPhrase) throws IOException {
		Mockito.when(urlConnection.getResponseMessage()).thenReturn("HTTP/1.0 " + statusCode + " " + reasonPhrase);
		Mockito.when(urlConnection.getResponseCode()).thenReturn(statusCode);
	}

	private void mockRetryHeader() {
		final String retryAfterHeaderName = "Retry-After";
		final String retryAfterHeaderValue = Integer.toString(RETRY_AFTER_VALUE);
		Map<String, List<String>> headers = new HashMap<>();
		headers.put(retryAfterHeaderName, Collections.singletonList(retryAfterHeaderValue));

//		final Header[] headers = new Header[] {new BasicHeader(retryAfterHeaderName, retryAfterHeaderValue)};
		Mockito.when(urlConnection.getHeaderFields()).thenReturn(headers);
	}

	private void mockXRateLimitHeader() {
		final String xRateLimitHeaderName = "X-RateLimit-Remaining";
		final String xRateLimitHeaderValue = Integer.toString(X_RATE_LIMIT_REMAINING_VALUE);
		//final Header[] headers = new Header[] {new BasicHeader(xRateLimitHeaderName, xRateLimitHeaderValue)};

		Map<String, List<String>> headers = new HashMap<>();
		headers.put(xRateLimitHeaderName, Collections.singletonList(xRateLimitHeaderValue));

		Mockito.when(urlConnection.getHeaderFields()).thenReturn(headers);
	}

	private void mockResponseEntityWithContent() throws IOException {
		final InputStream content = new ByteArrayInputStream(DUMMY_RESPONSE_CONTENT.getBytes());

		Mockito.when(urlConnection.getInputStream()).thenReturn(content);
		Mockito.when(urlConnection.getContent()).thenReturn(content);
		Mockito.when(urlConnection.getContentLength()).thenReturn(DUMMY_RESPONSE_CONTENT.getBytes().length);
	}
}