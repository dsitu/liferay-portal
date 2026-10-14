/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.digital.signature.rest.resource.v1_0.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.batch.engine.test.util.BatchEngineTestUtil;
import com.liferay.digital.signature.configuration.DigitalSignatureConfiguration;
import com.liferay.digital.signature.constants.DSObjectDefinitionConstants;
import com.liferay.digital.signature.constants.DSRequestConstants;
import com.liferay.digital.signature.manager.DSRequestManager;
import com.liferay.digital.signature.rest.client.dto.v1_0.SignatureRequest;
import com.liferay.digital.signature.rest.client.dto.v1_0.SignatureRequestRecipient;
import com.liferay.digital.signature.rest.client.pagination.Page;
import com.liferay.digital.signature.rest.client.pagination.Pagination;
import com.liferay.digital.signature.rest.client.problem.Problem;
import com.liferay.digital.signature.rest.client.resource.v1_0.SignatureRequestResource;
import com.liferay.document.library.kernel.model.DLFileEntry;
import com.liferay.document.library.kernel.model.DLFolderConstants;
import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.mail.kernel.model.MailMessage;
import com.liferay.mail.kernel.service.MailService;
import com.liferay.object.constants.ObjectActionKeys;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.petra.function.UnsafeRunnable;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.configuration.test.util.CompanyConfigurationTemporarySwapper;
import com.liferay.portal.kernel.model.ResourceConstants;
import com.liferay.portal.kernel.model.Role;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.model.role.RoleConstants;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.test.ReflectionTestUtil;
import com.liferay.portal.kernel.test.constants.TestDataConstants;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.RoleTestUtil;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.test.util.UserTestUtil;
import com.liferay.portal.kernel.util.ContentTypes;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.HashMapDictionaryBuilder;
import com.liferay.portal.kernel.util.HttpComponentsUtil;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.MapUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.ProxyUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.test.log.LogCapture;
import com.liferay.portal.test.log.LoggerTestUtil;
import com.liferay.portal.test.rule.FeatureFlag;
import com.liferay.portal.test.rule.Inject;

import java.io.Serializable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Danny Situ
 */
