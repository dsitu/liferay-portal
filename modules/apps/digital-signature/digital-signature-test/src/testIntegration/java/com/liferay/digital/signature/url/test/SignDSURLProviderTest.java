/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.digital.signature.url.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.digital.signature.constants.DigitalSignaturePortletKeys;
import com.liferay.digital.signature.url.SignDSURLProvider;
import com.liferay.layout.test.util.LayoutTestUtil;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.GroupConstants;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.test.rule.PermissionCheckerMethodTestRule;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Danny Situ
 */
@RunWith(Arquillian.class)
public class SignDSURLProviderTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			new LiferayIntegrationTestRule(),
			PermissionCheckerMethodTestRule.INSTANCE);

	@Test
	public void testGetURL() throws Exception {
		long dsRequestId = RandomTestUtil.randomLong();

		_assertPersonalAreaURL(
			_groupLocalService.getGroup(
				TestPropsValues.getCompanyId(), GroupConstants.GUEST),
			"/-/digital_signature/sign/" + dsRequestId,
			_signDSURLProvider.getURL(
				TestPropsValues.getCompanyId(), 0, dsRequestId));
	}

	@Test
	public void testGetURLWhenSignPageIsMissing() throws Exception {
		_group = GroupTestUtil.addGroup();

		long dsRequestId = RandomTestUtil.randomLong();

		_assertPersonalAreaURL(
			_group, "/-/digital_signature/sign/" + dsRequestId,
			_signDSURLProvider.getURL(
				TestPropsValues.getCompanyId(), _group.getGroupId(),
				dsRequestId));
	}

	@Test
	public void testGetURLWhenSignPageIsPresent() throws Exception {
		_group = GroupTestUtil.addGroup();

		Layout layout = LayoutTestUtil.addTypePortletLayout(_group);

		LayoutTestUtil.addPortletToLayout(
			layout, DigitalSignaturePortletKeys.SIGN_DIGITAL_SIGNATURE);

		long dsRequestId = RandomTestUtil.randomLong();

		String url = _signDSURLProvider.getURL(
			TestPropsValues.getCompanyId(), _group.getGroupId(), dsRequestId);

		Assert.assertTrue(
			url,
			url.endsWith(
				StringBundler.concat(
					_group.getFriendlyURL(), layout.getFriendlyURL(),
					"/-/digital_signature/sign/", dsRequestId)));
	}

	private void _assertPersonalAreaURL(Group group, String path, String url) {
		Assert.assertTrue(
			url,
			url.endsWith(
				StringBundler.concat(
					group.getFriendlyURL(),
					PropsUtil.get(PropsKeys.CONTROL_PANEL_LAYOUT_FRIENDLY_URL),
					path)));
	}

	@DeleteAfterTestRun
	private Group _group;

	@Inject
	private GroupLocalService _groupLocalService;

	@Inject
	private SignDSURLProvider _signDSURLProvider;

}