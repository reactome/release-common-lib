/**
 * 
 */
package org.reactome.release.common.dataretrieval;

import static org.junit.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Random;

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
import org.junit.Before;
import org.junit.Test;

import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedConstruction.Context;
import org.mockito.MockedConstruction.MockInitializer;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * @author sshorser
 *
 */
public class TestFileRetrieval {

	private static final String MESSAGE_CONTENT = "this is a test";
	
	@Mock
	URI mockUri;
	
	@Mock
	CloseableHttpClient mockClient;
	
	@Mock
	CloseableHttpResponse mockResponse;

	@Mock
	StatusLine mockStatusLine;
	
	HttpEntity entity = new ByteArrayEntity(MESSAGE_CONTENT.getBytes());
	
	@Before
	public void setup()
	{
		MockitoAnnotations.openMocks(this);
	}
	
	/**
	 * Test method for {@link org.reactome.addlinks.dataretrieval.FileRetriever#fetchData()}.
	 * @throws Exception 
	 */
	@Test
	public void testFetchData() throws Exception
	{
		try(MockedStatic<HttpClients> mockedStatic = Mockito.mockStatic(HttpClients.class);)
		{
			DataRetriever retriever = new FileRetriever();
			//retrieve google - it should be pretty easy.
			URI uri = new URI("http://www.google.com");
			retriever.setDataURL(uri);
			
			Mockito.when(mockResponse.getEntity()).thenReturn(entity);
			Mockito.when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);
			Mockito.when(mockClient.execute(any(HttpUriRequest.class))).thenReturn(mockResponse);
			Mockito.when(mockClient.execute(any(HttpUriRequest.class), (HttpContext) any(HttpContext.class))).thenReturn(mockResponse);
			
			Mockito.when(HttpClients.createDefault()).thenReturn(mockClient);
			
			String dest = "/tmp/testFetchData_"+ String.valueOf((new Random()).nextInt(Integer.MAX_VALUE));
			retriever.setFetchDestination(dest);
			Duration age = Duration.of(5, ChronoUnit.SECONDS);
			retriever.setMaxAge(age);
			
			retriever.fetchData();
			assertTrue(Files.exists(Paths.get(dest)));
			
			//Sleep for 6 seconds, and then re-download because the file is stale (MAX AGE was 5 seconds).
			Thread.sleep(Duration.of(6, ChronoUnit.SECONDS).toMillis());
			retriever.fetchData();
			assertTrue(Files.exists(Paths.get(dest)));
			//now set a longer maxAge.
			age = Duration.of(100, ChronoUnit.SECONDS);
			retriever.setMaxAge(age);
			// this time, the file will not be stale (because maxAge is larger) so nothing will be downloaded.
			retriever.fetchData();
			//check that the file exists.
			assertTrue(Files.exists(Paths.get(dest)));
		}
	}
	
	/**
	 * Test retrieving FTP data.
	 * @throws Exception
	 */
	@Test
	public void testFetchFTPData() throws Exception
	{
		//this Mock Initializer sets up the mock client
		MockInitializer<FTPClient> mockInitializer = new MockInitializer<FTPClient>()
		{
			@Override
			public void prepare(FTPClient localMockFTPClient, Context context) throws Throwable {
				Mockito.doNothing().when(localMockFTPClient).connect(anyString());
				Mockito.when(localMockFTPClient.login(anyString(),anyString())).thenReturn(true);
				Mockito.when(localMockFTPClient.getReplyCode()).thenReturn(220);
				Mockito.when(localMockFTPClient.getReplyString()).thenReturn("220 reply string");
				InputStream inStream = new ByteArrayInputStream("this is a test".getBytes());
				Mockito.when(localMockFTPClient.retrieveFileStream(anyString())).thenReturn(inStream);
			}
		};
		try (MockedConstruction<FTPClient> mockedFTPClient = Mockito.mockConstruction(FTPClient.class, mockInitializer );
			MockedStatic<URI> mockedStatic = Mockito.mockStatic(URI.class);)
		{
			DataRetriever retriever = new FileRetriever();
			
			URI mockUri = Mockito.mock(URI.class);
			
			Mockito.doReturn("ftp").when(mockUri).getScheme();
			Mockito.when(mockUri.getHost()).thenReturn("testhost");
			Mockito.doReturn("/some/path").when(mockUri).getPath();
			
			retriever.setDataURL(mockUri);
			String dest = "/tmp/testFetchData_"+ String.valueOf((new Random()).nextInt(Integer.MAX_VALUE));
			retriever.setFetchDestination(dest);
			Duration age = Duration.of(1,ChronoUnit.SECONDS);
			retriever.setMaxAge(age);
			
			retriever.fetchData();
			assertTrue(Files.exists(Paths.get(dest)));
		}
	}
	
	/**
	 * Test FTP Error handling.
	 * @throws Exception
	 */
	@Test
	public void testFetchFTPErr() throws Exception
	{
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
		try(MockedConstruction<FTPClient> mockedConstruction = Mockito.mockConstruction(FTPClient.class, mockInitializer);
			MockedStatic<URI> mockedStatic = Mockito.mockStatic(URI.class);)
		{
			DataRetriever retriever = new FileRetriever();
			
			Mockito.doReturn("ftp").when(mockUri).getScheme();
			Mockito.when(mockUri.getHost()).thenReturn("testhost");
			Mockito.doReturn("/some/path").when(mockUri).getPath();
			
			retriever.setDataURL(mockUri);
			String dest = "/tmp/testFetchData_"+ String.valueOf((new Random()).nextInt(Integer.MAX_VALUE));
			retriever.setFetchDestination(dest);
			Duration age = Duration.of(1,ChronoUnit.SECONDS);
			retriever.setMaxAge(age);
			
			try
			{
				retriever.fetchData();
			}
			catch (Exception e)
			{
				e.printStackTrace();
				assertTrue(e.getMessage().contains("500 ERROR"));
			}
		}
	}
	
	/**
	 * Test HTTP Error handling
	 * @throws ClientProtocolException
	 * @throws IOException
	 * @throws Exception
	 */
	@Test
	public void testHttpErr() throws ClientProtocolException, IOException, Exception
	{
		try(MockedStatic<HttpClients> mockedStatic = Mockito.mockStatic(HttpClients.class);)
		{
			Mockito.when(mockClient.execute(any(HttpUriRequest.class), any(HttpContext.class))).thenThrow(new ClientProtocolException("MOCK Generic Error"));
			
			Mockito.when(HttpClients.createDefault()).thenReturn(mockClient);
			
			DataRetriever retriever = new FileRetriever();
			//retrieve google - it should be pretty easy.
			URI uri = new URI("http://www.google.com");
			retriever.setDataURL(uri);
			String dest = "/tmp/testFetchData_"+ String.valueOf((new Random()).nextInt(Integer.MAX_VALUE));
			retriever.setFetchDestination(dest);
			Duration age = Duration.of(1,ChronoUnit.SECONDS);
			retriever.setMaxAge(age);
			((FileRetriever)retriever).setNumRetries(0);
			((FileRetriever)retriever).setTimeout(Duration.of(1, ChronoUnit.SECONDS));
			try
			{
				retriever.fetchData();
			}
			catch (Exception e)
			{
				e.printStackTrace();
				assertTrue(e.getMessage().contains("MOCK Generic Error"));
			}
		}
	}
	
	/**
	 * Test HTTP Retry.
	 * @throws ClientProtocolException
	 * @throws IOException
	 * @throws Exception
	 */
	@Test
	public void testHttpRetry() throws ClientProtocolException, IOException, Exception
	{
		try(MockedStatic<HttpClients> mockedStatic = Mockito.mockStatic(HttpClients.class);)
		{
			Mockito.when(mockClient.execute(any(HttpUriRequest.class), any(HttpContext.class))).thenThrow(new ConnectTimeoutException("MOCK Timeout Error"));
			
			Mockito.when(HttpClients.createDefault()).thenReturn(mockClient);
			
			DataRetriever retriever = new FileRetriever();
			//retrieve google - it should be pretty easy.
			URI uri = new URI("http://www.google.com");
			retriever.setDataURL(uri);
			String dest = "/tmp/testFetchData_"+ String.valueOf((new Random()).nextInt(Integer.MAX_VALUE));
			retriever.setFetchDestination(dest);
			Duration age = Duration.of(1,ChronoUnit.SECONDS);
			retriever.setMaxAge(age);
			((FileRetriever)retriever).setNumRetries(1);
			((FileRetriever)retriever).setTimeout(Duration.of(1, ChronoUnit.SECONDS));
			try
			{
				retriever.fetchData();
			}
			catch (Exception e)
			{
				e.printStackTrace();
				assertTrue(e.getMessage().contains("Connection timed out. Number of retries (1) exceeded. No further attempts will be made."));
			}
		}
	}
}