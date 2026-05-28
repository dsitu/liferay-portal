/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub.internal.notification;

import com.liferay.ai.hub.notification.AIHubAlertEventBuffer;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.test.ReflectionTestUtil;
import com.liferay.portal.test.rule.LiferayUnitTestRule;

import java.util.Map;
import java.util.Queue;

import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * @author Andrea Sbarra
 */
public class AIHubAlertEventBufferTest {

	@ClassRule
	@Rule
	public static final LiferayUnitTestRule liferayUnitTestRule =
		LiferayUnitTestRule.INSTANCE;

	@Before
	public void setUp() {
		_aiHubAlertEventBuffer = new AIHubAlertEventBufferImpl();
	}

	@Test
	public void testShouldDispatchCooldown() {
		for (int i = 0; i < 4; i++) {
			Assert.assertFalse(
				_aiHubAlertEventBuffer.shouldDispatch(
					_AGENT_EXTERNAL_REFERENCE_CODE, _EVENT_TYPE));
		}

		Assert.assertTrue(
			_aiHubAlertEventBuffer.shouldDispatch(
				_AGENT_EXTERNAL_REFERENCE_CODE, _EVENT_TYPE));

		Assert.assertFalse(
			_aiHubAlertEventBuffer.shouldDispatch(
				_AGENT_EXTERNAL_REFERENCE_CODE, _EVENT_TYPE));

		Map<String, AIHubAlertEventBucket> aiHubAlertEventBucketMap =
			ReflectionTestUtil.getFieldValue(
				_aiHubAlertEventBuffer, "_aiHubAlertEventBucketMap");

		AIHubAlertEventBucket aiHubAlertEventBucket =
			aiHubAlertEventBucketMap.get(
				_AGENT_EXTERNAL_REFERENCE_CODE + StringPool.POUND +
					_EVENT_TYPE);

		aiHubAlertEventBucket.setLastDispatchTime(
			System.currentTimeMillis() - (31 * 60 * 1000));

		for (int i = 0; i < 4; i++) {
			Assert.assertFalse(
				_aiHubAlertEventBuffer.shouldDispatch(
					_AGENT_EXTERNAL_REFERENCE_CODE, _EVENT_TYPE));
		}

		Assert.assertTrue(
			_aiHubAlertEventBuffer.shouldDispatch(
				_AGENT_EXTERNAL_REFERENCE_CODE, _EVENT_TYPE));
	}

	@Test
	public void testShouldDispatchPerAgent() {
		for (int i = 0; i < 4; i++) {
			Assert.assertFalse(
				_aiHubAlertEventBuffer.shouldDispatch(
					"L_CHANGE_TONE", _EVENT_TYPE));
		}

		Assert.assertFalse(
			_aiHubAlertEventBuffer.shouldDispatch(
				"L_IMPROVE_WRITING", _EVENT_TYPE));

		Assert.assertTrue(
			_aiHubAlertEventBuffer.shouldDispatch(
				"L_CHANGE_TONE", _EVENT_TYPE));
	}

	@Test
	public void testShouldDispatchPerEventType() {
		for (int i = 0; i < 4; i++) {
			Assert.assertFalse(
				_aiHubAlertEventBuffer.shouldDispatch(
					_AGENT_EXTERNAL_REFERENCE_CODE, "TYPE_A"));
		}

		Assert.assertFalse(
			_aiHubAlertEventBuffer.shouldDispatch(
				_AGENT_EXTERNAL_REFERENCE_CODE, "TYPE_B"));

		Assert.assertTrue(
			_aiHubAlertEventBuffer.shouldDispatch(
				_AGENT_EXTERNAL_REFERENCE_CODE, "TYPE_A"));
	}

	@Test
	public void testShouldDispatchThreshold() {
		for (int i = 0; i < 4; i++) {
			Assert.assertFalse(
				_aiHubAlertEventBuffer.shouldDispatch(
					_AGENT_EXTERNAL_REFERENCE_CODE, _EVENT_TYPE));
		}

		Assert.assertTrue(
			_aiHubAlertEventBuffer.shouldDispatch(
				_AGENT_EXTERNAL_REFERENCE_CODE, _EVENT_TYPE));
	}

	@Test
	public void testShouldDispatchWindow() {
		Map<String, AIHubAlertEventBucket> aiHubAlertEventBucketMap =
			ReflectionTestUtil.getFieldValue(
				_aiHubAlertEventBuffer, "_aiHubAlertEventBucketMap");

		AIHubAlertEventBucket aiHubAlertEventBucket =
			new AIHubAlertEventBucket();

		Queue<Long> timestamps = aiHubAlertEventBucket.getTimestamps();

		for (int i = 0; i < 4; i++) {
			timestamps.offer(System.currentTimeMillis() - (6 * 60 * 1000));
		}

		aiHubAlertEventBucketMap.put(
			_AGENT_EXTERNAL_REFERENCE_CODE + StringPool.POUND + _EVENT_TYPE,
			aiHubAlertEventBucket);

		Assert.assertFalse(
			_aiHubAlertEventBuffer.shouldDispatch(
				_AGENT_EXTERNAL_REFERENCE_CODE, _EVENT_TYPE));

		Assert.assertEquals(1, timestamps.size());
	}

	private static final String _AGENT_EXTERNAL_REFERENCE_CODE =
		"L_LIFERAY_SEARCH";

	private static final String _EVENT_TYPE = "AI_HUB_GUARDRAIL_ALERT";

	private AIHubAlertEventBuffer _aiHubAlertEventBuffer;

}