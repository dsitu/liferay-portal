/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub.internal.notification;

import com.liferay.ai.hub.notification.AIHubAlertEventBuffer;
import com.liferay.portal.kernel.util.Time;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

import org.osgi.service.component.annotations.Component;

/**
 * @author Andrea Sbarra
 */
@Component(service = AIHubAlertEventBuffer.class)
public class AIHubAlertEventBufferImpl implements AIHubAlertEventBuffer {

	@Override
	public synchronized boolean shouldDispatch(String eventType) {
		AIHubAlertEventBucket aiHubAlertEventBucket =
			_aiHubAlertEventBucketMap.get(eventType);

		if (aiHubAlertEventBucket == null) {
			aiHubAlertEventBucket = new AIHubAlertEventBucket();

			_aiHubAlertEventBucketMap.put(eventType, aiHubAlertEventBucket);
		}

		long now = System.currentTimeMillis();

		if ((now - aiHubAlertEventBucket.getLastDispatchTime()) <
				_COOLDOWN_MILLIS) {

			return false;
		}

		Queue<Long> timestamps = aiHubAlertEventBucket.getTimestamps();

		Long timestamp = timestamps.peek();

		while ((timestamp != null) && ((now - timestamp) > _WINDOW_MILLIS)) {
			timestamps.poll();

			timestamp = timestamps.peek();
		}

		timestamps.offer(now);

		if (timestamps.size() < _THRESHOLD) {
			return false;
		}

		timestamps.clear();

		aiHubAlertEventBucket.setLastDispatchTime(now);

		return true;
	}

	private static final long _COOLDOWN_MILLIS = Time.MINUTE * 30;

	private static final int _THRESHOLD = 5;

	private static final long _WINDOW_MILLIS = Time.MINUTE * 5;

	private final Map<String, AIHubAlertEventBucket> _aiHubAlertEventBucketMap =
		new HashMap<>();

}