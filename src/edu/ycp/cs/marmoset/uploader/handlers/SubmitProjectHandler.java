package edu.ycp.cs.marmoset.uploader.handlers;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;

/**
 * Our sample handler extends AbstractHandler, an IHandler base class.
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

		if (selectedProjects == null) {
			MessageDialog.openError(window.getShell(), "No project selected", "Please select a project in the package explorer.");
		} else {
			MessageDialog.openInformation(
					window.getShell(),
					"SimpleMarmosetUploader",
					"Found " + selectedProjects.size() + " selected projects");
		}
		
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
}
