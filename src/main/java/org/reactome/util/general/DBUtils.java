package org.reactome.util.general;

import org.gk.persistence.MySQLAdaptor;

import java.sql.SQLException;
import java.util.Properties;

/**
 * @author Joel Weiser (joel.weiser@oicr.on.ca)
 *         Created 4/1/2024
 */
public class DBUtils {

    public static MySQLAdaptor getCuratorDbAdaptor(Properties configProperties) throws SQLException {
        return getDbAdaptor("curator.database", configProperties);
    }

    public static MySQLAdaptor getReleaseDbAdaptor(Properties configProperties) throws SQLException {
        return getDbAdaptor("release.database", configProperties);
    }

    public static MySQLAdaptor getDbAdaptor(String prefix, Properties configProperties) throws SQLException {
        return new MySQLAdaptor(
            configProperties.getProperty(prefix + ".host", "localhost"),
            configProperties.getProperty(prefix + ".name"),
            configProperties.getProperty(prefix + ".user", "root"),
            configProperties.getProperty(prefix + ".password", "root"),
            Integer.parseInt(configProperties.getProperty(prefix + ".port", "3306"))
        );
    }
}
