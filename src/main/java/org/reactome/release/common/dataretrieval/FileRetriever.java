package org.reactome.release.common.dataretrieval;	

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

public class FileRetriever implements DataRetriever {

	protected URI uri;
	protected String destination;
	protected Duration maxAge;
	protected Duration timeout = Duration.ofSeconds(30);
	protected int numRetries = 1;
	protected String retrieverName;
	protected Logger logger;
	protected boolean passiveFTP = false;
	
	
	public FileRetriever()
	{
		this(null);
	}
	
	/**
	 * This constructor takes a string that will be used to name the logging output file.
	 * 
	 * @param retrieverName - the name of this File Retriever - the log file produced by this class will be named "${retrieverName}.log"
	 */
	public FileRetriever(String retrieverName)
	{
		//logger.trace("Setting retrieverName to {}", retrieverName);
		this.setRetrieverName(retrieverName);
		this.logger = this.createLogger(this.retrieverName, "RollingRandomAccessFile", this.getClass().getName(), true, Level.DEBUG, this.logger, "Data Retriever");
	}
	
	@Override
	public void fetchData() throws Exception 
	{
		if (this.uri == null)
		{
			throw new RuntimeException("You must provide a URI from which the file will be downloaded!");
		}
		else if (this.destination.trim().length() == 0)
		{
			throw new RuntimeException("You must provide a destination to which the file will be downloaded!");
		}
		//Before fetching anything, we need to check to see if the file already exists.
		Path pathToFile = Paths.get(this.destination);
		if (Files.exists(pathToFile))
		{
			BasicFileAttributes attributes = Files.readAttributes(pathToFile, BasicFileAttributes.class);
			
			Instant fileCreateTime = attributes.creationTime().toInstant();
			Instant now = Instant.now();
			//If the file is older than the maxAge...
			if (fileCreateTime.isBefore( now.minus(this.maxAge) ))
			{
				//TODO: Option to save back-ups of old files.
				logger.debug("File {} is older than allowed amount ({}) so it will be downloaded again.",this.destination,this.maxAge);
				downloadData();
				logger.debug("Download is complete.");
			}
			else
			{
				logger.debug("File {} is not older than allowed amount ({}) so it will not be downloaded.",this.destination,this.maxAge);
			}
		}
		else
		{
			logger.debug("File {} does not exist and must be downloaded.", pathToFile);
			//if file does not exist, get it from the URL.
			downloadData();
		}
		
		// Print some basic file stats.
		if (Files.exists(pathToFile))
		{
			if (Files.isReadable(pathToFile))
			{
				BasicFileAttributes attribs = Files.readAttributes(pathToFile, BasicFileAttributes.class);
				logger.info("File Info: Name: {}, Size: {}, Created: {}, Modified: {}",this.destination,attribs.size(), attribs.creationTime(), attribs.lastModifiedTime());
			}
			else
			{
				logger.error("File {} is not readable!", pathToFile);
			}
		}
		else
		{
			// If something failed during the data retrieval, the file might not exist. If that happens, let the user know.
			logger.error("File \"{}\" still does not exist after executing the file retriever!", pathToFile);
		}
	}

	protected void downloadData() throws Exception
	{
		logger.trace("Scheme is: {}", this.uri.getScheme());

		try
		{
			createFileParentDirectory();
			if (this.uri.getScheme().equals("http") || this.uri.getScheme().equals("https"))
			{
				doHttpDownload(getFilePath());
			}
			else if (this.uri.getScheme().equals("ftp") || this.uri.getScheme().equals("sftp"))
			{
				doFtpDownload();
			}
			else
			{
				throw new Exception("URI "+this.uri.toString()+" uses an unsupported scheme: "+this.uri.getScheme());
			}
		}
		catch (URISyntaxException e)
		{
			logger.error("Error creating download destination: " + this.destination, e);
			throw e;
		}
		catch (IOException e)
		{
			logger.error("Unable to create parent directory of download destination: " + getFilePath().toString(), e);
			throw e;
		}
		catch (Exception e)
		{
			logger.error("Error performing download!", e);
			throw e;
		}
		

	}

	protected void doFtpDownload() throws Exception
	{
		doFtpDownload(null, null);
	}
	
	protected void doFtpDownload(String user, String password) throws Exception
	{
		// user is "anonymous" if provided username is null/empty
		if (user == null || user.trim().equals(""))
		{
			user = "anonymous";
		}
		
		// password is an empty string, if provided password is null/whitespace
		if (password == null || password.trim().equals(""))
		{
			password = "";
		}
		FTPClient client = new FTPClient();
		
		client.connect(this.uri.getHost());
		if (this.passiveFTP)
		{
			client.enterLocalPassiveMode(); //PASSIVE mode works better when inside a docker container.
		}
		client.login(user, password);
		logger.debug("connect/login reply code: {}",client.getReplyCode());
		client.setFileType(FTP.BINARY_FILE_TYPE);
		client.setFileTransferMode(FTP.COMPRESSED_TRANSFER_MODE);
		try
		{
			try(InputStream inStream = client.retrieveFileStream(this.uri.getPath()))
			{
				if (inStream != null)
				{
					writeInputStreamToFile(inStream);
				}
				else
				{
					logger.error("No data returned from server for {}", this.uri.toString());
				}
			}
		}
		catch (IOException e)
		{
			logger.error("Error while retrieving the file: {}",e.getMessage());
			e.printStackTrace();
			throw new IOException(e);
		}
		//Should probably have more/better reply-code checks.
		logger.debug("retreive file reply code: {}",client.getReplyCode());
		if (client.getReplyString().matches("^5\\d\\d.*") || (client.getReplyCode() >= 500 && client.getReplyCode() < 600) )
		{
			String errorString = "5xx reply code detected (" + client.getReplyCode() + "), reply string is: "+client.getReplyString();
			logger.error(errorString);
			throw new Exception(errorString);
		}
		client.logout();
		client.disconnect();
	}

