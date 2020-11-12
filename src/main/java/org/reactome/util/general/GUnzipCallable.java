package org.reactome.util.general;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.zip.GZIPInputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 * An object that can unzip a GZIP file, and mutliple instances of this type could be run in parallel.
 * It made more sense to implement Runnable, but ExecutorService.invokeAll only accepts Callables.
 * The return of this calling call() on this object will be true if unzipping doesn't completely fail,
 * but you should not rely on this. The source file does *not* get removed by this process.
 * <br/><br/>
 * Example usage:
<pre>
	GUnzipCallable datUnzipper1 = new GUnzipCallable(Paths.get("large_file_1.dat.gz"), Paths.get("./large_file_1.dat"));
	GUnzipCallable datUnzipper2 = new GUnzipCallable(Paths.get("large_file_3.dat.gz"), Paths.get("./large_file_3.dat"));
	GUnzipCallable unzipper3 = new GUnzipCallable(Paths.get("large_text_file.csv.gz"), Paths.get("/path/to/text/files/large_text_file.csv"));
	GUnzipCallable unzipper4 = new GUnzipCallable(Paths.get("some_other_file.gz"), Paths.get("./some_other_file"));
	
	ExecutorService execService = Executors.newCachedThreadPool();
	// The files are large and it could be slow to unzip them sequentially, so we will unzip them in parallel.
	execService.invokeAll(Arrays.asList(datUnzipper1, datUnzipper2, unzipper3, unzipper4));
	execService.shutdown();
</pre>
 * @author sshorser
 */
public class GUnzipCallable implements Callable<Boolean>
{
	private static final Logger logger = LogManager.getLogger();
	
	private Path source, target;
	
	/**
	 * Creates an UnzipCallable
	 * @param src - The path to the file to unzip.
	 * @param targ - The path where the content should be unzipped to.
	 */
	public GUnzipCallable(Path src, Path targ)
	{
		this.source = src;
		this.target = targ;
	}
	
	/**
	 * Decompress a gzip file.
	 * @throws IOException
	 */
	public void decompressGzip() throws IOException
	{
		logger.info("Extracting {} to {}", this.source, this.target);
		try (GZIPInputStream gis = new GZIPInputStream(new FileInputStream(this.source.toFile()));
				FileOutputStream fos = new FileOutputStream(this.target.toFile()))
		{
			// copy GZIPInputStream to FileOutputStream
			byte[] buffer = new byte[1024];
			int len;
			while ((len = gis.read(buffer)) > 0)
			{
				fos.write(buffer, 0, len);
			}
		}
		logger.info("Completed: Extraction of {} to {}", this.source, this.target);
	}
	
	@Override
	public Boolean call() throws IOException
	{
		this.decompressGzip();
		// If decompress doesn't fail, then true will be returned.
		return true;
	}
}