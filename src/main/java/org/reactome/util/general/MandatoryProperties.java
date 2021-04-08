package org.reactome.util.general;

import java.util.Properties;

/**
 * A specialised implementation of Properties that will throw an exception if a requested property key is missing or has no value. 
 * @author sshorser
 *
 */
public class MandatoryProperties extends Properties
{
	public class PropertyNotPresentException extends RuntimeException
	{
		public PropertyNotPresentException(String propName)
		{
			super("The property " + propName + " is not in this set of Properties.");
		}
		
	}
	
	public class PropertyHasNoValueException extends RuntimeException
	{
		public PropertyHasNoValueException(String propName)
		{
			super("The property " + propName + " is present in this set of Properties, but no value has been set for it.");
		}
		
	}
	
	@Override
	public Object get(Object key)
	{
		if (this.containsKey(key))
		{
			return super.get(key);
		}
		else
		{
			throw new PropertyNotPresentException(key.toString());
		}
	}
	
	public Object getMandatoryValue(Object key)
	{
		Object value = this.get(key);
		
		if (value != null && !value.toString().trim().equals(""))
		{
			return key;
		}
		else
		{
			throw new PropertyHasNoValueException(key.toString());
		}
	}
}
