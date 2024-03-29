
package cz.it4i.fiji.heappe_hpc_client.paradigm_manager.ui;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import cz.it4i.fiji.heappe_hpc_client.HaaSClientSettingsImpl;
import cz.it4i.fiji.hpc_workflow.core.Constants;
import cz.it4i.fiji.hpc_workflow.paradigm_manager.WorkflowParadigmManager;
import cz.it4i.swing_javafx_ui.JavaFXRoutines;
import cz.it4i.swing_javafx_ui.SimpleDialog;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

public class LoginViewController extends AnchorPane {

	@FXML
	Button okButton;

	@FXML
	Button browseButton;

	@FXML
	TextField userNameTextField;

	@FXML
	PasswordField passwordPasswordField;

	@FXML
	TextField emailTextFiled;

	@FXML
	TextField workingDirectoryTextField;

	private HaaSClientSettingsImpl parameters;

	public LoginViewController() {
		JavaFXRoutines.initRootAndController("LoginView.fxml", this);
	}

	public void setInitialFormValues(HaaSClientSettingsImpl oldLoginSettings) {
		if (oldLoginSettings != null) {
			this.userNameTextField.setText(oldLoginSettings.getUserName());
			this.passwordPasswordField.setText(oldLoginSettings.getPassword());
			this.emailTextFiled.setText(oldLoginSettings.getEmail());
			this.workingDirectoryTextField.setText(oldLoginSettings
				.getWorkingDirectory().toString());
		}
	
	}

	public HaaSClientSettingsImpl getParameters() {
		return parameters;
	}

	@FXML
	private void browseAction() {
		Stage stage = (Stage) browseButton.getScene().getWindow();
		File selectedDirectory = SimpleDialog.directoryChooser(stage,
			"Open Working Directory");
		if (selectedDirectory != null) {
			this.workingDirectoryTextField.setText(selectedDirectory
				.getAbsolutePath());
		}
	}

	@FXML
	private void okAction() {

		if (parametersAreFilledInAndCorrect()) {
			// Save parameters:
			this.parameters = constructParameters();

			// Close the modal window:
			Stage stage = (Stage) okButton.getScene().getWindow();
			stage.close();
		}

	}

	private boolean parametersAreFilledInAndCorrect() {
		// Check if parameters are filled in:
		if (this.userNameTextField.getText().isEmpty() || this.passwordPasswordField
			.getText().isEmpty() || this.emailTextFiled.getText().isEmpty() ||
			this.workingDirectoryTextField.getText().isEmpty())
		{
			SimpleDialog.showInformation("Missing fields",
				"Please fill in the whole form.");
			return false;
		}

		return WorkflowParadigmManager.workingDirectoryExists(Paths.get(
			this.workingDirectoryTextField.getText()));

	}

	private HaaSClientSettingsImpl constructParameters() {
		String userName = this.userNameTextField.getText();
		String password = this.passwordPasswordField.getText();
		String email = this.emailTextFiled.getText();
		Path workingDirPath = new File(this.workingDirectoryTextField.getText())
			.toPath();

		return new HaaSClientSettingsImpl(userName, password, Constants.PHONE,
			email, workingDirPath);
	}
}
