package org.reactome.release.common;

import java.io.Serializable;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.appender.RollingRandomAccessFileAppender;
import org.apache.logging.log4j.core.appender.rolling.RollingRandomAccessFileManager;
import org.apache.logging.log4j.core.appender.rolling.RolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.TriggeringPolicy;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

/**
 * An interface for classes that may want to customise the way they log.
 * @author sshorser
 *
 */
public interface CustomLoggable
{
	/**
	 * Creates the logger. 
	 * @param logFileName - custom name for log file.
	 * @param oldAppenderName - the name of the appender to copy config from.
	 * @param newAppenderName - the name of the new appender.
	 * @param append - append, or not (truncate).
	 * @param level - logging level.
	 * @return
	 */
	default Logger createLogger(String logFileName, String oldAppenderName, String newAppenderName, boolean append, Level level)
	{
		LoggerContext context = (LoggerContext) LogManager.getContext(false);
		Configuration configuration = context.getConfiguration();
		Appender oldAppender = configuration.getAppender(oldAppenderName);

		// Get base directory for logs, so we know what to use when creating the new appender.
		String baseDirVar = configuration.getStrSubstitutor().getVariableResolver().lookup("baseDir");
		String baseDir = configuration.getStrSubstitutor().replace(baseDirVar);
		Layout<? extends Serializable> oldLayout = oldAppender.getLayout();
		
		// create new appender/logger
		LoggerConfig loggerConfig = new LoggerConfig(logFileName, level, false);
		
		Appender appender ;
		// TODO: Find a better way to create *any* appender of *any* type and still copy over all the config. This is probably much easier said than done. :(
		if (oldAppender instanceof RollingRandomAccessFileAppender)
		{
			int bufferSize = ((RollingRandomAccessFileAppender)oldAppender).getBufferSize();
			
			RollingRandomAccessFileManager oldMananger = (RollingRandomAccessFileManager)((RollingRandomAccessFileAppender) oldAppender).getManager();
			
			TriggeringPolicy triggerPolicy = oldMananger.getTriggeringPolicy();
			RolloverStrategy rollStrategy = oldMananger.getRolloverStrategy();
			Filter filter = ((RollingRandomAccessFileAppender)oldAppender).getFilter();
			// Inject new log file name into filePattern so that file rolling will work properly 
			String pattern = ((RollingRandomAccessFileAppender)oldAppender).getFilePattern().replaceAll("/[^/]*-\\%d\\{yyyy-MM-dd\\}\\.\\%i\\.log\\.gz", "/"+logFileName+"-%d{yyyy-MM-dd}.%i.log.gz");
			appender = RollingRandomAccessFileAppender.newBuilder().withFileName(baseDir + "/" + logFileName + ".log")
																	.withFilePattern(pattern)
																	.withAppend(append)
																	.withName(newAppenderName)
																	.withBufferSize(bufferSize)
																	.withPolicy(triggerPolicy)
																	.withStrategy(rollStrategy)
																	.withLayout(oldLayout)
																	.withImmediateFlush(true)
																	.withFilter(filter)
																	.build();
		}
		else
		{
			appender = FileAppender.newBuilder().withFileName(baseDir + "/" + logFileName + ".log")
												.withAppend(append)
												.withName(newAppenderName)
												.withLayout(oldLayout)
												.setConfiguration(configuration)
												.withLocking(false)
												.withImmediateFlush(true)
												.withIgnoreExceptions(true)
												.withBufferSize(8192)
												.withFilter(null)
												.withAdvertise(false)
												.withAdvertiseUri("")
												.build();
		}
		appender.start();
		loggerConfig.addAppender(appender, level, null);
		configuration.addLogger(logFileName, loggerConfig);
		context.updateLoggers();

		return context.getLogger(logFileName);
	}
	
	/**
	 * Creates a custom logger.
	 * @param logFileName - custom log file name.
	 * @param oldAppenderName - name of the old appender to copy config from
	 * @param newAppenderName - name of new appender
	 * @param append - append?
	 * @param level - logging level.
	 * @param oldLogger - the old Logger - used to log the process of creating the new logger.
	 * @param loggerContainerClassTypeName - name of class that invokes this method (no easy way to get that within the function via reflection, so please set this correctly).
	 * @return
	 */
	default Logger createLogger(String logFileName, String oldAppenderName, String newAppenderName, boolean append, Level level, Logger oldLogger, String loggerContainerClassTypeName)
	{
		if (oldLogger == null)
		{
			oldLogger = LogManager.getLogger();
		}
		
		if (logFileName == null || logFileName.trim().equals(""))
		{
			oldLogger.warn("No custom log file name was set, so this " + loggerContainerClassTypeName + " will not use its own log file.");
			return oldLogger;
		}
		else
		{
			oldLogger.trace("Now creating new logger: {}", logFileName);
			return this.createLogger(logFileName , oldAppenderName, logFileName, true, level);
		}
	}
}
