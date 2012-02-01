package edu.ycp.cs.marmoset.uploader.handlers;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

import edu.ycp.cs.marmoset.uploader.Activator;
import edu.ycp.cs.marmoset.uploader.ui.UsernamePasswordDialog;

/**
 * Command handler for Marmoset project submission.
 * 
 * @see org.eclipse.core.commands.IHandler
 * @see org.eclipse.core.commands.AbstractHandler
 * @author David Hovemeyer
 */
public class SubmitProjectHandler extends AbstractHandler {
	static final String PROP_SUBMIT_URL = "submitURL";
	private static final String PROP_SEMESTER = "semester";
	private static final String PROP_COURSE_NAME = "courseName";
	private static final String PROP_PROJECT_NUMBER = "projectNumber";

	static String[] REQUIRED_PROPERTIES = new String[]{
		PROP_PROJECT_NUMBER, PROP_COURSE_NAME, PROP_SEMESTER, PROP_SUBMIT_URL
	};

	/**
	 * Regex pattern matching the submit url in a .submit file.
	 * All we care about is the hostname and context path, since we will force submission via
	 * the BlueJ upload servlet.
	 */
	static final Pattern SUBMIT_URL_PATTERN =
		Pattern.compile("^(https?://([^/]+))(/.*)$");

	/**
	 * The constructor.
	 */
	public SubmitProjectHandler() {
	}
	
