package org.eclipse.gef.examples.logicdesigner;
/*
 * Licensed Material - Property of IBM
 * (C) Copyright IBM Corp. 2001, 2002 - All Rights Reserved.
 * US Government Users Restricted Rights - Use, duplication or disclosure
 * restricted by GSA ADP Schedule Contract with IBM Corp.
 */

import java.io.*;
import java.util.EventObject;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.draw2d.PositionConstants;
import org.eclipse.gef.*;
import org.eclipse.gef.dnd.TemplateTransferDragSourceListener;
import org.eclipse.gef.examples.logicdesigner.edit.GraphicalPartFactory;
import org.eclipse.gef.examples.logicdesigner.edit.TreePartFactory;
import org.eclipse.gef.examples.logicdesigner.model.LogicDiagram;
import org.eclipse.gef.internal.GEFMessages;
import org.eclipse.gef.palette.PaletteRoot;
import org.eclipse.gef.ui.actions.*;
import org.eclipse.gef.ui.palette.PaletteContextMenuProvider;
import org.eclipse.gef.ui.palette.PaletteViewerImpl;
import org.eclipse.gef.ui.parts.*;
import org.eclipse.gef.ui.stackview.CommandStackInspectorPage;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.*;
import org.eclipse.ui.actions.WorkspaceModifyOperation;
import org.eclipse.ui.dialogs.SaveAsDialog;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

