/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub.internal.notification;

import java.util.LinkedList;
import java.util.Queue;

/**
 * @author Andrea Sbarra
 */
public class AIHubAlertEventBucket {

	public long getLastDispatchTime() {
		return _lastDispatchTime;
	}

	public Queue<Long> getTimestamps() {
		return _timestamps;
	}

	public void setLastDispatchTime(long lastDispatchTime) {
		_lastDispatchTime = lastDispatchTime;
	}

	private long _lastDispatchTime;
	private final Queue<Long> _timestamps = new LinkedList<>();

}