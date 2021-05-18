package org.reactome.release.common.dataretrieval.exceptions;

/**
 * Indicates that the number of allowed retries has been exceeded. Data has probably not been retrieved if this is caught.
 * @author sshorser
 *
 */
public class RetriesExceeded extends DataRetrievalException
{
	public RetriesExceeded(String message)
	{
		super(message);
	}

	public RetriesExceeded(String message, Throwable e)
	{
		super(message, e);
	}
}
