/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.digital.signature.rest.resource.v1_0.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.digital.signature.configuration.DigitalSignatureConfiguration;
import com.liferay.digital.signature.constants.DSRequestConstants;
import com.liferay.digital.signature.constants.DSRequestRecipientConstants;
import com.liferay.digital.signature.rest.client.dto.v1_0.SignatureRequest;
import com.liferay.digital.signature.rest.client.pagination.Page;
import com.liferay.digital.signature.rest.client.pagination.Pagination;
import com.liferay.digital.signature.rest.client.problem.Problem;
import com.liferay.digital.signature.rest.client.resource.v1_0.SignatureRequestResource;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.petra.function.UnsafeRunnable;
import com.liferay.portal.configuration.test.util.CompanyConfigurationTemporarySwapper;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.test.util.UserTestUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.HashMapDictionaryBuilder;
import com.liferay.portal.kernel.util.HttpComponentsUtil;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.test.log.LogCapture;
import com.liferay.portal.test.log.LoggerTestUtil;
import com.liferay.portal.test.rule.Inject;

import java.io.Serializable;

import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Danny Situ
 */
@RunWith(Arquillian.class)
public class SignatureRequestResourceTest
	extends BaseSignatureRequestResourceTestCase {

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();

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
		_user1 = UserTestUtil.addUser();

		ObjectEntry dsRequestObjectEntry = _addDSRequestObjectEntries(
			_user1.getEmailAddress(), DSRequestConstants.STATUS_SENT);

		SignatureRequestResource userSignatureRequestResource =
			_getSignatureRequestResource(_user1);

		Page<SignatureRequest> page =
			userSignatureRequestResource.getSignatureRequestsAssignedToMePage(
				null, Pagination.of(1, 10));

		List<SignatureRequest> signatureRequests =
			(List<SignatureRequest>)page.getItems();

		SignatureRequest signatureRequest = signatureRequests.get(0);

		SignatureRequest userSignatureRequest =
			userSignatureRequestResource.getSignatureRequest(
				signatureRequest.getId());

		Map<String, Serializable> values = dsRequestObjectEntry.getValues();

		Assert.assertEquals(
			values.get("emailBody"), userSignatureRequest.getEmailBody());

		Assert.assertEquals(
			signatureRequest.getId(), userSignatureRequest.getId());

		_user2 = UserTestUtil.addUser();

		SignatureRequestResource user2SignatureRequestResource =
			_getSignatureRequestResource(_user2);

		_assertProblemStatus(
			"NOT_FOUND",
			() -> user2SignatureRequestResource.getSignatureRequest(
				signatureRequest.getId()));
	}

	@Override
	@Test
	public void testGetSignatureRequestsAssignedToMePage() throws Exception {
		_user1 = UserTestUtil.addUser();

		_addDSRequestObjectEntries(
			_user1.getEmailAddress(), DSRequestConstants.STATUS_SENT);

		SignatureRequestResource userSignatureRequestResource =
			_getSignatureRequestResource(_user1);

		Page<SignatureRequest> page =
			userSignatureRequestResource.getSignatureRequestsAssignedToMePage(
				null, Pagination.of(1, 10));

		Assert.assertEquals(1, page.getTotalCount());

		List<SignatureRequest> signatureRequests =
			(List<SignatureRequest>)page.getItems();

		SignatureRequest signatureRequest = signatureRequests.get(0);

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

		page =
			userSignatureRequestResource.getSignatureRequestsAssignedToMePage(
				StringUtil.toUpperCase(signatureRequest.getEmailSubject()),
				Pagination.of(1, 10));

		Assert.assertEquals(1, page.getTotalCount());

		page =
			userSignatureRequestResource.getSignatureRequestsAssignedToMePage(
				RandomTestUtil.randomString(), Pagination.of(1, 10));

		Assert.assertEquals(0, page.getTotalCount());

		_user2 = UserTestUtil.addUser();

		userSignatureRequestResource = _getSignatureRequestResource(_user2);

		page =
			userSignatureRequestResource.getSignatureRequestsAssignedToMePage(
				null, Pagination.of(1, 10));

		Assert.assertEquals(0, page.getTotalCount());
	}

	@Ignore
	@Override
	@Test
	public void testGetSignatureRequestsAssignedToMePageWithPagination()
		throws Exception {
	}

	@Override
	@Test
	public void testGetSiteSignatureRequestsPage() throws Exception {
		_addDSRequestObjectEntries(
			RandomTestUtil.randomString() + "@liferay.com",
			DSRequestConstants.STATUS_SENT);

		Page<SignatureRequest> page =
			signatureRequestResource.getSiteSignatureRequestsPage(
				testGroup.getGroupId(), null, Pagination.of(1, 10));

		Assert.assertEquals(1, page.getTotalCount());

		page = signatureRequestResource.getSiteSignatureRequestsPage(
			irrelevantGroup.getGroupId(), null, Pagination.of(1, 10));

		Assert.assertEquals(0, page.getTotalCount());

		_user1 = UserTestUtil.addUser();

		SignatureRequestResource userSignatureRequestResource =
			_getSignatureRequestResource(_user1);

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
	public void testGraphQLGetSignatureRequestsAssignedToMePage()
		throws Exception {
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
	public void testGraphQLPostSiteSignatureRequest() throws Exception {
	}

	@Override
	@Test
	public void testPatchSignatureRequest() throws Exception {
		_testPatchSignatureRequestWhenRequestIsTerminal();
		_testPatchSignatureRequestWhenStatusIsNotVoided();
		_testPatchSignatureRequestWhenVoidReasonIsNull();
		_testPatchSignatureRequestWithoutUpdatePermission();
		_testPatchSignatureRequestWithoutViewPermission();
	}

	@Ignore
	@Override
	@Test
	public void testPostSignatureRequestNotification() throws Exception {
	}

	@Override
	@Test
	public void testPostSiteSignatureRequest() throws Exception {
		_user1 = UserTestUtil.addUser(testGroup.getGroupId());

		SignatureRequestResource userSignatureRequestResource =
			_getSignatureRequestResource(_user1);

		_assertProblemStatus(
			"FORBIDDEN",
			() -> userSignatureRequestResource.postSiteSignatureRequest(
				testGroup.getGroupId(), randomSignatureRequest()));
	}

	@Ignore
	@Override
	@Test
	public void testVulcanCRUDItemDelegateGetItem() throws Exception {
	}

	private ObjectEntry _addDSRequestObjectEntries(
			String emailAddress, String requestStatus)
		throws Exception {

		ObjectDefinition dsRequestObjectDefinition = _getObjectDefinition(
			"L_DS_REQUEST");
		String languageId = LocaleUtil.toLanguageId(
			LocaleUtil.getSiteDefault());
		ServiceContext serviceContext =
			ServiceContextTestUtil.getServiceContext(
				testGroup.getGroupId(), TestPropsValues.getUserId());

		ObjectEntry dsRequestObjectEntry =
			_objectEntryLocalService.addObjectEntry(
				0, TestPropsValues.getUserId(),
				dsRequestObjectDefinition.getObjectDefinitionId(), 0,
				languageId,
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

		ObjectDefinition dsRequestDocumentObjectDefinition =
			_getObjectDefinition("L_DS_REQUEST_DOCUMENT");

		_objectEntryLocalService.addObjectEntry(
			0, TestPropsValues.getUserId(),
			dsRequestDocumentObjectDefinition.getObjectDefinitionId(), 0,
			languageId,
			HashMapBuilder.<String, Serializable>put(
				"fileEntryId", RandomTestUtil.randomInt()
			).put(
				"r_dsRequestToDSRequestDocuments_l_dsRequestId",
				dsRequestObjectEntry.getObjectEntryId()
			).build(),
			serviceContext);

		ObjectDefinition dsRequestRecipientObjectDefinition =
			_getObjectDefinition("L_DS_REQUEST_RECIPIENT");

		_objectEntryLocalService.addObjectEntry(
			0, TestPropsValues.getUserId(),
			dsRequestRecipientObjectDefinition.getObjectDefinitionId(), 0,
			languageId,
			HashMapBuilder.<String, Serializable>put(
				"emailAddress", emailAddress
			).put(
				"name", RandomTestUtil.randomString()
			).put(
				"providerRecipientId", "1"
			).put(
				"r_dsRequestToDSRequestRecipients_l_dsRequestId",
				dsRequestObjectEntry.getObjectEntryId()
			).put(
				"requestRecipientStatus",
				DSRequestRecipientConstants.STATUS_SENT
			).put(
				"signingOrder", 1
			).build(),
			serviceContext);

		return dsRequestObjectEntry;
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

	private ObjectDefinition _getObjectDefinition(String externalReferenceCode)
		throws Exception {

		return _objectDefinitionLocalService.
			getObjectDefinitionByExternalReferenceCode(
				externalReferenceCode, testCompany.getCompanyId());
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

	private void _testPatchSignatureRequestWhenRequestIsTerminal()
		throws Exception {

		ObjectEntry dsRequestObjectEntry = _addDSRequestObjectEntries(
			RandomTestUtil.randomString() + "@liferay.com",
			DSRequestConstants.STATUS_COMPLETED);

		_assertProblemStatus(
			"BAD_REQUEST",
			() -> signatureRequestResource.patchSignatureRequest(
				dsRequestObjectEntry.getObjectEntryId(),
				new SignatureRequest() {
					{
						setStatus(DSRequestConstants.STATUS_VOIDED);
						setVoidReason(RandomTestUtil.randomString());
					}
				}));
	}

	private void _testPatchSignatureRequestWhenStatusIsNotVoided()
		throws Exception {

		ObjectEntry dsRequestObjectEntry = _addDSRequestObjectEntries(
			RandomTestUtil.randomString() + "@liferay.com",
			DSRequestConstants.STATUS_SENT);

		_assertProblemStatus(
			"BAD_REQUEST",
			() -> signatureRequestResource.patchSignatureRequest(
				dsRequestObjectEntry.getObjectEntryId(),
				new SignatureRequest() {
					{
						setStatus(DSRequestConstants.STATUS_COMPLETED);
					}
				}));

		SignatureRequest signatureRequest =
			signatureRequestResource.patchSignatureRequest(
				dsRequestObjectEntry.getObjectEntryId(),
				new SignatureRequest() {
					{
						setStatus(DSRequestConstants.STATUS_SENT);
					}
				});

		Assert.assertEquals(
			DSRequestConstants.STATUS_SENT, signatureRequest.getStatus());
	}

	private void _testPatchSignatureRequestWhenVoidReasonIsNull()
		throws Exception {

		ObjectEntry dsRequestObjectEntry = _addDSRequestObjectEntries(
			RandomTestUtil.randomString() + "@liferay.com",
			DSRequestConstants.STATUS_SENT);

		_assertProblemStatus(
			"BAD_REQUEST",
			() -> signatureRequestResource.patchSignatureRequest(
				dsRequestObjectEntry.getObjectEntryId(),
				new SignatureRequest() {
					{
						setStatus(DSRequestConstants.STATUS_VOIDED);
					}
				}));
	}

	private void _testPatchSignatureRequestWithoutUpdatePermission()
		throws Exception {

		_user1 = UserTestUtil.addUser();

		ObjectEntry dsRequestObjectEntry = _addDSRequestObjectEntries(
			_user1.getEmailAddress(), DSRequestConstants.STATUS_SENT);

		SignatureRequestResource userSignatureRequestResource =
			_getSignatureRequestResource(_user1);

		_assertProblemStatus(
			"FORBIDDEN",
			() -> userSignatureRequestResource.patchSignatureRequest(
				dsRequestObjectEntry.getObjectEntryId(),
				new SignatureRequest() {
					{
						setStatus(DSRequestConstants.STATUS_VOIDED);
						setVoidReason(RandomTestUtil.randomString());
					}
				}));
	}

	private void _testPatchSignatureRequestWithoutViewPermission()
		throws Exception {

		ObjectEntry dsRequestObjectEntry = _addDSRequestObjectEntries(
			RandomTestUtil.randomString() + "@liferay.com",
			DSRequestConstants.STATUS_SENT);

		_user1 = UserTestUtil.addUser();

		SignatureRequestResource userSignatureRequestResource =
			_getSignatureRequestResource(_user1);

		_assertProblemStatus(
			"NOT_FOUND",
			() -> userSignatureRequestResource.patchSignatureRequest(
				dsRequestObjectEntry.getObjectEntryId(),
				new SignatureRequest() {
					{
						setStatus(DSRequestConstants.STATUS_VOIDED);
						setVoidReason(RandomTestUtil.randomString());
					}
				}));
	}

	private CompanyConfigurationTemporarySwapper
		_companyConfigurationTemporarySwapper;

	@Inject
	private ObjectDefinitionLocalService _objectDefinitionLocalService;

	@Inject
	private ObjectEntryLocalService _objectEntryLocalService;

	@DeleteAfterTestRun
	private User _user1;

	@DeleteAfterTestRun
	private User _user2;

	@Inject
	private UserLocalService _userLocalService;

}