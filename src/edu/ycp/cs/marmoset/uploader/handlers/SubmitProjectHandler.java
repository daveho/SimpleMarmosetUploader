package edu.ycp.cs.marmoset.uploader.handlers;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
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

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.apache.commons.httpclient.params.HttpMethodParams;
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
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;
import org.osgi.framework.Bundle;

import edu.ycp.cs.marmoset.uploader.Activator;
import edu.ycp.cs.marmoset.uploader.ui.UsernamePasswordDialog;

/**
 * Command handler for Marmoset project submission.
 * 
 * @see org.eclipse.core.commands.IHandler
 * @see org.eclipse.core.commands.AbstractHandler
 */
public class SubmitProjectHandler extends AbstractHandler {
	private static final String PROP_SUBMIT_URL = "submitURL";
	private static final String PROP_SEMESTER = "semester";
	private static final String PROP_COURSE_NAME = "courseName";
	private static final String PROP_PROJECT_NUMBER = "projectNumber";

	private static String[] REQUIRED_PROPERTIES = new String[]{
		PROP_PROJECT_NUMBER, PROP_COURSE_NAME, PROP_SEMESTER, PROP_SUBMIT_URL
	};

	/**
	 * The constructor.
	 */
	public SubmitProjectHandler() {
	}
	
	private static class Result {
		int httpCode;
		String responseBody;
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
			File zipFile = createZipFile(project);
			
			UsernamePasswordDialog dialog = new UsernamePasswordDialog(
					window.getShell(),
					submitProperties.getProperty(PROP_PROJECT_NUMBER),
					submitProperties.getProperty(PROP_COURSE_NAME),
					submitProperties.getProperty(PROP_SEMESTER));
			
			int rc = dialog.open();
			
			if (rc == IDialogConstants.OK_ID) {
				Result result = sendZipFileToServer(submitProperties, zipFile, dialog.getUsername(), dialog.getPassword());
				
//				MessageDialog.openInformation(
//						window.getShell(),
//						"SimpleMarmosetUploader",
//						"Found " + selectedProjects.size() + " selected projects");
				MessageDialog.openInformation(window.getShell(), "Upload result", result.responseBody);
			}
		} catch (IOException e) {
			throw new ExecutionException("Could not create zip file of project", e);
		} catch (CoreException e) {
			throw new ExecutionException("Could not create zip file of project", e);
		}
		
		return null;
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

	/**
	 * Regex pattern matching the submit url in a .submit file.
	 * All we care about is the hostname, since we will force submission via
	 * the BlueJ upload servlet.
	 */
	private static final Pattern SUBMIT_URL_PATTERN =
		Pattern.compile("^(http(s)?://[^/]+)/eclipse/SubmitProjectViaEclipse$");

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

	private Result sendZipFileToServer(Properties submitProperties, File zipFile, String username, String password) throws HttpException, IOException {
		PostMethod post = null;
		HttpClient client = null;
		
		Matcher m = SUBMIT_URL_PATTERN.matcher(submitProperties.getProperty(PROP_SUBMIT_URL));
		if (!m.matches()) {
			throw new IllegalStateException(); // we've already verified that it's a match
		}
		
		String server = m.group(1);
		
		// Submit via the BlueJ submitter servlet, which is simpler than the standard Eclipse servlet
		String url = server + "/bluej/SubmitProjectViaBlueJSubmitter";
		
		
		try {
			post = new PostMethod(url);
			post.getParams().setBooleanParameter(HttpMethodParams.USE_EXPECT_CONTINUE, true);
			
			
			List<Part> parts = new ArrayList<Part>();
			
			// Add form parameters
			parts.add(new StringPart("campusUID", username));
			parts.add(new StringPart("password", password));
			parts.add(new StringPart("submitClientTool", Activator.PLUGIN_ID));
			parts.add(new StringPart("submitClientVersion", Activator.getDefault().getBundle().getVersion().toString()));
			// All submit properties except the submit URL must be added as parameters
			for (String prop : REQUIRED_PROPERTIES) {
				if (!prop.equals(PROP_SUBMIT_URL)) {
					parts.add(new StringPart(prop, submitProperties.getProperty(prop)));
				}
			}
			
			// Add the file part
			parts.add(new FilePart("submittedFiles", zipFile));
			
			MultipartRequestEntity entity = new MultipartRequestEntity(parts.toArray(new Part[parts.size()]), post.getParams());
			
			post.setRequestEntity(entity);
			
			client = new HttpClient();
			
			Result result = new Result();
			
			result.httpCode = client.executeMethod(post);
			result.responseBody = post.getResponseBodyAsString();
			
			return result;
		} finally {
			if (post != null) {
				post.releaseConnection();
			}
			if (client != null) {
				client.getHttpConnectionManager().closeIdleConnections(0L);
			}
			
		}
	}
}
