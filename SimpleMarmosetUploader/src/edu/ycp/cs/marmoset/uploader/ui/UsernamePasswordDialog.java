/*
SWT/JFace in Action
GUI Design with Eclipse 3.0
Matthew Scarpino, Stephen Holder, Stanford Ng, and Laurent Mihalkovic

ISBN: 1932394273

Publisher: Manning
 */

// Adapted for SimpleMarmosetUploader

package edu.ycp.cs.marmoset.uploader.ui;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class UsernamePasswordDialog extends Dialog {
	private String projectNumber;
	private String courseName;
	private String semester;
	private Label projectAndCourseDescription;
	private Label message;
	private Text usernameField;
	private Text passwordField;
	private String username;
	private String password;

	public UsernamePasswordDialog(Shell parentShell, String projectNumber, String courseName, String semester) {
		super(parentShell);
		this.projectNumber = projectNumber;
		this.courseName = courseName;
		this.semester = semester;
	}

	public String getUsername() {
		return username;
	}

	public String getPassword() {
		return password;
	}

	protected Control createDialogArea(Composite parent) {
		Composite comp = (Composite) super.createDialogArea(parent);

		GridLayout layout = (GridLayout) comp.getLayout();
		layout.numColumns = 2;

		GridData data;

		projectAndCourseDescription = new Label(comp, SWT.CENTER);
		projectAndCourseDescription.setText("Submitting project " + projectNumber + " for course " + courseName + ", " + semester);
		data = new GridData(GridData.FILL, GridData.FILL, true, true, 2, 1);
		projectAndCourseDescription.setLayoutData(data);

		message = new Label(comp, SWT.LEFT);
		message.setText("Please enter your Marmoset username and password.");
		data = new GridData(GridData.FILL, GridData.FILL, true, true, 2, 1);
		message.setLayoutData(data);

		Label usernameLabel = new Label(comp, SWT.RIGHT);
		usernameLabel.setText("Username: ");

		usernameField = new Text(comp, SWT.SINGLE);
		data = new GridData(GridData.FILL_HORIZONTAL);
		usernameField.setLayoutData(data);

		Label passwordLabel = new Label(comp, SWT.RIGHT);
		passwordLabel.setText("Password: ");

		passwordField = new Text(comp, SWT.SINGLE | SWT.PASSWORD);
		data = new GridData(GridData.FILL_HORIZONTAL);
		passwordField.setLayoutData(data);

		return comp;
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("Enter Marmoset username/password");
	}
	
	@Override
	protected void okPressed() {
		this.username = usernameField.getText();
		this.password = passwordField.getText();
		super.okPressed();
	}
}
