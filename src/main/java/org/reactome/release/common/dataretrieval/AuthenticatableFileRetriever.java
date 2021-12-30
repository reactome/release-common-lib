package org.reactome.release.common.dataretrieval;

import javax.json.JsonObject;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;

/**
 * This class is to be extended by File Retrievers that need to be able to
 * authenticate themselves with the peer from which they retrieve data.
 * @author sshorser
 *
 */
public abstract class AuthenticatableFileRetriever extends FileRetriever
{
	public AuthenticatableFileRetriever(String retrieverName)
	{
		super(retrieverName);
	}
	
	public AuthenticatableFileRetriever()
	{
		super();
	}
	
	protected String userName;
	protected String password;
	
	/**
	 * Set the user name.
	 * @param userName User name to set
	 */
	public void setUserName(String userName)
	{
		this.userName = userName;
	}
	
	/**
	 * Set the password.
	 * @param password Password to set
	 */
	public void setPassword(String password)
	{
		this.password = password;
	}

	@Override
	public HttpURLConnection getHttpURLConnection() throws IOException {
		// Encoded string.
		String encodedUsernamePassword = Base64.getEncoder()
			.encodeToString((this.userName + ":" + this.password).getBytes("UTF-8"));

		HttpURLConnection urlConnection = super.getHttpURLConnection();
		urlConnection.setRequestProperty("Authorization", "Basic " + encodedUsernamePassword);
		return urlConnection;
	}

	public HttpURLConnection getHttpURLConnectionWithoutUsingCredentials() throws IOException {
		return super.getHttpURLConnection();
	}

	public JsonObject fetchJSONResponseWithoutUsingCredentials() throws IOException {
		return super.fetchJSONResponse(getHttpURLConnectionWithoutUsingCredentials());
	}

	public String getContentWithoutUsingCredentials() throws IOException {
		return super.getContentForHttp(getHttpURLConnectionWithoutUsingCredentials());
	}

	protected void doHttpDownloadWithoutUsingCredentials(Path path) throws Exception
	{
		super.doHttpDownload(path, getHttpURLConnectionWithoutUsingCredentials());
	}
}
