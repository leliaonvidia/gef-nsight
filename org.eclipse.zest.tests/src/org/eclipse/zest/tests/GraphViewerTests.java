/*******************************************************************************
 * Copyright (c) 2011 Fabian Steeg. All rights reserved. This program and
 * the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * <p/>
 * Contributors: Fabian Steeg - initial tests
 *******************************************************************************/
package org.eclipse.zest.tests;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.eclipse.jface.util.DelegatingDragAdapter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.zest.core.viewers.GraphViewer;

/**
 * Tests for the {@link GraphViewer} class.
 * 
 * @author Fabian Steeg (fsteeg)
 * 
 */
public class GraphViewerTests extends TestCase {

	private GraphViewer viewer;
	private Shell shell;

	/**
	 * Set up the shell and viewer to use in the tests.
	 * 
	 * @see junit.framework.TestCase#setUp()
	 */
	public void setUp() {
		shell = new Shell();
		viewer = new GraphViewer(shell, SWT.NONE);
	}

	/**
	 * Create a drop target on a viewer's control and check disposal (see
	 * https://bugs.eclipse.org/bugs/show_bug.cgi?id=200732)
	 */
	public void testDisposalWithDropTarget() {
		new DropTarget(viewer.getGraphControl(), DND.DROP_MOVE | DND.DROP_COPY);
		shell.dispose();
		Assert.assertTrue("The viewer's graph control should be disposed",
				viewer.getControl().isDisposed());
	}

	/**
	 * Create a drag source on a viewer and check disposal (see
	 * https://bugs.eclipse.org/bugs/show_bug.cgi?id=334009)
	 */
	public void testDisposalWithDragSource() {
		viewer.addDragSupport(DND.DROP_MOVE,
				new Transfer[] { TextTransfer.getInstance() },
				new DelegatingDragAdapter());
		shell.dispose();
		Assert.assertTrue("The viewer's graph control should be disposed",
				viewer.getControl().isDisposed());
	}
}
