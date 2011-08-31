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
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

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
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * Command handler for Marmoset project submission.
 * 
 * @see org.eclipse.core.commands.IHandler
 * @see org.eclipse.core.commands.AbstractHandler
 */
public class SubmitProjectHandler extends AbstractHandler {
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
		
		// OK, .submit file is valid.  Attempt the submission.
		try {
			ZipFile zipFile = createZipFile(project);
		} catch (IOException e) {
			throw new ExecutionException("Could not create zip file of project", e);
		} catch (CoreException e) {
			throw new ExecutionException("Could not create zip file of project", e);
		}
		
		
		MessageDialog.openInformation(
				window.getShell(),
				"SimpleMarmosetUploader",
				"Found " + selectedProjects.size() + " selected projects");
		
		return null;
	}

	public List<IProject> getSelectedProjects(IStructuredSelection selection) {
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
	
	private ZipFile createZipFile(IProject project) throws IOException, CoreException {
		File tempFile = File.createTempFile("marmosetSubmit", ".zip");
		//tempFile.deleteOnExit();
		
		ZipOutputStream out = null;
		
		try {
			out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(tempFile)));
		
			recursivelyAddToZipFile(project, out);
		} finally {
			IOUtil.closeQuietly(out);
		}
		
		return new ZipFile(tempFile);
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

	/**
	 * Regex pattern matching the submit url in a .submit file.
	 * All we care about is the hostname, since we will force submission via
	 * the BlueJ upload servlet.
	 */
	private static final Pattern SUBMIT_URL_PATTERN =
		Pattern.compile("^https?://([^/]+)/eclipse/SubmitProjectViaEclipse$");

	private void checkSubmitProperties(Properties submitProperties) {
		String[] required = new String[]{"projectNumber", "courseName", "semester", "submitURL"};
		for (String prop : required) {
			if (submitProperties.getProperty(prop) == null) {
				throw new IllegalArgumentException("Missing required " + prop + " property");
			}
		}
		
		// Check submit URL
		String submitUrl = submitProperties.getProperty("submitURL");
		Matcher m = SUBMIT_URL_PATTERN.matcher(submitUrl);
		if (!m.matches()) {
			throw new IllegalArgumentException("Invalid submit URL: " + submitUrl);
		}
	}
}
