
package cz.it4i.fiji.ssh_hpc_client;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import cz.it4i.fiji.hpc_workflow.paradigm_manager.SettingsWithWorkingDirectory;
import lombok.Data;

@Data
public class SshConnectionSettings implements SettingsWithWorkingDirectory {

	private static final long serialVersionUID = 7L;

	private String host;
	private int port;
	private AuthenticationChoice authenticationChoice;
	private String userName;
	private String password;
	private File keyFile;
	private String keyFilePassword;
	private String schedulerType;
	private String workingDirectory;
	private String remoteDirectory;
	private String command;
	private String remoteWorkingDirectory;

	public SshConnectionSettings(String host, int port,
		AuthenticationChoice authenticationChoice, String userName, String password,
		File keyFile, String keyFilePassword, String schedulerType,
		String workingDirectory, String remoteDirectoryTextField,
		String commandTextField, String remoteWorkingDirectory)
	{
		this.host = host;
		this.port = port;
		this.authenticationChoice = authenticationChoice;
		this.userName = userName;
		this.password = password;
		this.keyFile = keyFile;
		this.keyFilePassword = keyFilePassword;
		this.schedulerType = schedulerType;
		this.workingDirectory = workingDirectory;
		this.remoteDirectory = remoteDirectoryTextField;
		this.command = commandTextField;
		this.remoteWorkingDirectory = remoteWorkingDirectory;
	}

	@Override
	public Path getWorkingDirectory() {
		return Paths.get(this.workingDirectory);
	}

}
