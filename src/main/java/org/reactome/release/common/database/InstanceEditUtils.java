package org.reactome.release.common.database;

import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.PersistenceAdaptor;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.InvalidAttributeValueException;
import org.gk.schema.SchemaClass;
import org.gk.util.GKApplicationUtilities;
import org.reactome.release.common.database.exceptions.DataFetchException;
import org.reactome.release.common.database.exceptions.DataStorageException;
import org.reactome.release.common.database.exceptions.DataUpdateException;
import org.reactome.release.common.database.exceptions.MissingPersonException;

/**
 * This class provides methods to create InstanceEdits as GKInstance objects
 * @author sshorser
 */
public class InstanceEditUtils
{
	/*
	 * private constructor to prevent instantiation of this utility class
	 */
	private InstanceEditUtils()
	{
		// no-op
	}

	/**
	 * Creates an InstanceEdit object associated with a specific person id and creator name
	 *
	 * @param dba MySQLAdaptor connecting to the database for which the InstanceEdit will be created and stored
	 * @param personID ID of the associated Person instance in the database
	 * @param creatorName The name of the thing that is creating this InstanceEdit.  Typically, you would want to use
	 * the package and classname that uses <i>this</i> object, so it can be traced to the appropriate part of the
	 * program.
	 * @return InstanceEdit as a GKInstance object
	 * @throws InvalidAttributeValueException
	 * @throws InvalidAttributeException
	 * @throws MissingPersonException
	 * @throws DataUpdateException
	 * @throws DataFetchException
	 * @throws DataStorageException
	 * @throws Exception Thrown if unable to create the GKInstance object representing an InstanceEdit
	 */
	public static GKInstance createInstanceEdit(MySQLAdaptor dba, long personID, String creatorName) throws InvalidAttributeException, InvalidAttributeValueException, MissingPersonException, DataUpdateException, DataStorageException, DataFetchException
	{
		GKInstance instanceEdit = createDefaultIE(dba, personID, true, "Inserted by " + creatorName);
		instanceEdit.getDBID();
		try
		{
			dba.updateInstance(instanceEdit);
		}
		catch (Exception e)
		{
			throw new DataUpdateException(e);
		}
		return instanceEdit;
	}

	// This code below was taken from 'add-links' repo:
	// org.reactomeaddlinks.db.ReferenceCreator
	/**
	 * Creates and saves in the database a default InstanceEdit associated with the
	 * Person entity whose DB_ID is <i>defaultPersonId</i>.
	 *
	 * @param dba MySQLAdaptor connecting to the database for which the InstanceEdit will be created and stored
	 * @param defaultPersonId ID of the associated Person instance in the database
	 * @param needStore true if the created InstanceEdit should be stored in the database; false otherwise
	 * @param note Text added to the InstanceEdit to describe its purpose (e.g. what was changed or by whom)
	 * @return InstanceEdit as a GKInstance object.
	 * @throws InvalidAttributeValueException
	 * @throws InvalidAttributeException
	 * @throws MissingPersonException
	 * @throws DataStorageException
	 * @throws DataFetchException
	 * @throws Exception Thrown if unable to retrieve the Person instance associated with the defaultPersonId
	 */
	public static GKInstance createDefaultIE(MySQLAdaptor dba, long defaultPersonId, boolean needStore, String note) throws InvalidAttributeException, InvalidAttributeValueException, MissingPersonException, DataStorageException, DataFetchException
	{
		GKInstance defaultPerson;
		try
		{
			defaultPerson = dba.fetchInstance(defaultPersonId);
			if (defaultPerson != null)
			{
				GKInstance newIE = createDefaultInstanceEdit(defaultPerson);
				newIE.addAttributeValue(ReactomeJavaConstants.dateTime, GKApplicationUtilities.getDateTime());
				newIE.addAttributeValue(ReactomeJavaConstants.note, note);
				InstanceDisplayNameGenerator.setDisplayName(newIE);

				if (needStore)
				{
					try
					{
						dba.storeInstance(newIE);
					}
					catch (Exception e)
					{
						throw new DataStorageException(e);
					}
				}
				return newIE;
			}
			else
			{
				throw new MissingPersonException(defaultPersonId);
			}
		}
		catch (Exception e1)
		{
			throw new DataFetchException(e1);
		}
	}

	/**
	 * Creates an InstanceEdit for with a Person instance as its author
	 *
	 * @param person Person instance to associate as the InstanceEdit author
	 * @return InstanceEdit as GKInstance object
	 * @throws InvalidAttributeValueException Thrown if the person argument is an invalid value for the "author"
	 * attribute of the InstanceEdit
	 * @throws InvalidAttributeException
	 */
	public static GKInstance createDefaultInstanceEdit(GKInstance person) throws InvalidAttributeValueException, InvalidAttributeException
	{
		GKInstance instanceEdit = new GKInstance();
		PersistenceAdaptor adaptor = person.getDbAdaptor();
		instanceEdit.setDbAdaptor(adaptor);
		SchemaClass cls = adaptor.getSchema().getClassByName(ReactomeJavaConstants.InstanceEdit);
		instanceEdit.setSchemaClass(cls);

		try
		{
			instanceEdit.addAttributeValue(ReactomeJavaConstants.author, person);
		}
		catch (InvalidAttributeException e)
		{
			throw e;
		}

		return instanceEdit;
	}
}
