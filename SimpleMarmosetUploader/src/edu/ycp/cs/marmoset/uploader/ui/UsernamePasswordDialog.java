/*
SWT/JFace in Action
GUI Design with Eclipse 3.0
Matthew Scarpino, Stephen Holder, Stanford Ng, and Laurent Mihalkovic

ISBN: 1932394273

Publisher: Manning
 */

// Adapted for SimpleMarmosetUploader

package edu.ycp.cs.marmoset.uploader.ui;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * Prompt the user for username/password.
 * Also, if the .submit file specifies multiple project numbers (inboxes),
 * show radio buttons allowing the user to choose one of them.
 * 
 * @author David Hovemeyer
 */
public class UsernamePasswordDialog extends Dialog {
	private String[] projectNumber;
	private String courseName;
	private String semester;
	private Label projectAndCourseDescription;
	private Label message;
	private Text usernameField;
	private Text passwordField;
	private String username;
	private String password;
	private List<Button> radioButtons;
	private String selectedInbox;

	/**
	 * Constructor.
	 * 
	 * @param parentShell    the parent Shell
	 * @param projectNumber  inbox: either a single inbox or a comma-separated list of inboxes
	 * @param courseName     the course name
	 * @param semester       the semester
	 */
	public UsernamePasswordDialog(Shell parentShell, String projectNumber, String courseName, String semester) {
		super(parentShell);
		this.projectNumber = projectNumber.split(","); // there can be multiple project numbers;
		this.courseName = courseName;
		this.semester = semester;
	}

	/**
	 * @return the username the user entered
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * @return the password the user entered
	 */
	public String getPassword() {
		return password;
	}
	
	/**
	 * @return the selected inbox
	 */
	public String getSelectedInbox() {
		return selectedInbox;
	}

	protected Control createDialogArea(Composite parent) {
		Composite comp = (Composite) super.createDialogArea(parent);

		GridLayout layout = (GridLayout) comp.getLayout();
		layout.numColumns = 2;

		GridData data;

		projectAndCourseDescription = new Label(comp, SWT.CENTER);
		projectAndCourseDescription.setText(getProjectTitle());
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

		if (projectNumber.length > 1) {
			// Multiple projects are available to submit.
			// Show radio buttons to allow user to choose.
			
			Label chooseProjectLabel = new Label(comp, SWT.LEFT);
			chooseProjectLabel.setText("Please choose an inbox:");
			data = new GridData(GridData.FILL, GridData.FILL, true, true, 2, 1);
			chooseProjectLabel.setLayoutData(data);

			Composite buttonGroup = new Composite(comp, 0);
			FillLayout layout2 = new FillLayout();
			layout2.type = SWT.VERTICAL;
			buttonGroup.setLayout(layout2);
			radioButtons = new ArrayList<Button>();
			
			for (String proj : projectNumber) {
				Button r = new Button(buttonGroup, SWT.RADIO);
				r.setText(proj);
				radioButtons.add(r);
			}
			
			buttonGroup.pack();
			
			data = new GridData(GridData.FILL, GridData.FILL, true, true, 2, 1);
			buttonGroup.setLayoutData(data);
		}
		
		return comp;
	}

	private String getProjectTitle() {
		if (projectNumber.length == 1) {
			// Single inbox
			return "Submitting project " + projectNumber[0] + " for course " + courseName + ", " + semester;
		} else {
			// Multiple inboxes: we'll allow the user to choose which one
			return "Submitting project for course " + courseName + ", " + semester;
		}
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("Enter Marmoset username/password");
	}
	
	@Override
	protected void buttonPressed(int buttonId) {
		if (buttonId == IDialogConstants.OK_ID) {
			// Check to make sure an inbox was selected
			if (doGetSelectedProject() == null) {
				MessageDialog.openInformation(getParentShell(), "No inbox selected", "Please choose an inbox");
				return;
			}
		}
		super.buttonPressed(buttonId);
	}
	
	@Override
	protected void okPressed() {
		this.username = usernameField.getText();
		this.password = passwordField.getText();
		this.selectedInbox = doGetSelectedProject();
		super.okPressed();
	}
	
	private String doGetSelectedProject() {
		if (projectNumber.length == 1) {
			return projectNumber[0];
		} else {
			for (int i = 0; i < projectNumber.length; i++) {
				if (radioButtons.get(i).getSelection()) {
					return projectNumber[i];
				}
			}
			return null; // okPressed will detect this
		}
	}
}
