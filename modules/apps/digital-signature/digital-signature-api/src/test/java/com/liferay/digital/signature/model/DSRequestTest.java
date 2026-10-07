/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.digital.signature.model;

import com.liferay.digital.signature.constants.DSRequestConstants;
import com.liferay.digital.signature.constants.DSRequestRecipientConstants;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.test.rule.LiferayUnitTestRule;

import java.io.Serializable;

import java.util.Collections;
import java.util.Date;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * @author Brian I. Kim
 */
public class DSRequestTest {

	@ClassRule
	@Rule
	public static final LiferayUnitTestRule liferayUnitTestRule =
		LiferayUnitTestRule.INSTANCE;

	@Test
	public void testIsSignatureRequired() {
		String emailAddress = RandomTestUtil.randomString() + "@liferay.com";

		DSRequest dsRequest = _getDSRequest(
			emailAddress, DSRequestRecipientConstants.STATUS_SENT,
			DSRequestConstants.STATUS_SENT);

		Assert.assertTrue(dsRequest.isSignatureRequired(emailAddress));
		Assert.assertFalse(
			dsRequest.isSignatureRequired(RandomTestUtil.randomString()));

		dsRequest = _getDSRequest(
			emailAddress, DSRequestRecipientConstants.STATUS_CREATED,
			DSRequestConstants.STATUS_SENT);

		Assert.assertFalse(dsRequest.isSignatureRequired(emailAddress));

		dsRequest = _getDSRequest(
			emailAddress, DSRequestRecipientConstants.STATUS_COMPLETED,
			DSRequestConstants.STATUS_COMPLETED);

		Assert.assertFalse(dsRequest.isSignatureRequired(emailAddress));
	}

	private DSRequest _getDSRequest(
		String emailAddress, String recipientStatus, String requestStatus) {

		return new DSRequest(
			RandomTestUtil.randomLong(), new Date(),
			RandomTestUtil.randomLong(),
			Collections.singletonList(
				new DSRequestRecipient(
					HashMapBuilder.<String, Serializable>put(
						"emailAddress", emailAddress
					).put(
						"requestRecipientStatus", recipientStatus
					).build())),
			Collections.emptyList(), RandomTestUtil.randomString(),
			RandomTestUtil.randomString(), RandomTestUtil.randomLong(),
			HashMapBuilder.<String, Serializable>put(
				"requestStatus", requestStatus
			).build());
	}

}