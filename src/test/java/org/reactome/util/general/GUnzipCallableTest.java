package org.reactome.util.general;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;

import org.junit.Before;
import org.junit.Test;

public class GUnzipCallableTest
{

	private static final String FILE2_CONTENT = "This is file2";
	private static final String FILE1_CONTENT = "This is file1";
	private static final String PATH_TO_ZIP2 = "test2.txt.gz";
	private static final String PATH_TO_UNZIPPED2 = PATH_TO_ZIP2.replace(".gz","");
	private static final String PATH_TO_ZIP1 = "test1.txt.gz";
	private static final String PATH_TO_UNZIPPED1 = PATH_TO_ZIP1.replace(".gz","");


	@Before
	public void setup()
	{
		// Need to create two files and zip them
		try (FileOutputStream fos1 = new FileOutputStream(new File(PATH_TO_ZIP1));
			FileOutputStream fos2 = new FileOutputStream(new File(PATH_TO_ZIP2));
			GZIPOutputStream outStream1 = new GZIPOutputStream(fos1);
			GZIPOutputStream outStream2 = new GZIPOutputStream(fos2); )
		{
			outStream1.write(FILE1_CONTENT.getBytes());
			outStream2.write(FILE2_CONTENT.getBytes());
		}
		catch (FileNotFoundException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	@Test
	public void testGunzipCallable()
	{
		try
		{
			GUnzipCallable gunzipper1 = new GUnzipCallable(Paths.get(PATH_TO_ZIP1), Paths.get(PATH_TO_UNZIPPED1));
			GUnzipCallable gunzipper2 = new GUnzipCallable(Paths.get(PATH_TO_ZIP2), Paths.get(PATH_TO_UNZIPPED2));
			
			ExecutorService executor = Executors.newCachedThreadPool();
			executor.invokeAll(Arrays.asList(gunzipper1, gunzipper2));
			
			// Now, we have to test that the files were unzipped and the contents are correct.
			assertTrue(Files.exists(Paths.get(PATH_TO_UNZIPPED1)));
			assertTrue(Files.exists(Paths.get(PATH_TO_UNZIPPED2)));
			
			assertEquals(new String(Files.readAllBytes(Paths.get(PATH_TO_UNZIPPED1))), FILE1_CONTENT);
			assertEquals(new String(Files.readAllBytes(Paths.get(PATH_TO_UNZIPPED2))), FILE2_CONTENT);
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
			fail();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
}
