package org.reactome.release.common.dataretrieval.exceptions;

/**
 * Used to indicate some general problem has occured while trying to retrieve data.
 * @author sshorser
 *
 */
public class DataRetrievalException extends Exception
{

	public DataRetrievalException(String message)
	{
		super(message);
	}

	public DataRetrievalException(String message, Throwable e)
	{
		super(message, e);
	}

}
