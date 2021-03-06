/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     E.D.Willink
 *******************************************************************************/
package org.eclipse.gef.examples.ediagram.model.properties;

import java.util.List;

import org.eclipse.emf.ecore.EReference;
import org.eclipse.gef.examples.ediagram.model.ReferenceView;

public class ReferencePropertySource extends LinkPropertySource
{
	
private final PropertyId idOppositeShown;

public ReferencePropertySource(String categoryName, Object model) {
	super(categoryName, model);
	idOppositeShown = new PropertyId(categoryName, "Opposite Shown");
}

protected void createPropertyDescriptors(List list) {
	super.createPropertyDescriptors(list);
	list.add(new BooleanPropertyDescriptor(idOppositeShown));
}

protected EReference getEReference() {
	return getReference().getEReference();
}

protected ReferenceView getReference() {
	return (ReferenceView)getModel();
}

public Object getPropertyValue(Object id) {
	if (id == idOppositeShown)
		return BooleanPropertyDescriptor.fromModel(getReference().isOppositeShown());
	return super.getPropertyValue(id);
}

public boolean isPropertyResettable(Object id) {
	return super.isPropertyResettable(id);
}

public boolean isPropertySet(Object id) {
	return super.isPropertySet(id);
}

public void resetPropertyValue(Object id) {
	super.resetPropertyValue(id);
}

public void setPropertyValue(Object id, Object value) {
	if (id == idOppositeShown)
		getReference().setOppositeShown(BooleanPropertyDescriptor.toModel(value));
	else
		super.setPropertyValue(id, value);
}

}