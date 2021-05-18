package org.reactome.release.common.database.exceptions;

public class DataFetchException extends DatabaseException
{
	public DataFetchException(Throwable e)
	{
		super(e);
	}

	public DataFetchException(String message, Throwable e)
	{
		super(message, e);
	}

	public DataFetchException(String message)
	{
		super(message);
	}
}
