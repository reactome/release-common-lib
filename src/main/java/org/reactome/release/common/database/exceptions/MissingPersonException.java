package org.reactome.release.common.database.exceptions;

public class MissingPersonException extends Exception
{

	public MissingPersonException(long dbid)
	{
		super("Could not fetch Person entity with ID " + dbid + ". Please check that a Person entity exists in the database with this ID.");
	}

}
