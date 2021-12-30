/**
 * 
 */
package org.reactome.release.common.dataretrieval;

import static org.junit.Assert.assertTrue;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Random;

import org.apache.commons.net.ftp.FTPClient;

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

	@Before
	public void setup()
	{
		MockitoAnnotations.openMocks(this);
	}
	
	/**
	 * Test method for {@link org.reactome.release.common.dataretrieval.FileRetriever#fetchData()}.
	 * @throws Exception 
	 */
	@Test
	public void testFetchData() throws Exception
	{
		DataRetriever retriever = new FileRetriever();
		//retrieve google - it should be pretty easy.
		URI uri = new URI("https://www.google.com");
		retriever.setDataURL(uri);

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
				assertTrue(e.getMessage().contains("500 ERROR"));
			}
		}
	}
	
	/**
	 * Test HTTP Error handling
	 * @throws Exception
	 */
	@Test
	public void testHttpErr() throws Exception
	{
		FileRetriever retriever = Mockito.spy(FileRetriever.class);
		//retrieve google - it should be pretty easy.
		retriever.setDataURL(new URI("https://www.google.com"));
		String dest = "/tmp/testFetchData_"+ String.valueOf((new Random()).nextInt(Integer.MAX_VALUE));
		retriever.setFetchDestination(dest);
		retriever.setMaxAge(Duration.of(1,ChronoUnit.SECONDS));
		retriever.setNumRetries(0);
		retriever.setTimeout(Duration.of(1, ChronoUnit.SECONDS));

		try
		{
			Mockito.doThrow(new SocketTimeoutException("Dummy text"))
				.when(retriever).saveContentsToFile(Mockito.any(HttpURLConnection.class), Mockito.any(Path.class));
			retriever.fetchData();

			// Test fails here since an fetch data should throw an exception going to the catch block
			// (that is, fetching data on previous line should throw an exception due to the mock)
			fail();
		}
		catch (Exception e)
		{
			assertTrue(e.getMessage().contains("No further attempts will be made"));
		}
	}
	
	/**
	 * Test HTTP Retry.
	 * @throws Exception
	 */
	@Test
	public void testHttpRetry() throws URISyntaxException
	{
		FileRetriever retriever = Mockito.spy(FileRetriever.class);
		//retrieve google - it should be pretty easy.
		retriever.setDataURL(new URI("https://www.google.com"));
		String dest = "/tmp/testFetchData_"+ String.valueOf((new Random()).nextInt(Integer.MAX_VALUE));
		retriever.setFetchDestination(dest);
		retriever.setMaxAge(Duration.of(1,ChronoUnit.SECONDS));
		retriever.setNumRetries(1);
		retriever.setTimeout(Duration.of(1, ChronoUnit.SECONDS));

		try
		{
			Mockito.doThrow(new SocketTimeoutException("Dummy text"))
				.when(retriever).saveContentsToFile(Mockito.any(HttpURLConnection.class), Mockito.any(Path.class));
			retriever.fetchData();

			// Test fails here since an fetch data should throw an exception going to the catch block
			// (that is, fetching data on previous line should throw an exception due to the mock)
			fail();
		}
		catch (Exception e)
		{
			assertTrue(e.getMessage().contains("Connection timed out. Number of retries (1) exceeded. No further attempts will be made."));
		}
	}
}