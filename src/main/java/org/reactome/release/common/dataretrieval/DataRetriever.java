package org.reactome.release.common.dataretrieval;

import java.net.URI;
import java.time.Duration;

import org.reactome.release.common.CustomLoggable;


public interface DataRetriever extends CustomLoggable
{
	public void fetchData() throws Exception;
	public void setFetchDestination(String destination);
	public void setDataURL(URI uri);
	public void setMaxAge(Duration age);
	public void setRetrieverName(String retrieverName);
}
