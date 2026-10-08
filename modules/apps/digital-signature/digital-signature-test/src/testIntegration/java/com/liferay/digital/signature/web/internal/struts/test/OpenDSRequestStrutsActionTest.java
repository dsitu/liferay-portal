/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.digital.signature.web.internal.struts.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.layout.test.util.LayoutTestUtil;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.GroupConstants;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.model.LayoutConstants;
import com.liferay.portal.kernel.model.ResourceConstants;
import com.liferay.portal.kernel.model.Role;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.model.role.RoleConstants;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactoryUtil;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.service.LayoutLocalService;
import com.liferay.portal.kernel.service.ResourcePermissionLocalService;
import com.liferay.portal.kernel.service.RoleLocalService;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.struts.StrutsAction;
import com.liferay.portal.kernel.test.context.ContextUserReplace;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.test.util.UserTestUtil;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.HttpComponentsUtil;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.URLCodec;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;

import jakarta.portlet.WindowState;

import jakarta.servlet.http.HttpServletResponse;

import java.io.Serializable;

import java.util.Collections;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * @author Danny Situ
 */
@RunWith(Arquillian.class)
public class OpenDSRequestStrutsActionTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new LiferayIntegrationTestRule();

	@Before
	public void setUp() throws Exception {
		_group = GroupTestUtil.addGroup();
		_user = UserTestUtil.addUser();
	}

	@Test
	public void testExecute() throws Exception {
		_testExecuteAsGuest();
		_testExecuteWithoutDSRequest();
		_testExecuteWithoutSite();
		_testExecuteWithoutViewPermission();
	}

	private long _addDSRequest(long siteGroupId) throws Exception {
		ObjectDefinition objectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST", TestPropsValues.getCompanyId());

		ObjectEntry objectEntry = _objectEntryLocalService.addObjectEntry(
			0, TestPropsValues.getUserId(),
			objectDefinition.getObjectDefinitionId(), 0,
			LocaleUtil.toLanguageId(LocaleUtil.getSiteDefault()),
			HashMapBuilder.<String, Serializable>put(
				"emailSubject", RandomTestUtil.randomString()
			).put(
				"providerKey", "docusign"
			).put(
				"providerRequestId", RandomTestUtil.randomString()
			).put(
				"requestStatus", "sent"
			).put(
				"siteGroupId", siteGroupId
			).build(),
			ServiceContextTestUtil.getServiceContext(
				_group.getGroupId(), TestPropsValues.getUserId()));

		return objectEntry.getObjectEntryId();
	}

	private void _assertRedirectedToLayout(
		long dsRequestId, Layout layout,
		MockHttpServletResponse mockHttpServletResponse) {

		Assert.assertEquals(
			HttpServletResponse.SC_MOVED_TEMPORARILY,
			mockHttpServletResponse.getStatus());

		String redirectedURL = mockHttpServletResponse.getRedirectedUrl();

		Assert.assertTrue(
			redirectedURL,
			redirectedURL.contains(
				layout.getFriendlyURL() + "/-/digital_signature/sign/" +
					dsRequestId));
		Assert.assertEquals(
			WindowState.MAXIMIZED.toString(),
			HttpComponentsUtil.getParameter(redirectedURL, "p_p_state", false));
	}

	private MockHttpServletResponse _execute(
			long dsRequestId, Map<String, String> parameters, User user)
		throws Exception {

		PermissionChecker permissionChecker =
			PermissionCheckerFactoryUtil.create(user);

		try (ContextUserReplace contextUserReplace = new ContextUserReplace(
				user, permissionChecker)) {

			MockHttpServletRequest mockHttpServletRequest =
				new MockHttpServletRequest();

			mockHttpServletRequest.setParameter(
				"dsRequestId", String.valueOf(dsRequestId));
			mockHttpServletRequest.setQueryString("dsRequestId=" + dsRequestId);
			mockHttpServletRequest.setRequestURI(
				"/c/digital_signature/open_ds_request");

			for (Map.Entry<String, String> entry : parameters.entrySet()) {
				mockHttpServletRequest.setParameter(
					entry.getKey(), entry.getValue());
			}

			Company company = _companyLocalService.getCompany(
				TestPropsValues.getCompanyId());

			Group group = _groupLocalService.getGroup(
				company.getCompanyId(), GroupConstants.GUEST);

			ThemeDisplay themeDisplay = new ThemeDisplay();

			themeDisplay.setCompany(company);
			themeDisplay.setLocale(LocaleUtil.getSiteDefault());
			themeDisplay.setPermissionChecker(permissionChecker);
			themeDisplay.setPortalURL(company.getPortalURL(0));
			themeDisplay.setSignedIn(!user.isGuestUser());
			themeDisplay.setSiteGroupId(group.getGroupId());
			themeDisplay.setUser(user);

			mockHttpServletRequest.setAttribute(
				WebKeys.THEME_DISPLAY, themeDisplay);

			MockHttpServletResponse mockHttpServletResponse =
				new MockHttpServletResponse();

			_openDSRequestStrutsAction.execute(
				mockHttpServletRequest, mockHttpServletResponse);

			return mockHttpServletResponse;
		}
	}

	private void _testExecuteAsGuest() throws Exception {
		long dsRequestId = _addDSRequest(_group.getGroupId());

		MockHttpServletResponse mockHttpServletResponse = _execute(
			dsRequestId, Collections.emptyMap(),
			_userLocalService.getGuestUser(TestPropsValues.getCompanyId()));

		String redirectedURL = mockHttpServletResponse.getRedirectedUrl();

		Assert.assertTrue(
			redirectedURL, redirectedURL.contains("/portal/login"));
		Assert.assertTrue(
			redirectedURL,
			redirectedURL.contains(
				URLCodec.encodeURL("dsRequestId=" + dsRequestId)));
	}

	private void _testExecuteWithoutDSRequest() throws Exception {
		MockHttpServletResponse mockHttpServletResponse = _execute(
			RandomTestUtil.randomLong(), Collections.emptyMap(), _user);

		Assert.assertEquals(
			HttpServletResponse.SC_NOT_FOUND,
			mockHttpServletResponse.getStatus());
	}

	private void _testExecuteWithoutSite() throws Exception {
		long dsRequestId = _addDSRequest(RandomTestUtil.randomInt());

		Group group = _groupLocalService.getGroup(
			TestPropsValues.getCompanyId(), GroupConstants.GUEST);

		_assertRedirectedToLayout(
			dsRequestId,
			_layoutLocalService.fetchFirstLayout(
				group.getGroupId(), false,
				LayoutConstants.DEFAULT_PARENT_LAYOUT_ID, false),
			_execute(dsRequestId, Collections.emptyMap(), _user));
	}

	private void _testExecuteWithoutViewPermission() throws Exception {
		Layout layout1 = LayoutTestUtil.addTypePortletLayout(_group);
		Layout layout2 = LayoutTestUtil.addTypePortletLayout(_group);

		Role role = _roleLocalService.getRole(
			TestPropsValues.getCompanyId(), RoleConstants.GUEST);

		_resourcePermissionLocalService.removeResourcePermission(
			TestPropsValues.getCompanyId(), Layout.class.getName(),
			ResourceConstants.SCOPE_INDIVIDUAL,
			String.valueOf(layout1.getPlid()), role.getRoleId(),
			ActionKeys.VIEW);

		long dsRequestId = _addDSRequest(_group.getGroupId());

		_assertRedirectedToLayout(
			dsRequestId, layout2,
			_execute(dsRequestId, Collections.emptyMap(), _user));
	}

	@Inject
	private CompanyLocalService _companyLocalService;

	@DeleteAfterTestRun
	private Group _group;

	@Inject
	private GroupLocalService _groupLocalService;

	@Inject
	private LayoutLocalService _layoutLocalService;

	@Inject
	private ObjectDefinitionLocalService _objectDefinitionLocalService;

	@Inject
	private ObjectEntryLocalService _objectEntryLocalService;

	@Inject(filter = "path=/digital_signature/open_ds_request")
	private StrutsAction _openDSRequestStrutsAction;

	@Inject
	private ResourcePermissionLocalService _resourcePermissionLocalService;

	@Inject
	private RoleLocalService _roleLocalService;

	@DeleteAfterTestRun
	private User _user;

	@Inject
	private UserLocalService _userLocalService;

}