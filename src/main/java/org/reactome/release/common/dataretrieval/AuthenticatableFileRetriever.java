package org.reactome.release.common.dataretrieval;

/**
 * This class is to be extended by File Retrievers that need to be able to
 * authenticate themselves with the peer from which they retrieve data.
 * @author sshorser
 *
 */
public abstract class AuthenticatableFileRetriever extends FileRetriever
{
	public AuthenticatableFileRetriever(String retrieverName)
	{
		super(retrieverName);
	}
	
	public AuthenticatableFileRetriever()
	{
		super();
	}
	
	protected String userName;
	protected String password;
	
	/**
	 * Set the user name.
	 * @param userName
	 */
	public void setUserName(String userName)
	{
		this.userName = userName;
	}
	
	/**
	 * Set the password.
	 * @param password
	 */
	public void setPassword(String password)
	{
		this.password = password;
	}
	
}