public class LogicEditor 
	extends GraphicalEditorWithPalette 
{

class OutlinePage
	extends ContentOutlinePage
{
	public OutlinePage(EditPartViewer viewer){
		super(viewer);
	}
	
	protected void configureOutlineViewer(){
		getViewer().setEditDomain(getEditDomain());
		getViewer().setEditPartFactory(new TreePartFactory());
		getViewer().setContextMenuProvider(
				new LogicContextMenuProvider(LogicEditor.this, getViewer()));
		getViewer().setKeyHandler(getCommonKeyHandler());
		getViewer().addDropTargetListener(
			new LogicTemplateTransferDropTargetListener(getViewer()));
	}

	public void createControl(Composite parent){
		super.createControl(parent);
		configureOutlineViewer();
		hookOutlineViewer();
		initializeOutlineViewer();
	}
	
	public void dispose(){
		unhookOutlineViewer();
		super.dispose();
	}
	
	protected void hookOutlineViewer(){
		getSelectionSynchronizer().addViewer(getViewer());
	}

	protected void initializeOutlineViewer(){
		getViewer().setContents(getLogicDiagram());
	}
	
	protected void unhookOutlineViewer(){
		getSelectionSynchronizer().removeViewer(getViewer());
	}
}

private KeyHandler sharedKeyHandler;
private PaletteRoot root;

// This class listens to changes to the file system in the workspace, and 
// makes changes accordingly.
// 1) An open, saved file gets deleted -> close the editor
// 2) An open file gets renamed or moved -> change the editor's input accordingly	
class ResourceTracker
	implements IResourceChangeListener, IResourceDeltaVisitor
{
	public void resourceChanged(IResourceChangeEvent event) {
		IResourceDelta delta = event.getDelta();
		try {
			if (delta != null)
				delta.accept(this);
		} 
		catch (CoreException exception) {
			// What should be done here?
		}
	}	
	public boolean visit(IResourceDelta delta) { 
		if (delta == null || !delta.getResource().equals(((FileEditorInput)getEditorInput()).getFile()))
			return true;
			
		if (delta.getKind() == IResourceDelta.REMOVED) {
			if ((IResourceDelta.MOVED_TO & delta.getFlags()) == 0) { // if the file was deleted
				// NOTE: The case where an open, unsaved file is deleted is being handled by the 
				// PartListener added to the Workbench in the initialize() method.
				if (!isDirty()) 
					closeEditor(false); 
			} 
			else { // else if it was moved or renamed
				final IFile newFile = ResourcesPlugin.getWorkspace().getRoot().getFile(delta.getMovedToPath());
				Display display = getSite().getShell().getDisplay();
				display.asyncExec(new Runnable() {
					public void run() {
						superSetInput(new FileEditorInput(newFile));
					}
				});
			}
		}			
		return false; 
	}
}

private LogicDiagram logicDiagram = new LogicDiagram();
private boolean savePreviouslyNeeded = false;
private ResourceTracker resourceListener = new ResourceTracker();

private IPartListener partListener = new IPartListener() {
	// If an open, unsaved file was deleted, query the user to either do a "Save As"
	// or close the editor.
	public void partActivated(IWorkbenchPart part) {
		if (part != LogicEditor.this)
			return;
		if (!((FileEditorInput)getEditorInput()).getFile().exists()) {
			Shell shell = getSite().getShell();
			String title = GEFMessages.GraphicalEditor_FILE_DELETED_TITLE_UI;
			String message = GEFMessages.GraphicalEditor_FILE_DELETED_WITHOUT_SAVE_INFO;
			String[] buttons = { 	GEFMessages.GraphicalEditor_SAVE_BUTTON_UI, 
						   			GEFMessages.GraphicalEditor_CLOSE_BUTTON_UI };
			MessageDialog dialog = new MessageDialog(shell, title, null, message, MessageDialog.QUESTION, buttons, 0);			
			if (dialog.open() == 0) {
				if (!performSaveAs())
					partActivated(part);
			} 
			else {
				closeEditor(false);
			}
		}
	}
	public void partBroughtToTop(IWorkbenchPart part) {}
	public void partClosed(IWorkbenchPart part) {}
	public void partDeactivated(IWorkbenchPart part) {}
	public void partOpened(IWorkbenchPart part) {}
};

public LogicEditor() {
	setEditDomain(new DefaultEditDomain(this));
}

protected void closeEditor(boolean save) {
	getSite().getPage().closeEditor(LogicEditor.this, save);
}

public void commandStackChanged(EventObject event) {
	if (isDirty()){
		if (!savePreviouslyNeeded()) {
			setSavePreviouslyNeeded(true);
			firePropertyChange(IEditorPart.PROP_DIRTY);
		}
	}
	else {
		setSavePreviouslyNeeded(false);
		firePropertyChange(IEditorPart.PROP_DIRTY);
	}
	super.commandStackChanged(event);
}

/**
 * @see org.eclipse.gef.ui.parts.GraphicalEditorWithPalette#configurePaletteViewer()
 */
protected void configurePaletteViewer() {
	super.configurePaletteViewer();
	PaletteViewerImpl viewer = (PaletteViewerImpl)getPaletteViewer();
	getPaletteViewer().setContextMenuProvider(new PaletteContextMenuProvider(viewer));
	viewer.setCustomizer(new LogicPaletteCustomizer());
}


protected void configureGraphicalViewer() {
	super.configureGraphicalViewer();
	ScrollingGraphicalViewer viewer = (ScrollingGraphicalViewer)getGraphicalViewer();
	viewer.setRootEditPart(new ScalableFreeformRootEditPart());
	viewer.setEditPartFactory(new GraphicalPartFactory());
	viewer.setContextMenuProvider(new LogicContextMenuProvider(this, viewer));
	viewer.setKeyHandler(new GraphicalViewerKeyHandler(viewer)
		.setParent(getCommonKeyHandler()));
}

protected void createOutputStream(OutputStream os)throws IOException {
	ObjectOutputStream out = new ObjectOutputStream(os);
	out.writeObject(getLogicDiagram());
	out.close();	
}

public void dispose() {
	getSite().getWorkbenchWindow().getPartService().removePartListener(partListener);
	partListener = null;
	((FileEditorInput)getEditorInput()).getFile().getWorkspace().removeResourceChangeListener(resourceListener);
	super.dispose();
}

public void doSave(IProgressMonitor progressMonitor) {
	try {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		createOutputStream(out);
		IFile file = ((IFileEditorInput)getEditorInput()).getFile();
		file.setContents(new ByteArrayInputStream(out.toByteArray()), 
						true, false, progressMonitor);
		out.close();
		getCommandStack().markSaveLocation();
	} 
	catch (Exception e) {
		e.printStackTrace();
	}
}

public void doSaveAs() {
	performSaveAs();
}

public Object getAdapter(Class type){
	if (type == CommandStackInspectorPage.class)
		return new CommandStackInspectorPage(getCommandStack());
	if (type == IContentOutlinePage.class)
		return new OutlinePage(new TreeViewer());
	return super.getAdapter(type);
}

/**
 * Returns the KeyHandler with common bindings for both the Outline and Graphical Views.
 * For example, delete is a common action.
 */
protected KeyHandler getCommonKeyHandler(){
	if (sharedKeyHandler == null){
		sharedKeyHandler = new KeyHandler();
		sharedKeyHandler.put(
			KeyStroke.getPressed(SWT.DEL, 127, 0),
			getAction(DeleteAction.ID));
		sharedKeyHandler.put(
			KeyStroke.getPressed(SWT.F2, 0),
			getAction(DirectEditAction.ID));
	}
	return sharedKeyHandler;
}

protected LogicDiagram getLogicDiagram() {
	return logicDiagram;
}

protected PaletteRoot getPaletteRoot() {
	if( root == null ){
		root = LogicPlugin.createPalette();
	}
	return root;
}

public void gotoMarker(IMarker marker) {}

protected void initializeGraphicalViewer() {
	getGraphicalViewer().setContents(getLogicDiagram());
	getGraphicalViewer().addDropTargetListener(
		new LogicTemplateTransferDropTargetListener(getGraphicalViewer()));
	getGraphicalViewer().addDropTargetListener(
		new TextTransferDropTargetListener(getGraphicalViewer(), TextTransfer.getInstance()));
}

protected void initializePaletteViewer() {
	super.initializePaletteViewer();
	getPaletteViewer().addDragSourceListener(
		new TemplateTransferDragSourceListener(getPaletteViewer()));
}

protected void createActions() {
	super.createActions();
	
//	setAction(ZoomAction.ZOOM_IN, new ZoomAction(this, true));
//	setAction(ZoomAction.ZOOM_OUT, new ZoomAction(this, false));

	setAction(IncrementDecrementAction.INCREMENT, 
				new IncrementDecrementAction(this, true));
	markAsSelectionDependentAction(IncrementDecrementAction.INCREMENT, true);
	setAction(IncrementDecrementAction.DECREMENT, 
				new IncrementDecrementAction(this, false));
	markAsSelectionDependentAction(IncrementDecrementAction.DECREMENT, true);
	setAction(DirectEditAction.ID, new DirectEditAction(this));
	markAsSelectionDependentAction(DirectEditAction.ID, true);
	setAction(AlignmentAction.ID_ALIGN_LEFT, 
				new AlignmentAction(this, PositionConstants.LEFT));
	markAsSelectionDependentAction(AlignmentAction.ID_ALIGN_LEFT, true);
	setAction(AlignmentAction.ID_ALIGN_RIGHT, 
				new AlignmentAction(this, PositionConstants.RIGHT));
	markAsSelectionDependentAction(AlignmentAction.ID_ALIGN_RIGHT, true);
	setAction(AlignmentAction.ID_ALIGN_TOP, 
				new AlignmentAction(this, PositionConstants.TOP));
	markAsSelectionDependentAction(AlignmentAction.ID_ALIGN_TOP, true);
	setAction(AlignmentAction.ID_ALIGN_BOTTOM, 
				new AlignmentAction(this, PositionConstants.BOTTOM));
	markAsSelectionDependentAction(AlignmentAction.ID_ALIGN_BOTTOM, true);
	setAction(AlignmentAction.ID_ALIGN_CENTER, 
				new AlignmentAction(this, PositionConstants.CENTER));
	markAsSelectionDependentAction(AlignmentAction.ID_ALIGN_CENTER, true);
	setAction(AlignmentAction.ID_ALIGN_MIDDLE, 
				new AlignmentAction(this, PositionConstants.MIDDLE));
	markAsSelectionDependentAction(AlignmentAction.ID_ALIGN_MIDDLE, true);
}

public boolean isDirty() {
	return isSaveOnCloseNeeded();
}

public boolean isSaveAsAllowed() {
	return true;
}

public boolean isSaveOnCloseNeeded() {
	return getCommandStack().isDirty();
}

protected boolean performSaveAs() {
	SaveAsDialog dialog= new SaveAsDialog(getSite().getWorkbenchWindow().getShell());
	dialog.setOriginalFile(((IFileEditorInput)getEditorInput()).getFile());
	dialog.open();
	IPath path= dialog.getResult();
	
	if (path == null)
		return false;
	
	IWorkspace workspace= ResourcesPlugin.getWorkspace();
	final IFile file= workspace.getRoot().getFile(path);
	
	WorkspaceModifyOperation op= new WorkspaceModifyOperation() {
		public void execute(final IProgressMonitor monitor) throws CoreException {
			try {
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				createOutputStream(out);
				file.create(new ByteArrayInputStream(out.toByteArray()), true, monitor);
				out.close();
			} 
			catch (Exception e) {
				e.printStackTrace();
			}
		}
	};
	
	try {
		new ProgressMonitorDialog(getSite().getWorkbenchWindow().getShell()).run(false, true, op);
		setInput(new FileEditorInput((IFile)file));
		getCommandStack().markSaveLocation();
	} 
	catch (Exception e) {
		e.printStackTrace();
	} 
	return true;
}

private boolean savePreviouslyNeeded() {
	return savePreviouslyNeeded;
}

public void setInput(IEditorInput input) {
	superSetInput(input);

	IFile file = ((IFileEditorInput)input).getFile();
	try {
		InputStream is = file.getContents(false);
		ObjectInputStream ois = new ObjectInputStream(is);
		setLogicDiagram((LogicDiagram)ois.readObject());
		ois.close();
	}
	catch (Exception e) {
		//This is just an example.  All exceptions caught here.
		e.printStackTrace();
	}
}

public void setLogicDiagram(LogicDiagram diagram) {
	logicDiagram = diagram;
}

private void setSavePreviouslyNeeded(boolean value) {
	savePreviouslyNeeded = value;
}

protected void superSetInput(IEditorInput input) {
	// The workspace never changes for an editor.  So, removing and re-adding the 
	// resourceListener is not necessary.  But it is being done here for the sake
	// of proper implementation.  Plus, the resourceListener needs to be added 
	// to the workspace the first time around.
	if(getEditorInput() != null) {
		IFile file = ((FileEditorInput)getEditorInput()).getFile();
		file.getWorkspace().removeResourceChangeListener(resourceListener);
	}
	
	super.setInput(input);
	
	if(getEditorInput() != null) {
		IFile file = ((FileEditorInput)getEditorInput()).getFile();
		file.getWorkspace().addResourceChangeListener(resourceListener);
		setTitle(file.getName());
	}
}

protected void setSite(IWorkbenchPartSite site){
	super.setSite(site);
	getSite().getWorkbenchWindow().getPartService().addPartListener(partListener);
}

}