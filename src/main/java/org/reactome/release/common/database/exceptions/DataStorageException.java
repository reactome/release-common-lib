package org.reactome.release.common.database.exceptions;

public class DataStorageException extends DatabaseException
{
	public DataStorageException(Throwable e)
	{
		super(e);
	}

	public DataStorageException(String message, Throwable e)
	{
		super(message, e);
	}

	public DataStorageException(String message)
	{
		super(message);
	}
}
