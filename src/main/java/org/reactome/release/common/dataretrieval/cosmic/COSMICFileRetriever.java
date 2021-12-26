package org.reactome.release.common.dataretrieval.cosmic;

import org.reactome.release.common.dataretrieval.AuthenticatableFileRetriever;

import java.io.*;
import java.net.*;
import java.util.Base64;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;


/**
 * Get COSMIC data file.
 * 
 * The COSMIC data file cannot be downloaded in a single step.
 * To download the COSMIC data file programmatically, you must first request the file 
 * with a Basic Authorization header which includes the username and password, base64 encoded.
 * You will get a small JSON document back, if authorization succeeds. This document will contain
 * a "url" element. The data file can be accessed if you download the URL that is returned in the JSON
 * document.
 * @author sshorser
 *
 */
public class COSMICFileRetriever extends AuthenticatableFileRetriever
{
	public COSMICFileRetriever(String retrieverName)
	{
		super(retrieverName);
	}
	
	public COSMICFileRetriever()
	{
		super();
	}
	
	private boolean retrieveAndSetCOSMICDownloadURL() throws IOException {
		boolean gotDownloadURLOK = false;
		// The NEW process to download from COSMIC requires a few steps.
		// 1) Generate a base64-encoded string of the username and password.
		// 2) Send this encoded string in the "Authorization: Basic" header to the URL of the file you want.
		// 3) Parse the response - extract the "url" JSON attribute and then download THAT URL to get the REAL file.
		
		// Encoded string.
		String encodedUsernamePassword = Base64.getEncoder().encodeToString((this.userName + ":" + this.password).getBytes("UTF-8"));

		HttpURLConnection urlConnection = (HttpURLConnection) this.uri.toURL().openConnection();
		//Need to multiply by 1000 because timeouts are in milliseconds.
		int delayInMilliseconds = 1000 * (int)this.timeout.getSeconds();
		urlConnection.setConnectTimeout(delayInMilliseconds);
		urlConnection.setReadTimeout(delayInMilliseconds);
		urlConnection.setRequestProperty("Authorization", "Basic "+encodedUsernamePassword);

		String downloadURL = null;
		try {
				int statusCode = urlConnection.getResponseCode();
				String content = getContent(urlConnection);
				// If status code was not 200, we should print something so that the users know that an unexpected response was received.
				switch (statusCode)
				{
					case HttpURLConnection.HTTP_OK:
						// Now we need to turn parse the JSON in responseString and extract the URL to download from.
						JsonReader reader = Json.createReader(new StringReader(content));
						JsonObject responseObject = reader.readObject();
						downloadURL = responseObject.get("url").toString().replaceAll("\"", "");
						// Update this object's downloadURL to be the one that came back from the request
						this.setDataURL(new URI(downloadURL));
						logger.info("COSMIC download URL has been set.");
						gotDownloadURLOK = true;
						// Call downloadData of FileRetriever to perform a "normal" download, now that the special URL has been set.
						break;
					
					default:
						logger.error("Non-200 response: {}", urlConnection.getResponseMessage());
						gotDownloadURLOK = false;
						break;
				}
		}
		catch (IOException e)
		{
			logger.error("IOException was caught, probably caused by some network communication issue. Message: {}", e.getMessage());
			e.printStackTrace();
		}
		catch (URISyntaxException e)
		{
			logger.error("The URL from COSMIC might be malformed. URL is: \"{}\", Error message is: {}", downloadURL, e.getMessage() );
			e.printStackTrace();
		}
		return gotDownloadURLOK;
	}
	
	@Override
	protected void downloadData() throws UnsupportedEncodingException, Exception
	{
		boolean updatedCOSMICURLOK = this.retrieveAndSetCOSMICDownloadURL();
		if (updatedCOSMICURLOK)
		{
			super.downloadData();
		}
		else
		{
			logger.warn("The COSMIC Download URL was not updated successfully, so file download was not attempted.");
		}
	}

	private String getContent(HttpURLConnection urlConnection) throws IOException {
		BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
		return bufferedReader.lines().collect(Collectors.joining(System.lineSeparator()));
	}
}
