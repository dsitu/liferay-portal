/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.commerce.order.content.web.internal.display.context;

/**
 * @author Danny Situ
 */
public class CommerceOrderContentConfigurationDisplayContext {

	public String getDisplayStyle() {
		return _displayStyle;
	}

	public long getDisplayStyleGroupId() {
		return _displayStyleGroupId;
	}

	public boolean isShowCommerceOrderCreateTime() {
		return _showCommerceOrderCreateTime;
	}

	public void setDisplayStyle(String displayStyle) {
		_displayStyle = displayStyle;
	}

	public void setDisplayStyleGroupId(long displayStyleGroupId) {
		_displayStyleGroupId = displayStyleGroupId;
	}

	public void setShowCommerceOrderCreateTime(
		boolean showCommerceOrderCreateTime) {

		_showCommerceOrderCreateTime = showCommerceOrderCreateTime;
	}

	private String _displayStyle;
	private long _displayStyleGroupId;
	private boolean _showCommerceOrderCreateTime;

}