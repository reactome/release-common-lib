package org.reactome.release.common.database.exceptions;

public class DatabaseException extends Exception
{

	public DatabaseException(Throwable e)
	{
		super(e);
	}

	public DatabaseException(String message, Throwable e)
	{
		super(message, e);
	}

	public DatabaseException(String message)
	{
		super(message);
	}

}
