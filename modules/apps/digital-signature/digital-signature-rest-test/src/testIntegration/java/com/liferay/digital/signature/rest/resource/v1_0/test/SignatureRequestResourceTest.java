/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.digital.signature.rest.resource.v1_0.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.digital.signature.configuration.DigitalSignatureConfiguration;
import com.liferay.digital.signature.rest.client.dto.v1_0.SignatureRequest;
import com.liferay.digital.signature.rest.client.pagination.Page;
import com.liferay.digital.signature.rest.client.pagination.Pagination;
import com.liferay.digital.signature.rest.client.problem.Problem;
import com.liferay.digital.signature.rest.client.resource.v1_0.SignatureRequestResource;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.model.ObjectField;
import com.liferay.object.model.ObjectRelationship;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.object.service.ObjectFieldLocalService;
import com.liferay.object.service.ObjectRelationshipLocalService;
import com.liferay.petra.function.UnsafeRunnable;
import com.liferay.portal.configuration.module.configuration.ConfigurationProvider;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.service.ServiceContext;
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
import com.liferay.portal.test.rule.Inject;

import java.io.Serializable;

import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
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

		_configurationProvider.saveCompanyConfiguration(
			DigitalSignatureConfiguration.class, testCompany.getCompanyId(),
			HashMapDictionaryBuilder.<String, Object>put(
				"accountBaseURI", "https://demo.docusign.net/restapi"
			).put(
				"apiAccountId", RandomTestUtil.randomString()
			).put(
				"apiUsername", RandomTestUtil.randomString()
			).put(
				"enabled", true
			).put(
				"enableEmbeddedView", true
			).put(
				"environment", "sandbox"
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
		_configurationProvider.deleteCompanyConfiguration(
			DigitalSignatureConfiguration.class, testCompany.getCompanyId());

		super.tearDown();
	}

	@Override
	@Test
	public void testGetSignatureRequest() throws Exception {
		_user1 = UserTestUtil.addUser();

		ObjectEntry requestObjectEntry = _addDSRequestObjectEntries(
			_user1.getEmailAddress(), "sent");

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

		Map<String, Serializable> values = requestObjectEntry.getValues();

		Assert.assertEquals(
			values.get("emailBody"), userSignatureRequest.getEmailBody());

		Assert.assertEquals(
			signatureRequest.getId(), userSignatureRequest.getId());

		_user2 = UserTestUtil.addUser();

		userSignatureRequestResource = _getSignatureRequestResource(_user2);

		try {
			userSignatureRequestResource.getSignatureRequest(
				signatureRequest.getId());

			Assert.fail();
		}
		catch (Problem.ProblemException problemException) {
			Problem problem = problemException.getProblem();

			Assert.assertEquals("NOT_FOUND", problem.getStatus());
		}
	}

	@Override
	@Test
	public void testGetSignatureRequestsAssignedToMePage() throws Exception {
		_user1 = UserTestUtil.addUser();

		_addDSRequestObjectEntries(_user1.getEmailAddress(), "sent");

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

		Assert.assertEquals("GET", signAction.get("method"));
		Assert.assertTrue(
			signAction.get("href"),
			StringUtil.endsWith(
				HttpComponentsUtil.getPath(signAction.get("href")),
				"/-/digital_signature/sign/" + signatureRequest.getId()));

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

	@Override
	@Test
	public void testGetSiteSignatureRequestsPage() throws Exception {
		_addDSRequestObjectEntries(
			RandomTestUtil.randomString() + "@liferay.com", "sent");

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

	@Override
	@Test
	public void testPatchSignatureRequest() throws Exception {
		_testPatchSignatureRequestWhenRequestIsTerminal();
		_testPatchSignatureRequestWhenStatusIsNotVoided();
		_testPatchSignatureRequestWhenUserLacksPermission();
		_testPatchSignatureRequestWhenVoidReasonIsNull();
	}

	private ObjectEntry _addDSRequestObjectEntries(
			String emailAddress, String requestStatus)
		throws Exception {

		ObjectDefinition documentObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST_DOCUMENT", testCompany.getCompanyId());
		ObjectDefinition recipientObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST_RECIPIENT", testCompany.getCompanyId());
		ObjectDefinition requestObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST", testCompany.getCompanyId());

		String languageId = LocaleUtil.toLanguageId(
			LocaleUtil.getSiteDefault());

		ServiceContext serviceContext =
			ServiceContextTestUtil.getServiceContext(
				testGroup.getGroupId(), TestPropsValues.getUserId());

		ObjectEntry requestObjectEntry =
			_objectEntryLocalService.addObjectEntry(
				0, TestPropsValues.getUserId(),
				requestObjectDefinition.getObjectDefinitionId(), 0, languageId,
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
					"siteId", testGroup.getGroupId()
				).build(),
				serviceContext);

		_objectEntryLocalService.addObjectEntry(
			0, TestPropsValues.getUserId(),
			documentObjectDefinition.getObjectDefinitionId(), 0, languageId,
			HashMapBuilder.<String, Serializable>put(
				_getRelationshipFieldName(
					requestObjectDefinition, "dsRequestToDSRequestDocuments"),
				requestObjectEntry.getObjectEntryId()
			).put(
				"fileEntryId", RandomTestUtil.randomLong()
			).build(),
			serviceContext);

		_objectEntryLocalService.addObjectEntry(
			0, TestPropsValues.getUserId(),
			recipientObjectDefinition.getObjectDefinitionId(), 0, languageId,
			HashMapBuilder.<String, Serializable>put(
				_getRelationshipFieldName(
					requestObjectDefinition, "dsRequestToDSRequestRecipients"),
				requestObjectEntry.getObjectEntryId()
			).put(
				"emailAddress", emailAddress
			).put(
				"name", RandomTestUtil.randomString()
			).put(
				"providerRecipientId", "1"
			).put(
				"requestRecipientStatus", "sent"
			).put(
				"signingOrder", 1
			).build(),
			serviceContext);

		return requestObjectEntry;
	}

	private void _assertProblemStatus(
			String expectedStatus, UnsafeRunnable<Exception> unsafeRunnable)
		throws Exception {

		try {
			unsafeRunnable.run();

			Assert.fail();
		}
		catch (Problem.ProblemException problemException) {
			Problem problem = problemException.getProblem();

			Assert.assertEquals(expectedStatus, problem.getStatus());
		}
	}

	private String _getRelationshipFieldName(
			ObjectDefinition requestObjectDefinition, String relationshipName)
		throws Exception {

		ObjectRelationship objectRelationship =
			_objectRelationshipLocalService.fetchObjectRelationship(
				requestObjectDefinition.getObjectDefinitionId(),
				relationshipName);

		ObjectField objectField = _objectFieldLocalService.getObjectField(
			objectRelationship.getObjectFieldId2());

		return objectField.getName();
	}

	private SignatureRequestResource _getSignatureRequestResource(User user) {
		return SignatureRequestResource.builder(
		).authentication(
			user.getEmailAddress(), "test"
		).endpoint(
			testCompany.getVirtualHostname(),
			PortalUtil.getPortalServerPort(false), "http"
		).locale(
			LocaleUtil.getDefault()
		).build();
	}

	private void _testPatchSignatureRequestWhenRequestIsTerminal()
		throws Exception {

		ObjectEntry requestObjectEntry = _addDSRequestObjectEntries(
			RandomTestUtil.randomString() + "@liferay.com", "completed");

		_assertProblemStatus(
			"BAD_REQUEST",
			() -> signatureRequestResource.patchSignatureRequest(
				requestObjectEntry.getObjectEntryId(),
				new SignatureRequest() {
					{
						setStatus("voided");
						setVoidReason(RandomTestUtil.randomString());
					}
				}));
	}

	private void _testPatchSignatureRequestWhenStatusIsNotVoided()
		throws Exception {

		ObjectEntry requestObjectEntry = _addDSRequestObjectEntries(
			RandomTestUtil.randomString() + "@liferay.com", "sent");

		_assertProblemStatus(
			"BAD_REQUEST",
			() -> signatureRequestResource.patchSignatureRequest(
				requestObjectEntry.getObjectEntryId(),
				new SignatureRequest() {
					{
						setStatus("completed");
					}
				}));

		SignatureRequest signatureRequest =
			signatureRequestResource.patchSignatureRequest(
				requestObjectEntry.getObjectEntryId(),
				new SignatureRequest() {
					{
						setStatus("sent");
					}
				});

		Assert.assertEquals("sent", signatureRequest.getStatus());
	}

	private void _testPatchSignatureRequestWhenUserLacksPermission()
		throws Exception {

		ObjectEntry requestObjectEntry = _addDSRequestObjectEntries(
			RandomTestUtil.randomString() + "@liferay.com", "sent");

		_user1 = UserTestUtil.addUser();

		SignatureRequestResource userSignatureRequestResource =
			_getSignatureRequestResource(_user1);

		_assertProblemStatus(
			"NOT_FOUND",
			() -> userSignatureRequestResource.patchSignatureRequest(
				requestObjectEntry.getObjectEntryId(),
				new SignatureRequest() {
					{
						setStatus("voided");
						setVoidReason(RandomTestUtil.randomString());
					}
				}));
	}

	private void _testPatchSignatureRequestWhenVoidReasonIsNull()
		throws Exception {

		ObjectEntry requestObjectEntry = _addDSRequestObjectEntries(
			RandomTestUtil.randomString() + "@liferay.com", "sent");

		_assertProblemStatus(
			"BAD_REQUEST",
			() -> signatureRequestResource.patchSignatureRequest(
				requestObjectEntry.getObjectEntryId(),
				new SignatureRequest() {
					{
						setStatus("voided");
					}
				}));
	}

	@Inject
	private ConfigurationProvider _configurationProvider;

	@Inject
	private ObjectDefinitionLocalService _objectDefinitionLocalService;

	@Inject
	private ObjectEntryLocalService _objectEntryLocalService;

	@Inject
	private ObjectFieldLocalService _objectFieldLocalService;

	@Inject
	private ObjectRelationshipLocalService _objectRelationshipLocalService;

	@DeleteAfterTestRun
	private User _user1;

	@DeleteAfterTestRun
	private User _user2;

}