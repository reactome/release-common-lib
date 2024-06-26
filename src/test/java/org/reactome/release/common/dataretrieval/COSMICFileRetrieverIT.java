package org.reactome.release.common.dataretrieval;

import static org.junit.Assert.fail;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Properties;

import org.junit.Test;
import org.reactome.release.common.dataretrieval.cosmic.COSMICFileRetriever;

public class COSMICFileRetrieverIT
{
	@Test
	public void testGetCOSMICData() throws URISyntaxException, FileNotFoundException, IOException
	{
		// to run this test, you will need to create your own auth.properties under src/test/resources
		// and populate it with a valid username and password for COSMIC. 
		Properties props = new Properties();
		props.load(new FileReader("src/test/resources/auth.properties"));
		COSMICFileRetriever retriever = new COSMICFileRetriever();
		retriever.setUserName(props.getProperty("username"));
		retriever.setPassword(props.getProperty("password"));
		
		// classification.csv is a pretty small file, ~2MB I think, so a good choice for testing downloads.
		retriever.setDataURL(new URI("https://cancer.sanger.ac.uk/cosmic/file_download/GRCh38/cosmic/v92/classification.csv"));
		retriever.setMaxAge(Duration.ofSeconds(10));
		retriever.setTimeout(Duration.ofSeconds(100));
		retriever.setFetchDestination("/tmp/cosmic_classification.csv");
		retriever.setNumRetries(1);

		try
		{
			retriever.fetchData();
			assert(Files.exists(Paths.get("/tmp/cosmic_classification.csv")));
		}
		catch (Exception e)
		{
			e.printStackTrace();
			fail();
		}
	}
}