/******************************************************************************
 * Copyright (c) 2009-2016 Telink Semiconductor Co., LTD.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * -----------------------------------------------------------------------------
 * Module:
 * Purpose:
 * Reference :   
 * $Id:  IDs.java 851 20.1.08-07 19:37:00Z innot $
 *******************************************************************************/
 /*******************************************************************************
 * Copyright (c) 2014 Liviu Ionescu.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Liviu Ionescu - initial version
 *******************************************************************************/

package com.telink.tc32eclipse.mbs;

public class IDs {

	// ------------------------------------------------------------------------

	public static String getIdPrefix() {
		
		// keep it explicitly defined, since it must not be changed, even if the
		// plug-in id is changed
		return "com.telink.tc32eclipse.core.managedbuildsystem";
	}

	public static final String TOOLCHAIN_ID = getIdPrefix()
			+ ".toolchain";

	// ------------------------------------------------------------------------

}
