package org.reactome.release.common.dataretrieval;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedConstruction.Context;
import org.mockito.MockedConstruction.MockInitializer;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.reactome.release.common.dataretrieval.exceptions.DataRetrievalException;
import org.reactome.release.common.dataretrieval.exceptions.FtpException;
import org.reactome.release.common.dataretrieval.exceptions.RetriesExceeded;

public class TestFileRetrievalExceptions
{

	@Mock
	CloseableHttpClient mockClient;

	@Mock
	CloseableHttpResponse response;

	@Mock
	StatusLine mockStatusLine;

	/**
	 * Catch an IllegalArgumentException if the path to the destination where the file should be downloaded to is null.
	 * @throws URISyntaxException
	 */
	@Test
	public void testMissingDestinationURI() throws URISyntaxException
	{
		FileRetriever retriever = new FileRetriever();
		retriever.setDataURL(new URI("https://www.reactome.org"));
		try
		{
			retriever.fetchData();
		}
		catch (IllegalArgumentException e)
		{
			// an IllegalArgumentException is correct when there is no file destination.
			assertEquals(e.getMessage(), "You must provide a destination to which the file will be downloaded!");
		}
		catch (RetriesExceeded e)
		{
			e.printStackTrace();
			fail();
		}
		catch (FtpException e)
		{
			e.printStackTrace();
			fail();
		}
		catch (IOException e)
		{
			e.printStackTrace();
			fail();
		}
		catch (DataRetrievalException e)
		{
			e.printStackTrace();
			fail();
		}
	}

	/**
	 * Catch an IllegalArgumentException if the URI for the source is null
	 * @throws URISyntaxException
	 */
	@Test
	public void testMissingDownloadURI() throws URISyntaxException
	{
		FileRetriever retriever = new FileRetriever();
		retriever.setFetchDestination("/tmp/test");
		try
		{
			retriever.fetchData();
		}
		catch (IllegalArgumentException e)
		{
			// an IllegalArgumentException is correct when there is no file destination.
			assertEquals(e.getMessage(), "You must provide a URI from which the file will be downloaded!");
		}
		catch (DataRetrievalException | IOException e)
		{
			e.printStackTrace();
			fail();
		}
	}

	@Before
	public void setUpMocksForException() throws IOException
	{
		MockitoAnnotations.openMocks(this);
	}

	/**
	 * Catch a RetriesExceeded if the maximum number of retries is exceeded.
	 * @throws URISyntaxException
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	@Test
	public void testRetriesExceeded() throws URISyntaxException, ClientProtocolException, IOException
	{
		FileRetriever retriever = new FileRetriever();
		retriever.setFetchDestination("/tmp/test");
		retriever.setDataURL(new URI("https://www.reactome.org/asdfasdfasdfasdfasfasdf"));
		retriever.setNumRetries(1);
		retriever.setMaxAge(Duration.ofSeconds(0));
		retriever.setTimeout(Duration.ofNanos(1));
		try(MockedStatic<HttpClients> mockedStaticClient = Mockito.mockStatic(HttpClients.class); )
		{
			Mockito.when(HttpClients.createDefault()).thenReturn(mockClient);
			Mockito.when(mockClient.execute(any(HttpUriRequest.class), any(HttpContext.class))).thenThrow(new ConnectTimeoutException("TEST"));

			retriever.fetchData();
		}
		catch (RetriesExceeded e)
		{
			assertTrue(e.getMessage().contains("Connection timed out. Number of retries (1) exceeded"));
		}
		catch (IOException | DataRetrievalException e)
		{
			e.printStackTrace();
			fail();
		}
	}

	/**
	 * Catch a general FTP error.
	 * @throws URISyntaxException
	 */
	@Test
	public void testFtpException() throws URISyntaxException
	{
		FileRetriever retriever = new FileRetriever();
		retriever.setFetchDestination("/tmp/test");
		retriever.setDataURL(new URI("ftp://www.reactome.org/asdfasdfasdfasdfasfasdf"));
		retriever.setNumRetries(1);
		retriever.setMaxAge(Duration.ofSeconds(0));
		retriever.setTimeout(Duration.ofNanos(1));
		//set up the mock client
		MockInitializer<FTPClient> mockInitializer = new MockInitializer<FTPClient>()
		{
			@Override
			public void prepare(FTPClient localMockFTPClient, Context context) throws Throwable {
				Mockito.doNothing().when(localMockFTPClient).connect(anyString());
				Mockito.when(localMockFTPClient.login(anyString(),anyString())).thenReturn(true);
				Mockito.when(localMockFTPClient.getReplyCode()).thenReturn(500);
				Mockito.when(localMockFTPClient.getReplyString()).thenReturn("500 ERROR");
				InputStream inStream = new ByteArrayInputStream("this is a test".getBytes());
				Mockito.when(localMockFTPClient.retrieveFileStream(anyString())).thenReturn(inStream);
			}
		};
		try(MockedConstruction<FTPClient> mockedConstruction = Mockito.mockConstruction(FTPClient.class, mockInitializer) )
		{
			retriever.fetchData();
		}
		catch (FtpException e)
		{
			assertTrue(e.getMessage().contains("5xx reply code detected (500), reply string is: 500 ERROR"));
		}
		catch (IOException | DataRetrievalException e)
		{
			e.printStackTrace();
			fail();
		}
	}
}
