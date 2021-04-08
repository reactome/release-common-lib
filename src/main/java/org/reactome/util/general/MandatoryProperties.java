package org.reactome.util.general;

import java.util.Properties;

/**
 * A specialised implementation of Properties that will throw an exception if a requested property key is missing or has no value. 
 * @author sshorser
 *
 */
public class MandatoryProperties extends Properties
{
	/**
	 * This exception indicates that a Property key was not present.
	 * @author sshorser
	 *
	 */
	public class PropertyNotPresentException extends RuntimeException
	{
		public PropertyNotPresentException(String propName)
		{
			super("The property " + propName + " is not in this set of Properties.");
		}
		
	}
	
	/**
	 * This exception indicates that a Property key was present, but had no value (when trimmed, it is an empty string OR it is NULL). 
	 * @author sshorser
	 *
	 */
	public class PropertyHasNoValueException extends RuntimeException
	{
		public PropertyHasNoValueException(String propName)
		{
			super("The property " + propName + " is present in this set of Properties, but no value has been set for it.");
		}
		
	}
	
	/**
	 * Returns a value for a property.
	 * @param key The property to look up.
	 * @return The value of the property.
	 * @throws PropertyNotPresentException Thrown when the property is NOT present.
	 */
	@Override
	public String getProperty(String key) throws PropertyNotPresentException
	{
		if (this.containsKey(key))
		{
			return super.getProperty(key);
		}
		else
		{
			throw new PropertyNotPresentException(key);
		}
	}

	/**
	 * Returns the value for a Property. 
	 * @param key The property to look up.
	 * @return The value of the property
	 * @throws PropertyHasNoValueException Thrown when the Property is present but is either NULL, or empty when trimmed.
	 * @throws PropertyNotPresentException Thrown when the Property is NOT present.
	 */
	public String getMandatoryProperty(String key) throws PropertyHasNoValueException, PropertyNotPresentException
	{
		String value = this.getProperty(key);
		
		if (value != null && !value.trim().equals(""))
		{
			return key;
		}
		else
		{
			throw new PropertyHasNoValueException(key);
		}
	}
}
