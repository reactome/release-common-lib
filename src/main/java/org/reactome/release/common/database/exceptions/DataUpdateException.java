package org.reactome.release.common.database.exceptions;

public class DataUpdateException extends DatabaseException
{
	public DataUpdateException(Throwable e)
	{
		super(e);
	}

	public DataUpdateException(String message, Throwable e)
	{
		super(message, e);
	}

	public DataUpdateException(String message)
	{
		super(message);
	}
}