@FeatureFlag("LPD-69290")
@RunWith(Arquillian.class)
public class SignatureRequestResourceTest
	extends BaseSignatureRequestResourceTestCase {

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();

		String prefix = ".com.liferay.digital.signature.internal.batch.";

		BatchEngineTestUtil.processBatchEngineUnits(
			"com.liferay.digital.signature.impl",
			SignatureRequestResourceTest.class,
			new String[] {prefix + "01.object.definition"});

		_dsRequestDocumentObjectDefinition =
			_objectDefinitionLocalService.
				getObjectDefinitionByExternalReferenceCode(
					DSObjectDefinitionConstants.
						EXTERNAL_REFERENCE_CODE_REQUEST_DOCUMENT,
					testCompany.getCompanyId());
		_dsRequestObjectDefinition =
			_objectDefinitionLocalService.
				getObjectDefinitionByExternalReferenceCode(
					DSObjectDefinitionConstants.EXTERNAL_REFERENCE_CODE_REQUEST,
					testCompany.getCompanyId());
		_dsRequestRecipientObjectDefinition =
			_objectDefinitionLocalService.
				getObjectDefinitionByExternalReferenceCode(
					DSObjectDefinitionConstants.
						EXTERNAL_REFERENCE_CODE_REQUEST_RECIPIENT,
					testCompany.getCompanyId());

		_companyConfigurationTemporarySwapper =
			new CompanyConfigurationTemporarySwapper(
				testCompany.getCompanyId(),
				DigitalSignatureConfiguration.class.getName(),
				HashMapDictionaryBuilder.<String, Object>put(
					"accountBaseURI", RandomTestUtil.randomString()
				).put(
					"apiAccountId", RandomTestUtil.randomString()
				).put(
					"apiUsername", RandomTestUtil.randomString()
				).put(
					"embeddedViewEnabled", true
				).put(
					"enabled", true
				).put(
					"environment", RandomTestUtil.randomString()
				).put(
					"integrationKey", RandomTestUtil.randomString()
				).put(
					"rsaPrivateKey", RandomTestUtil.randomString()
				).put(
					"siteSettingsStrategy", "always-inherit"
				).build());
	}

	@After
	@Override
	public void tearDown() throws Exception {
		_companyConfigurationTemporarySwapper.close();

		super.tearDown();
	}

	@Override
	@Test
	public void testGetSignatureRequest() throws Exception {
		User user1 = UserTestUtil.addUser();

		ObjectEntry dsRequestObjectEntry = _addDSRequestObjectEntry(
			user1.getEmailAddress(), DSRequestConstants.STATUS_SENT);

		SignatureRequestResource user1SignatureRequestResource =
			_getSignatureRequestResource(user1);

		SignatureRequest signatureRequest =
			user1SignatureRequestResource.getSignatureRequest(
				dsRequestObjectEntry.getObjectEntryId());

		Assert.assertEquals(
			MapUtil.getString(dsRequestObjectEntry.getValues(), "emailBody"),
			signatureRequest.getEmailBody());

		Assert.assertEquals(
			Long.valueOf(dsRequestObjectEntry.getObjectEntryId()),
			signatureRequest.getId());

		Map<String, Map<String, String>> actions =
			signatureRequest.getActions();

		Map<String, String> signAction = actions.get("sign");

		Assert.assertEquals(
			String.valueOf(signatureRequest.getId()),
			HttpComponentsUtil.getParameter(
				signAction.get("href"), "dsRequestId", false));
		Assert.assertTrue(
			signAction.get("href"),
			StringUtil.endsWith(
				HttpComponentsUtil.getPath(signAction.get("href")),
				"/digital_signature/open_ds_request"));
		Assert.assertEquals("GET", signAction.get("method"));

		User user2 = UserTestUtil.addUser();

		SignatureRequestResource user2SignatureRequestResource =
			_getSignatureRequestResource(user2);

		_assertProblemStatus(
			"NOT_FOUND",
			() -> user2SignatureRequestResource.getSignatureRequest(
				dsRequestObjectEntry.getObjectEntryId()));
	}

	@Override
	@Test
	public void testGetSiteSignatureRequestsPage() throws Exception {
		_addDSRequestObjectEntry(
			RandomTestUtil.randomString() + "@liferay.com",
			DSRequestConstants.STATUS_SENT);

		Page<SignatureRequest> page =
			signatureRequestResource.getSiteSignatureRequestsPage(
				testGroup.getGroupId(), null, Pagination.of(1, 10));

		Assert.assertEquals(1, page.getTotalCount());

		page = signatureRequestResource.getSiteSignatureRequestsPage(
			irrelevantGroup.getGroupId(), null, Pagination.of(1, 10));

		Assert.assertEquals(0, page.getTotalCount());

		User user = UserTestUtil.addUser();

		SignatureRequestResource userSignatureRequestResource =
			_getSignatureRequestResource(user);

		page = userSignatureRequestResource.getSiteSignatureRequestsPage(
			testGroup.getGroupId(), null, Pagination.of(1, 10));

		Assert.assertEquals(0, page.getTotalCount());
	}

	@Ignore
	@Override
	@Test
	public void testGetSiteSignatureRequestsPageWithPagination()
		throws Exception {
	}

	@Ignore
	@Override
	@Test
	public void testGraphQLGetSignatureRequest() throws Exception {
	}

	@Ignore
	@Override
	@Test
	public void testGraphQLGetSiteSignatureRequestsPage() throws Exception {
	}

	@Ignore
	@Override
	@Test
	public void testGraphQLPostSignatureRequestNotification() throws Exception {
	}

	@Ignore
	@Override
	@Test
	public void testGraphQLPostSignatureRequestVoid() throws Exception {
	}

	@Ignore
	@Override
	@Test
	public void testGraphQLPostSiteSignatureRequest() throws Exception {
	}

	@Override
	@Test
	public void testPostSignatureRequestNotification() throws Exception {
		_testPostSignatureRequestNotification();
		_testPostSignatureRequestNotificationWithoutUpdatePermission();
		_testPostSignatureRequestNotificationWithoutViewPermission();
	}

	@Override
	@Test
	public void testPostSignatureRequestVoid() throws Exception {
		_testPostSignatureRequestVoidWhenRequestIsTerminal();
		_testPostSignatureRequestVoidWhenVoidReasonIsNull();
		_testPostSignatureRequestVoidWithoutUpdatePermission();
		_testPostSignatureRequestVoidWithoutViewPermission();
	}

	@Override
	@Test
	public void testPostSiteSignatureRequest() throws Exception {
		_testPostSiteSignatureRequestWithoutAddPermission();
		_testPostSiteSignatureRequestWithoutFileEntryUpdatePermission();
		_testPostSiteSignatureRequestWithoutRecipientViewPermission();
	}

	@Ignore
	@Override
	@Test
	public void testVulcanCRUDItemDelegateGetItem() throws Exception {
	}

	private ObjectEntry _addDSRequestObjectEntry(
			String emailAddress, String requestStatus)
		throws Exception {

		String languageId = LocaleUtil.toLanguageId(
			LocaleUtil.getSiteDefault());
		ServiceContext serviceContext =
			ServiceContextTestUtil.getServiceContext(
				testGroup.getGroupId(), TestPropsValues.getUserId());

		ObjectEntry objectEntry = _objectEntryLocalService.addObjectEntry(
			0, TestPropsValues.getUserId(),
			_dsRequestObjectDefinition.getObjectDefinitionId(), 0, languageId,
			HashMapBuilder.<String, Serializable>put(
				"emailBody", RandomTestUtil.randomString()
			).put(
				"emailSubject", RandomTestUtil.randomString()
			).put(
				"providerKey", "docusign"
			).put(
				"providerRequestId", RandomTestUtil.randomString()
			).put(
				"requestStatus", requestStatus
			).put(
				"siteGroupId", testGroup.getGroupId()
			).build(),
			serviceContext);

		_objectEntryLocalService.addObjectEntry(
			0, TestPropsValues.getUserId(),
			_dsRequestDocumentObjectDefinition.getObjectDefinitionId(), 0,
			languageId,
			HashMapBuilder.<String, Serializable>put(
				"fileEntryId", RandomTestUtil.randomInt()
			).put(
				"r_dsRequestToDSRequestDocuments_l_dsRequestId",
				objectEntry.getObjectEntryId()
			).build(),
			serviceContext);

		_objectEntryLocalService.addObjectEntry(
			0, TestPropsValues.getUserId(),
			_dsRequestRecipientObjectDefinition.getObjectDefinitionId(), 0,
			languageId,
			HashMapBuilder.<String, Serializable>put(
				"emailAddress", emailAddress
			).put(
				"name", RandomTestUtil.randomString()
			).put(
				"providerRecipientId", RandomTestUtil.randomString()
			).put(
				"r_dsRequestToDSRequestRecipients_l_dsRequestId",
				objectEntry.getObjectEntryId()
			).put(
				"r_userToDSRequestRecipients_userId",
				() -> {
					User user = _userLocalService.fetchUserByEmailAddress(
						testCompany.getCompanyId(), emailAddress);

					if (user == null) {
						return null;
					}

					return user.getUserId();
				}
			).put(
				"requestRecipientStatus", DSRequestConstants.STATUS_SENT
			).put(
				"signingOrder", 1
			).build(),
			serviceContext);

		return objectEntry;
	}

	private void _assertProblemStatus(
			String expectedStatus, UnsafeRunnable<Exception> unsafeRunnable)
		throws Exception {

		try (LogCapture logCapture = LoggerTestUtil.configureLog4JLogger(
				"com.liferay.portal.vulcan.internal.jaxrs.exception.mapper." +
					"WebApplicationExceptionMapper",
				LoggerTestUtil.WARN)) {

			unsafeRunnable.run();

			Assert.fail();
		}
		catch (Problem.ProblemException problemException) {
			Problem problem = problemException.getProblem();

			Assert.assertEquals(expectedStatus, problem.getStatus());
		}
	}

	private SignatureRequestResource _getSignatureRequestResource(User user)
		throws Exception {

		String password = RandomTestUtil.randomString();

		_userLocalService.updatePassword(
			user.getUserId(), password, password, false, true);

		return SignatureRequestResource.builder(
		).authentication(
			user.getEmailAddress(), password
		).endpoint(
			testCompany.getVirtualHostname(),
			PortalUtil.getPortalServerPort(false), "http"
		).locale(
			LocaleUtil.getDefault()
		).build();
	}

	private void _testPostSignatureRequestNotification() throws Exception {
		List<MailMessage> mailMessages = new ArrayList<>();

		MailService mailService =
			(MailService)ReflectionTestUtil.getAndSetFieldValue(
				_dsRequestManager, "_mailService",
				ProxyUtil.newProxyInstance(
					MailService.class.getClassLoader(),
					new Class<?>[] {MailService.class},
					(proxy, method, arguments) -> {
						if (Objects.equals(method.getName(), "sendEmail")) {
							mailMessages.add((MailMessage)arguments[0]);
						}

						return null;
					}));

		try {
			User user = UserTestUtil.addUser();

			ObjectEntry dsRequestObjectEntry = _addDSRequestObjectEntry(
				user.getEmailAddress(), DSRequestConstants.STATUS_SENT);

			signatureRequestResource.postSignatureRequestNotification(
				dsRequestObjectEntry.getObjectEntryId());

			Assert.assertEquals(
				mailMessages.toString(), 1, mailMessages.size());
		}
		finally {
			ReflectionTestUtil.setFieldValue(
				_dsRequestManager, "_mailService", mailService);
		}
	}

	private void _testPostSignatureRequestNotificationWithoutUpdatePermission()
		throws Exception {

		User user = UserTestUtil.addUser();

		ObjectEntry dsRequestObjectEntry = _addDSRequestObjectEntry(
			user.getEmailAddress(), DSRequestConstants.STATUS_SENT);

		SignatureRequestResource userSignatureRequestResource =
			_getSignatureRequestResource(user);

		_assertProblemStatus(
			"FORBIDDEN",
			() -> userSignatureRequestResource.postSignatureRequestNotification(
				dsRequestObjectEntry.getObjectEntryId()));
	}

	private void _testPostSignatureRequestNotificationWithoutViewPermission()
		throws Exception {

		User user = UserTestUtil.addUser();

		SignatureRequestResource userSignatureRequestResource =
			_getSignatureRequestResource(user);

		ObjectEntry dsRequestObjectEntry = _addDSRequestObjectEntry(
			RandomTestUtil.randomString() + "@liferay.com",
			DSRequestConstants.STATUS_SENT);

		_assertProblemStatus(
			"NOT_FOUND",
			() -> userSignatureRequestResource.postSignatureRequestNotification(
				dsRequestObjectEntry.getObjectEntryId()));
	}

	private void _testPostSignatureRequestVoidWhenRequestIsTerminal()
		throws Exception {

		ObjectEntry dsRequestObjectEntry = _addDSRequestObjectEntry(
			RandomTestUtil.randomString() + "@liferay.com",
			DSRequestConstants.STATUS_COMPLETED);

		_assertProblemStatus(
			"BAD_REQUEST",
			() -> signatureRequestResource.postSignatureRequestVoid(
				dsRequestObjectEntry.getObjectEntryId(),
				RandomTestUtil.randomString()));
	}

	private void _testPostSignatureRequestVoidWhenVoidReasonIsNull()
		throws Exception {

		ObjectEntry dsRequestObjectEntry = _addDSRequestObjectEntry(
			RandomTestUtil.randomString() + "@liferay.com",
			DSRequestConstants.STATUS_SENT);

		_assertProblemStatus(
			"BAD_REQUEST",
			() -> signatureRequestResource.postSignatureRequestVoid(
				dsRequestObjectEntry.getObjectEntryId(), StringPool.BLANK));
	}

	private void _testPostSignatureRequestVoidWithoutUpdatePermission()
		throws Exception {

		User user = UserTestUtil.addUser();

		ObjectEntry dsRequestObjectEntry = _addDSRequestObjectEntry(
			user.getEmailAddress(), DSRequestConstants.STATUS_SENT);

		SignatureRequestResource userSignatureRequestResource =
			_getSignatureRequestResource(user);

		_assertProblemStatus(
			"FORBIDDEN",
			() -> userSignatureRequestResource.postSignatureRequestVoid(
				dsRequestObjectEntry.getObjectEntryId(),
				RandomTestUtil.randomString()));
	}

	private void _testPostSignatureRequestVoidWithoutViewPermission()
		throws Exception {

		User user = UserTestUtil.addUser();

		SignatureRequestResource userSignatureRequestResource =
			_getSignatureRequestResource(user);

		ObjectEntry dsRequestObjectEntry = _addDSRequestObjectEntry(
			RandomTestUtil.randomString() + "@liferay.com",
			DSRequestConstants.STATUS_SENT);

		_assertProblemStatus(
			"NOT_FOUND",
			() -> userSignatureRequestResource.postSignatureRequestVoid(
				dsRequestObjectEntry.getObjectEntryId(),
				RandomTestUtil.randomString()));
	}

	private void _testPostSiteSignatureRequestWithoutAddPermission()
		throws Exception {

		User user = UserTestUtil.addUser(testGroup.getGroupId());

		SignatureRequestResource userSignatureRequestResource =
			_getSignatureRequestResource(user);

		_assertProblemStatus(
			"FORBIDDEN",
			() -> userSignatureRequestResource.postSiteSignatureRequest(
				testGroup.getGroupId(), randomSignatureRequest()));
	}

	private void _testPostSiteSignatureRequestWithoutFileEntryUpdatePermission()
		throws Exception {

		Role role = RoleTestUtil.addRole(RoleConstants.TYPE_REGULAR);

		RoleTestUtil.addResourcePermission(
			role, _dsRequestObjectDefinition.getResourceName(),
			ResourceConstants.SCOPE_COMPANY,
			String.valueOf(testCompany.getCompanyId()),
			ObjectActionKeys.ADD_OBJECT_ENTRY);

		User user = UserTestUtil.addUser(testGroup.getGroupId());

		_userLocalService.addRoleUser(role.getRoleId(), user);

		FileEntry fileEntry = _dlAppLocalService.addFileEntry(
			null, TestPropsValues.getUserId(), testGroup.getGroupId(),
			DLFolderConstants.DEFAULT_PARENT_FOLDER_ID,
			RandomTestUtil.randomString() + ".pdf",
			ContentTypes.APPLICATION_PDF, TestDataConstants.TEST_BYTE_ARRAY,
			null, null, null,
			ServiceContextTestUtil.getServiceContext(testGroup.getGroupId()));

		SignatureRequest signatureRequest = randomSignatureRequest();

		signatureRequest.setFileEntryIds(
			new Long[] {fileEntry.getFileEntryId()});

		SignatureRequestResource userSignatureRequestResource =
			_getSignatureRequestResource(user);

		_assertProblemStatus(
			"FORBIDDEN",
			() -> userSignatureRequestResource.postSiteSignatureRequest(
				testGroup.getGroupId(), signatureRequest));
	}

	private void _testPostSiteSignatureRequestWithoutRecipientViewPermission()
		throws Exception {

		Role role = RoleTestUtil.addRole(RoleConstants.TYPE_REGULAR);

		RoleTestUtil.addResourcePermission(
			role, _dsRequestObjectDefinition.getResourceName(),
			ResourceConstants.SCOPE_COMPANY,
			String.valueOf(testCompany.getCompanyId()),
			ObjectActionKeys.ADD_OBJECT_ENTRY);
		RoleTestUtil.addResourcePermission(
			role, DLFileEntry.class.getName(), ResourceConstants.SCOPE_GROUP,
			String.valueOf(testGroup.getGroupId()), ActionKeys.UPDATE);

		User user = UserTestUtil.addUser(testGroup.getGroupId());

		_userLocalService.addRoleUser(role.getRoleId(), user);

		FileEntry fileEntry = _dlAppLocalService.addFileEntry(
			null, TestPropsValues.getUserId(), testGroup.getGroupId(),
			DLFolderConstants.DEFAULT_PARENT_FOLDER_ID,
			RandomTestUtil.randomString() + ".pdf",
			ContentTypes.APPLICATION_PDF, TestDataConstants.TEST_BYTE_ARRAY,
			null, null, null,
			ServiceContextTestUtil.getServiceContext(testGroup.getGroupId()));

		SignatureRequest signatureRequest = randomSignatureRequest();

		signatureRequest.setFileEntryIds(
			new Long[] {fileEntry.getFileEntryId()});

		User recipientUser = UserTestUtil.addUser();

		signatureRequest.setSignatureRequestRecipients(
			new SignatureRequestRecipient[] {
				new SignatureRequestRecipient() {
					{
						setUserId(recipientUser.getUserId());
					}
				}
			});

		SignatureRequestResource userSignatureRequestResource =
			_getSignatureRequestResource(user);

		_assertProblemStatus(
			"FORBIDDEN",
			() -> userSignatureRequestResource.postSiteSignatureRequest(
				testGroup.getGroupId(), signatureRequest));
	}

	private CompanyConfigurationTemporarySwapper
		_companyConfigurationTemporarySwapper;

	@Inject
	private DLAppLocalService _dlAppLocalService;

	private ObjectDefinition _dsRequestDocumentObjectDefinition;

	@Inject
	private DSRequestManager _dsRequestManager;

	private ObjectDefinition _dsRequestObjectDefinition;
	private ObjectDefinition _dsRequestRecipientObjectDefinition;

	@Inject
	private ObjectDefinitionLocalService _objectDefinitionLocalService;

	@Inject
	private ObjectEntryLocalService _objectEntryLocalService;

	@Inject
	private UserLocalService _userLocalService;

}