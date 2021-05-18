package org.reactome.release.common.dataretrieval.exceptions;

/**
 * Indicates that a general FTP error occurred.
 * @author sshorser
 *
 */
public class FtpException extends DataRetrievalException
{

	public FtpException(String message)
	{
		super(message);
	}
}
