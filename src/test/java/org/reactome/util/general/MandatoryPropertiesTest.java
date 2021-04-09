package org.reactome.util.general;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.reactome.util.general.MandatoryProperties.PropertyHasNoValueException;
import org.reactome.util.general.MandatoryProperties.PropertyNotPresentException;

public class MandatoryPropertiesTest
{

	private static final String ABSENT_PROP = "BLAHBLAH";
	private static final String VALUE = "value";
	private static final String PROP = "prop";

	@Test
	public void testPropertyPresent()
	{
		MandatoryProperties props = new MandatoryProperties();
		props.put(PROP, VALUE);
		assertEquals(props.getProperty(PROP), VALUE);
	}
	
	@Test
	public void testPropertyAbsent()
	{
		boolean passed = false;
		MandatoryProperties props = new MandatoryProperties();
		props.put(PROP, VALUE);
		try
		{
			props.getProperty(ABSENT_PROP);
		}
		catch (PropertyNotPresentException e)
		{
			e.printStackTrace();
			passed = e.getMessage().contains(ABSENT_PROP + " is not in this set of Properties.");
		}
		assertTrue(passed);
	}
	
	@Test
	public void testPropertyNoValue()
	{
		boolean passed = false;
		MandatoryProperties props = new MandatoryProperties();
		props.put(PROP, "");
		try
		{
			props.getMandatoryProperty(PROP);
		}
		catch (PropertyHasNoValueException e)
		{
			e.printStackTrace();
			passed = e.getMessage().contains(PROP + " is present in this set of Properties, but no value has been set for it.");
		}
		assertTrue(passed);
	}
}