	protected void writeInputStreamToFile(InputStream inStream) throws IOException, FileNotFoundException
	{
		try(ByteArrayOutputStream baos = new ByteArrayOutputStream())
		{
			int b = inStream.read();
			while (b!=-1)
			{
				baos.write(b);
				b = inStream.read();
			}
	
			try (FileOutputStream file = new FileOutputStream(this.destination))
			{
				baos.writeTo(file);
				file.flush();
			}
		}
	}

	protected void doHttpDownload(Path path) throws Exception {
		doHttpDownload(path, getHttpURLConnection());
	}

	protected void doHttpDownload(Path path, HttpURLConnection urlConnection) throws Exception
	{
		int retries = this.numRetries;
		boolean done = retries + 1 <= 0;
		while(!done)
		{
			try
			{
				logResponseIfStatusNotOkay(urlConnection);
				saveContentsToFile(urlConnection, path);

				done = true;
			}
			catch (SocketTimeoutException e)
			{
				// we will only be retrying the connection timeouts, defined as the time required to establish a connection.
				// we will not handle socket timeouts (inactivity that occurs after the connection has been established).
				// we will not handle connection manager timeouts (time waiting for connection manager or connection pool).
				//e.printStackTrace();
				logger.info("Failed due to ConnectTimeout, but will retry {} more time(s).", retries);
				retries--;
				done = retries + 1 <= 0;
				if (done)
				{
					throw new Exception("Connection timed out. Number of retries ("+this.numRetries+") exceeded. No further attempts will be made.", e);
				}
			}
			catch (IOException e) {
				logger.error("Exception caught: {}",e.getMessage());
				throw e;
			}
		}
	}

	protected void logResponseIfStatusNotOkay(HttpURLConnection urlConnection) throws IOException {
		int statusCode = urlConnection.getResponseCode();
		String statusMessage = urlConnection.getResponseMessage();

		// If status code was not 200, we should print something so that the users know that an unexpected response was received.
		if (statusCode != HttpURLConnection.HTTP_OK){
			if (String.valueOf(statusCode).startsWith("4") || String.valueOf(statusCode).startsWith("5"))
			{
				logger.error("Response code was 4xx/5xx: {}, Status line is: {}", statusCode, statusMessage);
			}
			else
			{
				logger.warn("Response was not \"200\". It was: {}", statusMessage);
			}
		}
	}

	protected void saveContentsToFile(HttpURLConnection urlConnection, Path path) throws IOException {
		Files.copy(urlConnection.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
	}

	protected HttpURLConnection getHttpURLConnection() throws IOException {
		int timeoutInMilliSeconds = (int) this.getTimeout().toMillis();
		HttpURLConnection urlConnection = (HttpURLConnection) this.uri.toURL().openConnection();
		urlConnection.setConnectTimeout(timeoutInMilliSeconds);
		urlConnection.setReadTimeout(timeoutInMilliSeconds);
		return urlConnection;
	}

	protected String getContentForHttp() throws IOException {
		return getContentForHttp(getHttpURLConnection());
	}

	protected JsonObject fetchJSONResponse() throws IOException {
		return fetchJSONResponse(getHttpURLConnection());
	}

	protected String getContentForHttp(HttpURLConnection urlConnection) throws IOException {
		BufferedReader bufferedReader =
			new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
		return bufferedReader.lines().collect(Collectors.joining(System.lineSeparator()));
	}

	protected JsonObject fetchJSONResponse(HttpURLConnection urlConnection) throws IOException {
		JsonReader reader = Json.createReader(new StringReader(getContentForHttp(urlConnection)));
		return reader.readObject();
	}

	protected Path getFilePath() {
		return Paths.get(this.destination);
	}

	protected void createFileParentDirectory() throws IOException {
		Files.createDirectories(getFilePath().getParent());
	}

	public Duration getMaxAge()
	{
		return this.maxAge;
	}

	@Override
	public void setMaxAge(Duration age) {
		this.maxAge = age;
	}

	public URI getDataURL()
	{
		return this.uri;
	}

	@Override
	public void setDataURL(URI uri) {
		this.uri = uri;
	}

	public String getFetchDestination() {
		return this.destination;
	}

	@Override
	public void setFetchDestination(String destination) {
		this.destination = destination;
	}

	public int getNumRetries()
	{
		return this.numRetries;
	}

	public void setNumRetries(int i)
	{
		this.numRetries = i;
	}

	public Duration getTimeout() {
		return this.timeout;
	}

	public void setTimeout(Duration timeout)
	{
		this.timeout = timeout;
	}

	public String getRetrieverName()
	{
		return this.retrieverName;
	}

	@Override
	public void setRetrieverName(String retrieverName)
	{
		this.retrieverName = retrieverName;
	}

	public boolean isPassiveFTP()
	{
		return this.passiveFTP ;
	}

	public void setPassiveFTP(boolean passiveMode)
	{
		this.passiveFTP = passiveMode;
	}
}