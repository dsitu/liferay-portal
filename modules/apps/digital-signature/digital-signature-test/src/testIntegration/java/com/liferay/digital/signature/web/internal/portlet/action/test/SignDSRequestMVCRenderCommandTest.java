/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.digital.signature.web.internal.portlet.action.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.digital.signature.configuration.DigitalSignatureConfiguration;
import com.liferay.digital.signature.constants.DSRequestConstants;
import com.liferay.digital.signature.constants.DSRequestRecipientConstants;
import com.liferay.digital.signature.constants.DigitalSignaturePortletKeys;
import com.liferay.document.library.kernel.model.DLFolderConstants;
import com.liferay.layout.test.util.ContentLayoutTestUtil;
import com.liferay.layout.test.util.LayoutTestUtil;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.portal.configuration.test.util.CompanyConfigurationTemporarySwapper;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCRenderCommand;
import com.liferay.portal.kernel.portlet.bridges.mvc.constants.MVCRenderConstants;
import com.liferay.portal.kernel.portletfilerepository.PortletFileRepository;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.test.constants.TestDataConstants;
import com.liferay.portal.kernel.test.portlet.MockLiferayPortletRenderRequest;
import com.liferay.portal.kernel.test.portlet.MockLiferayPortletRenderResponse;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.util.ContentTypes;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.HashMapDictionaryBuilder;
import com.liferay.portal.kernel.util.HttpComponentsUtil;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.test.rule.PermissionCheckerMethodTestRule;

import java.io.Serializable;

import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.mock.web.MockHttpServletResponse;

/**
 * @author Danny Situ
 */
