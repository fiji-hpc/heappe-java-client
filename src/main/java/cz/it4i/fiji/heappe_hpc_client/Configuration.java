package cz.it4i.fiji.heappe_hpc_client;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Configuration {

	private Properties properties;
	
	public Configuration(String configFile) {
		try (InputStream is = this.getClass().getClassLoader().getResourceAsStream(configFile)) {
			if (is == null) {
				throw new IllegalArgumentException("Resource " + configFile + " does not exist. Copy " + configFile
						+ ".template and fill it, please!");
			}
			this.properties = new Properties();
			this.properties.load(is);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	protected String getValue(String key) {
		return this.properties.getProperty(key);
	}
}