	/**
	 * the command has been executed, so extract extract the needed information
	 * from the application context.
	 */
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);

		List<IProject> selectedProjects = null;
		
		ISelectionService selectionService = HandlerUtil.getActiveWorkbenchWindow(event).getSelectionService();
		ISelection selection = selectionService.getSelection();
		
		if (selection instanceof IStructuredSelection) {
			// Accumulate selected projects
			selectedProjects = getSelectedProjects((IStructuredSelection) selection);
		}

		if (selectedProjects == null || selectedProjects.isEmpty()) {
			MessageDialog.openError(
					window.getShell(),
					"No project selected",
					"Please select a project in the package explorer.");
			return null;
		}
		
		if (selectedProjects.size() > 1) {
			MessageDialog.openError(
					window.getShell(),
					"Multiple projects selected",
					"Please select a single project in the package explorer.");
			return null;
		}
		
		IProject project = selectedProjects.get(0);
		
		IFile dotSubmit = project.getFile(".submit");
		if (!dotSubmit.exists()) {
			MessageDialog.openError(
					window.getShell(),
					"No submit file",
					"This project does not contain project submission information. Sorry.");
			return null;
		}
		
		// Read the .submit file.
		Properties submitProperties = new Properties();
		InputStream in = null;
		try {
			in = dotSubmit.getContents();
			submitProperties.load(in);
			checkSubmitProperties(submitProperties);
		} catch (Exception e) {
			MessageDialog.openError(
					window.getShell(),
					"Error reading submit file",
					"An error occurred reading the project submission information. Sorry.\n" + e.getMessage());
			return null;
		} finally {
			IOUtil.closeQuietly(in);
		}

		// Get username and password.
		int rc;
		UsernamePasswordDialog dialog = new UsernamePasswordDialog(
				window.getShell(),
				submitProperties.getProperty(PROP_PROJECT_NUMBER),
				submitProperties.getProperty(PROP_COURSE_NAME),
				submitProperties.getProperty(PROP_SEMESTER));
		rc = dialog.open();
		if (rc != IDialogConstants.OK_ID) {
			return null; // canceled
		}
		
		File zipFile = null;
		
		try {
			zipFile = createZipFile(project);
		} catch (IOException e) {
//			throw new ExecutionException("Could not create zip file of project", e);
			MessageDialog.openError(
					window.getShell(),
					"Error creating project zip file",
					"Error creating a zip file of your project\n" + e.getMessage());
			return null;
		} catch (CoreException e) {
//			throw new ExecutionException("Could not create zip file of project", e);
			MessageDialog.openError(
					window.getShell(),
					"Error creating project zip file",
					"Error creating a zip file of your project (try refreshing the project)\n\n" + e.getMessage());
			return null;
		}
		
		//  Attempt the submission.
		try {
			uploadToServer(window, submitProperties, dialog, zipFile);
			return null;
		} finally {
			if (zipFile != null) {
				// delete eagerly (even though we've marked it delete-on-exit)
				zipFile.delete();
			}
		}

	}

	public void uploadToServer(IWorkbenchWindow window,
			Properties submitProperties, UsernamePasswordDialog dialog,
			File zipFile) {
		try {
			Result result;
			result = Uploader.sendZipFileToServer(submitProperties, zipFile, dialog.getUsername(), dialog.getPassword());
			
			if (result.httpCode == HttpStatus.SC_OK) {
				// Success!
				MessageDialog.openInformation(window.getShell(), "Upload result", result.responseBody);
			} else {
				if (result.responseBody.contains("Wrong password")) {
					MessageDialog.openError(
							window.getShell(),
							"Project submission failed",
							"Project submission failed\nYour password was not recognized (did you mistype it?)");
				} else if (result.responseBody.contains("Cannot find user")) {
					MessageDialog.openError(
							window.getShell(),
							"Project submission failed",
							"Project submission failed\nYour username was not recognized (did you mistype it?)");
				} else {
					MessageDialog.openError(
							window.getShell(),
							"Project submission failed",
							"An error occurred while uploading your project. Sorry.");
					// Log it.
					Activator.getDefault().getLog().log(new Status(Status.ERROR, Activator.PLUGIN_ID, "Error submitting project: " + result.responseBody));
				}
			}
			
		} catch (HttpException e) {
			MessageDialog.openError(window.getShell(), "Error uploading project", e.getMessage());
		} catch (IOException e) {
			MessageDialog.openError(window.getShell(), "Error uploading project", e.getMessage());
		}
	}

	private List<IProject> getSelectedProjects(IStructuredSelection selection) {
		List<IProject> selectedProjects = new ArrayList<IProject>();
		List<?> selectedItems = selection.toList();
		for (Object selectedItem : selectedItems) {
			if (selectedItem instanceof IAdaptable) {
				IAdaptable adaptable = (IAdaptable) selectedItem;
				IProject project = (IProject) adaptable.getAdapter(IProject.class);
				if (project != null) {
					selectedProjects.add(project);
				}
				
			}
		}
		return selectedProjects;
	}

	private void checkSubmitProperties(Properties submitProperties) {
		for (String prop : REQUIRED_PROPERTIES) {
			if (submitProperties.getProperty(prop) == null) {
				throw new IllegalArgumentException("Missing required " + prop + " property");
			}
		}
		
		// Check submit URL
		String submitUrl = submitProperties.getProperty(PROP_SUBMIT_URL);
		Matcher m = SUBMIT_URL_PATTERN.matcher(submitUrl);
		if (!m.matches()) {
			throw new IllegalArgumentException("Invalid submit URL: " + submitUrl);
		}
	}
	
	private File createZipFile(IProject project) throws IOException, CoreException {
		File zipFile = File.createTempFile("marmosetSubmit", ".zip");
		zipFile.deleteOnExit();
		
		ZipOutputStream out = null;
		
		try {
			out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)));
		
			recursivelyAddToZipFile(project, out);
		} finally {
			IOUtil.closeQuietly(out);
		}
		
		return zipFile;
	}

	private void recursivelyAddToZipFile(IResource resource, ZipOutputStream out) throws CoreException, IOException {
		if (resource instanceof IContainer) {
			IContainer container = (IContainer) resource; 
			IResource[] children = container.members();
			
			for (IResource child : children) {
				recursivelyAddToZipFile(child, out);
			}
		} else if (resource instanceof IFile) {
			IFile file = (IFile) resource;
			
			IPath relativePath = resource.getProjectRelativePath();
			ZipEntry entry = new ZipEntry(relativePath.toPortableString());
			out.putNextEntry(entry);

			InputStream fileIn = null;
			
			try {
				fileIn = file.getContents();
				
				IOUtil.copy(fileIn, out);
			} finally {
				IOUtil.closeQuietly(fileIn);
			}
		}
		
	}
}
