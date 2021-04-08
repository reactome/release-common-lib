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
		assertEquals(props.get(PROP), VALUE);
	}
	
	@Test
	public void testPropertyAbsent()
	{
		MandatoryProperties props = new MandatoryProperties();
		props.put(PROP, VALUE);
		try
		{
			props.get(ABSENT_PROP);
		}
		catch (PropertyNotPresentException e)
		{
			assertTrue(e.getMessage().contains(ABSENT_PROP));
		}
	}
	
	@Test
	public void testPropertyNoValue()
	{
		MandatoryProperties props = new MandatoryProperties();
		props.put(PROP, "");
		try
		{
			props.get(PROP);
		}
		catch (PropertyHasNoValueException e)
		{
			assertTrue(e.getMessage().contains(PROP));
		}
	}
}