@RunWith(Arquillian.class)
public class SignDSRequestMVCRenderCommandTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			new LiferayIntegrationTestRule(),
			PermissionCheckerMethodTestRule.INSTANCE);

	@Before
	public void setUp() throws Exception {
		_companyConfigurationTemporarySwapper =
			new CompanyConfigurationTemporarySwapper(
				TestPropsValues.getCompanyId(),
				DigitalSignatureConfiguration.class.getName(),
				HashMapDictionaryBuilder.<String, Object>put(
					"enabled", true
				).put(
					"enableEmbeddedView", true
				).put(
					"siteSettingsStrategy", "always-inherit"
				).build());

		_group = GroupTestUtil.addGroup();

		_layout = LayoutTestUtil.addTypePortletLayout(_group);
	}

	@After
	public void tearDown() throws Exception {
		_companyConfigurationTemporarySwapper.close();
	}

	@Test
	public void testRender() throws Exception {
		_testRenderWithViewInContext();
		_testRenderWithoutViewInContext();
	}

	private long _addDSRequest(long fileEntryId) throws Exception {
		ServiceContext serviceContext =
			ServiceContextTestUtil.getServiceContext(
				_group.getGroupId(), TestPropsValues.getUserId());

		ObjectEntry dsRequestObjectEntry = _addObjectEntry(
			"L_DS_REQUEST",
			HashMapBuilder.<String, Serializable>put(
				"emailSubject", RandomTestUtil.randomString()
			).put(
				"providerKey", "docusign"
			).put(
				"providerRequestId", RandomTestUtil.randomString()
			).put(
				"requestStatus", DSRequestConstants.STATUS_SENT
			).put(
				"siteGroupId", _group.getGroupId()
			).build(),
			serviceContext);

		_addObjectEntry(
			"L_DS_REQUEST_DOCUMENT",
			HashMapBuilder.<String, Serializable>put(
				"fileEntryId", fileEntryId
			).put(
				"r_dsRequestToDSRequestDocuments_l_dsRequestId",
				dsRequestObjectEntry.getObjectEntryId()
			).build(),
			serviceContext);

		_addObjectEntry(
			"L_DS_REQUEST_RECIPIENT",
			HashMapBuilder.<String, Serializable>put(
				"emailAddress",
				() -> {
					User user = TestPropsValues.getUser();

					return user.getEmailAddress();
				}
			).put(
				"name", RandomTestUtil.randomString()
			).put(
				"providerRecipientId", RandomTestUtil.randomString()
			).put(
				"r_dsRequestToDSRequestRecipients_l_dsRequestId",
				dsRequestObjectEntry.getObjectEntryId()
			).put(
				"requestRecipientStatus",
				DSRequestRecipientConstants.STATUS_SENT
			).build(),
			serviceContext);

		return dsRequestObjectEntry.getObjectEntryId();
	}

	private FileEntry _addFileEntry(long plid) throws Exception {
		return _portletFileRepository.addPortletFileEntry(
			_group.getGroupId(), TestPropsValues.getUserId(),
			Layout.class.getName(), plid,
			DigitalSignaturePortletKeys.SIGN_DIGITAL_SIGNATURE,
			DLFolderConstants.DEFAULT_PARENT_FOLDER_ID,
			TestDataConstants.TEST_BYTE_ARRAY,
			RandomTestUtil.randomString() + ".pdf",
			ContentTypes.APPLICATION_PDF, false);
	}

	private ObjectEntry _addObjectEntry(
			String externalReferenceCode, Map<String, Serializable> values,
			ServiceContext serviceContext)
		throws Exception {

		ObjectDefinition objectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					externalReferenceCode, TestPropsValues.getCompanyId());

		return _objectEntryLocalService.addObjectEntry(
			0, TestPropsValues.getUserId(),
			objectDefinition.getObjectDefinitionId(), 0,
			LocaleUtil.toLanguageId(LocaleUtil.getSiteDefault()), values,
			serviceContext);
	}

	private void _assertRedirectedToLayout(long dsRequestId, Layout layout)
		throws Exception {

		MockLiferayPortletRenderRequest mockLiferayPortletRenderRequest =
			new MockLiferayPortletRenderRequest();

		mockLiferayPortletRenderRequest.setAttribute(
			WebKeys.THEME_DISPLAY,
			ContentLayoutTestUtil.getThemeDisplay(
				_companyLocalService.getCompany(_group.getCompanyId()), _group,
				_layout));
		mockLiferayPortletRenderRequest.setParameter(
			"dsRequestId", String.valueOf(dsRequestId));

		MockLiferayPortletRenderResponse mockLiferayPortletRenderResponse =
			new MockLiferayPortletRenderResponse();

		Assert.assertEquals(
			MVCRenderConstants.MVC_PATH_VALUE_SKIP_DISPATCH,
			_mvcRenderCommand.render(
				mockLiferayPortletRenderRequest,
				mockLiferayPortletRenderResponse));

		MockHttpServletResponse mockHttpServletResponse =
			(MockHttpServletResponse)
				mockLiferayPortletRenderResponse.getHttpServletResponse();

		String redirectedURL = mockHttpServletResponse.getRedirectedUrl();

		Assert.assertTrue(
			redirectedURL, redirectedURL.contains(layout.getFriendlyURL()));
		Assert.assertFalse(
			redirectedURL,
			redirectedURL.contains("/-/digital_signature/sign/"));
		Assert.assertEquals(
			String.valueOf(dsRequestId),
			HttpComponentsUtil.getParameter(
				redirectedURL, "dsRequestId", false));
	}

	private void _testRenderWithViewInContext() throws Exception {
		Layout layout = LayoutTestUtil.addTypePortletLayout(_group);

		FileEntry fileEntry = _addFileEntry(layout.getPlid());

		_assertRedirectedToLayout(
			_addDSRequest(fileEntry.getFileEntryId()), layout);
	}

	private void _testRenderWithoutViewInContext() throws Exception {
		FileEntry fileEntry = _addFileEntry(RandomTestUtil.randomLong());

		_assertRedirectedToLayout(
			_addDSRequest(fileEntry.getFileEntryId()), _layout);
	}

	private CompanyConfigurationTemporarySwapper
		_companyConfigurationTemporarySwapper;

	@Inject
	private CompanyLocalService _companyLocalService;

	@DeleteAfterTestRun
	private Group _group;

	private Layout _layout;

	@Inject(filter = "mvc.command.name=/digital_signature/sign_ds_request")
	private MVCRenderCommand _mvcRenderCommand;

	@Inject
	private ObjectDefinitionLocalService _objectDefinitionLocalService;

	@Inject
	private ObjectEntryLocalService _objectEntryLocalService;

	@Inject
	private PortletFileRepository _portletFileRepository;

}