/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.digital.signature.internal.request.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.digital.signature.configuration.DigitalSignatureConfiguration;
import com.liferay.digital.signature.model.DSDocument;
import com.liferay.digital.signature.model.DSEnvelope;
import com.liferay.digital.signature.model.DSRecipient;
import com.liferay.digital.signature.model.DSRequest;
import com.liferay.digital.signature.model.DSRequestRecipient;
import com.liferay.digital.signature.request.DSRequestManager;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.model.ObjectField;
import com.liferay.object.model.ObjectRelationship;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.object.service.ObjectFieldLocalService;
import com.liferay.object.service.ObjectRelationshipLocalService;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.configuration.module.configuration.ConfigurationProvider;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.Organization;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactoryUtil;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.test.ReflectionTestUtil;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.test.util.OrganizationTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.test.util.UserTestUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.HashMapDictionaryBuilder;
import com.liferay.portal.kernel.util.HttpComponentsUtil;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.SetUtil;
import com.liferay.portal.kernel.util.URLCodec;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.test.rule.PermissionCheckerMethodTestRule;

import java.io.Serializable;

import java.time.LocalDateTime;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Brian I. Kim
 */
@RunWith(Arquillian.class)
public class DSRequestManagerTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			new LiferayIntegrationTestRule(),
			PermissionCheckerMethodTestRule.INSTANCE);

	@Before
	public void setUp() throws Exception {
		_configurationProvider.saveCompanyConfiguration(
			DigitalSignatureConfiguration.class, TestPropsValues.getCompanyId(),
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

		_group = GroupTestUtil.addGroup();
	}

	@After
	public void tearDown() throws Exception {
		_configurationProvider.deleteCompanyConfiguration(
			DigitalSignatureConfiguration.class,
			TestPropsValues.getCompanyId());
	}

	@Test
	public void testAddDSRequest() throws Exception {
		long companyId = TestPropsValues.getCompanyId();

		long fileEntryId = RandomTestUtil.randomInt();

		DSEnvelope dsEnvelope = _createDSEnvelope(fileEntryId);

		ReflectionTestUtil.invoke(
			_dsRequestManager, "_addDSRequest",
			new Class<?>[] {
				long.class, long.class, long.class, DSEnvelope.class,
				long[].class
			},
			companyId, _group.getGroupId(), TestPropsValues.getUserId(),
			dsEnvelope, new long[] {fileEntryId});

		DSRequest dsRequest = _dsRequestManager.fetchDSRequest(
			companyId, fileEntryId);

		Assert.assertEquals(
			dsEnvelope.getEmailSubject(), dsRequest.getEmailSubject());
		Assert.assertEquals(
			Collections.singletonList(fileEntryId),
			dsRequest.getFileEntryIds());
		Assert.assertEquals(
			dsEnvelope.getDSEnvelopeId(), dsRequest.getProviderRequestId());
		Assert.assertEquals(_group.getGroupId(), dsRequest.getSiteId());
		Assert.assertEquals("sent", dsRequest.getStatus());

		Set<String> expectedEmailAddresses = new HashSet<>();

		for (DSRecipient dsRecipient : dsEnvelope.getDSRecipients()) {
			expectedEmailAddresses.add(dsRecipient.getEmailAddress());
		}

		Set<String> actualEmailAddresses = new HashSet<>();

		for (DSRequestRecipient dsRequestRecipient :
				dsRequest.getDSRequestRecipients()) {

			actualEmailAddresses.add(dsRequestRecipient.getEmailAddress());
		}

		Assert.assertEquals(expectedEmailAddresses, actualEmailAddresses);

		_organization = OrganizationTestUtil.addOrganization();

		long organizationFileEntryId = RandomTestUtil.randomInt();

		ReflectionTestUtil.invoke(
			_dsRequestManager, "_addDSRequest",
			new Class<?>[] {
				long.class, long.class, long.class, DSEnvelope.class,
				long[].class
			},
			companyId, _organization.getGroupId(), TestPropsValues.getUserId(),
			_createDSEnvelope(organizationFileEntryId),
			new long[] {organizationFileEntryId});

		dsRequest = _dsRequestManager.fetchDSRequest(
			companyId, organizationFileEntryId);

		Assert.assertEquals(0, dsRequest.getSiteId());
	}

	@Test
	public void testAddDSRequestWhenEmbeddedViewIsDisabled() throws Exception {
		_configurationProvider.saveCompanyConfiguration(
			DigitalSignatureConfiguration.class, TestPropsValues.getCompanyId(),
			HashMapDictionaryBuilder.<String, Object>put(
				"enabled", true
			).put(
				"enableEmbeddedView", false
			).put(
				"siteSettingsStrategy", "always-inherit"
			).build());

		long fileEntryId = RandomTestUtil.randomInt();

		try {
			_dsRequestManager.addDSRequest(
				TestPropsValues.getCompanyId(), _group.getGroupId(),
				TestPropsValues.getUserId(), _createDSEnvelope(fileEntryId),
				new long[] {fileEntryId});

			Assert.fail();
		}
		catch (PortalException portalException) {
			String message = portalException.getMessage();

			Assert.assertTrue(message, message.contains("not enabled"));
		}
	}

	@Test
	public void testAddDSRequestWhenFileEntryHasActiveRequest()
		throws Exception {

		long fileEntryId = RandomTestUtil.randomInt();

		ReflectionTestUtil.invoke(
			_dsRequestManager, "_addDSRequest",
			new Class<?>[] {
				long.class, long.class, long.class, DSEnvelope.class,
				long[].class
			},
			TestPropsValues.getCompanyId(), _group.getGroupId(),
			TestPropsValues.getUserId(), _createDSEnvelope(fileEntryId),
			new long[] {fileEntryId});

		try {
			_dsRequestManager.addDSRequest(
				TestPropsValues.getCompanyId(), _group.getGroupId(),
				TestPropsValues.getUserId(), _createDSEnvelope(fileEntryId),
				new long[] {fileEntryId});

			Assert.fail();
		}
		catch (PortalException portalException) {
			String message = portalException.getMessage();

			Assert.assertTrue(
				message, message.contains("already has a signature request"));
		}
	}

	@Test
	public void testContainsPermission() throws Exception {
		long companyId = TestPropsValues.getCompanyId();

		_user = UserTestUtil.addUser();

		_addDSRequestObjectEntries(
			companyId, _user.getUserId(), _user.getEmailAddress(),
			RandomTestUtil.randomInt(), "sent", "sent");

		List<DSRequest> dsRequests = _dsRequestManager.getRecipientDSRequests(
			companyId, _user.getUserId(), null, QueryUtil.ALL_POS,
			QueryUtil.ALL_POS);

		DSRequest dsRequest = dsRequests.get(0);

		PermissionChecker permissionChecker =
			PermissionCheckerFactoryUtil.create(_user);

		Assert.assertTrue(
			_dsRequestManager.containsPermission(
				permissionChecker, dsRequest, ActionKeys.UPDATE));
		Assert.assertTrue(
			_dsRequestManager.containsPermission(
				permissionChecker, dsRequest, ActionKeys.VIEW));

		_otherUser = UserTestUtil.addUser();

		permissionChecker = PermissionCheckerFactoryUtil.create(_otherUser);

		Assert.assertFalse(
			_dsRequestManager.containsPermission(
				permissionChecker, dsRequest, ActionKeys.UPDATE));
		Assert.assertFalse(
			_dsRequestManager.containsPermission(
				permissionChecker, dsRequest, ActionKeys.VIEW));
	}

	@Test
	public void testFetchDSRequest() throws Exception {
		long companyId = TestPropsValues.getCompanyId();
		long userId = TestPropsValues.getUserId();

		long fileEntryId = RandomTestUtil.randomInt();

		_addDSRequestObjectEntries(
			companyId, userId, RandomTestUtil.randomString() + "@liferay.com",
			fileEntryId, "sent", "sent");

		DSRequest dsRequest = _dsRequestManager.fetchDSRequest(
			companyId, fileEntryId);

		Assert.assertEquals("sent", dsRequest.getStatus());
		Assert.assertFalse(dsRequest.isTerminal());

		List<DSRequestRecipient> dsRequestRecipients =
			dsRequest.getDSRequestRecipients();

		Assert.assertEquals(
			dsRequestRecipients.toString(), 1, dsRequestRecipients.size());

		DSRequestRecipient dsRequestRecipient = dsRequestRecipients.get(0);

		Assert.assertEquals("sent", dsRequestRecipient.getStatus());
		Assert.assertEquals(userId, dsRequestRecipient.getUserId());

		fileEntryId = RandomTestUtil.randomInt();

		_addDSRequestObjectEntries(
			companyId, userId, RandomTestUtil.randomString() + "@liferay.com",
			fileEntryId, "completed", "completed");

		dsRequest = _dsRequestManager.fetchDSRequest(companyId, fileEntryId);

		Assert.assertEquals("completed", dsRequest.getStatus());
		Assert.assertTrue(dsRequest.isTerminal());
	}

	@Test
	public void testFetchDSRequestByRequestId() throws Exception {
		long companyId = TestPropsValues.getCompanyId();
		long userId = TestPropsValues.getUserId();

		long fileEntryId = RandomTestUtil.randomInt();

		_addDSRequestObjectEntries(
			companyId, userId, RandomTestUtil.randomString() + "@liferay.com",
			fileEntryId, "sent", "sent");

		DSRequest dsRequest = _dsRequestManager.fetchDSRequest(
			companyId, fileEntryId);

		DSRequest requestIdDSRequest = _dsRequestManager.fetchDSRequest(
			dsRequest.getDSRequestId());

		Assert.assertEquals(
			dsRequest.getDSRequestId(), requestIdDSRequest.getDSRequestId());
		Assert.assertEquals("sent", requestIdDSRequest.getStatus());
	}

	@Test
	public void testFetchDSRequestWhenExpirationDateIsPast() throws Exception {
		long fileEntryId = RandomTestUtil.randomInt();

		DSEnvelope dsEnvelope = _createDSEnvelope(fileEntryId);

		dsEnvelope.setExpireLocalDateTime(
			LocalDateTime.now(
			).minusDays(
				1
			));

		DSRequest dsRequest = ReflectionTestUtil.invoke(
			_dsRequestManager, "_addDSRequest",
			new Class<?>[] {
				long.class, long.class, long.class, DSEnvelope.class,
				long[].class
			},
			TestPropsValues.getCompanyId(), _group.getGroupId(),
			TestPropsValues.getUserId(), dsEnvelope, new long[] {fileEntryId});

		dsRequest = _dsRequestManager.fetchDSRequest(
			dsRequest.getDSRequestId());

		Assert.assertEquals("expired", dsRequest.getStatus());
		Assert.assertTrue(dsRequest.isRequestable());
		Assert.assertTrue(dsRequest.isTerminal());
	}

	@Test
	public void testGetDSRequests() throws Exception {
		long companyId = TestPropsValues.getCompanyId();

		long fileEntryId1 = RandomTestUtil.randomInt();
		long fileEntryId2 = RandomTestUtil.randomInt();

		ReflectionTestUtil.invoke(
			_dsRequestManager, "_addDSRequest",
			new Class<?>[] {
				long.class, long.class, long.class, DSEnvelope.class,
				long[].class
			},
			companyId, _group.getGroupId(), TestPropsValues.getUserId(),
			_createDSEnvelope(fileEntryId1),
			new long[] {fileEntryId1, fileEntryId2});

		Map<Long, DSRequest> dsRequests = _dsRequestManager.getDSRequests(
			companyId, ListUtil.fromArray(fileEntryId1, fileEntryId2));

		Assert.assertEquals(dsRequests.toString(), 2, dsRequests.size());

		DSRequest dsRequest1 = dsRequests.get(fileEntryId1);
		DSRequest dsRequest2 = dsRequests.get(fileEntryId2);

		Assert.assertEquals(
			dsRequest1.getProviderRequestId(),
			dsRequest2.getProviderRequestId());

		dsRequests = _dsRequestManager.getDSRequests(
			companyId, Collections.singletonList(RandomTestUtil.randomLong()));

		Assert.assertTrue(dsRequests.isEmpty());
	}

	@Test
	public void testGetLoginURL() throws Exception {
		String path = StringBundler.concat(
			"/web/", RandomTestUtil.randomString(),
			"/manage/-/digital_signature/sign/", RandomTestUtil.randomLong());
		String portalURL = StringBundler.concat(
			"http://", RandomTestUtil.randomString(), ".com");

		String url = ReflectionTestUtil.invoke(
			_dsRequestManager, "_getLoginURL", new Class<?>[] {String.class},
			portalURL + path);

		Assert.assertTrue(
			url,
			url.startsWith(
				StringBundler.concat(
					portalURL, PortalUtil.getPathMain(), "/portal/login?")));
		Assert.assertEquals(
			path,
			URLCodec.decodeURL(
				HttpComponentsUtil.getParameter(url, "redirect", false)));
	}

	@Test
	public void testGetRecipientDSRequests() throws Exception {
		long companyId = TestPropsValues.getCompanyId();

		_user = UserTestUtil.addUser();

		long fileEntryId1 = RandomTestUtil.randomInt();

		_addDSRequestObjectEntries(
			companyId, _user.getUserId(), _user.getEmailAddress(), fileEntryId1,
			"sent", "voided");

		long fileEntryId2 = RandomTestUtil.randomInt();

		_addDSRequestObjectEntries(
			companyId, _user.getUserId(), _user.getEmailAddress(), fileEntryId2,
			"sent", "sent");

		List<DSRequest> dsRequests = _dsRequestManager.getRecipientDSRequests(
			companyId, _user.getUserId(), null, QueryUtil.ALL_POS,
			QueryUtil.ALL_POS);

		Set<Long> dsRequestIds = new HashSet<>();

		for (DSRequest dsRequest : dsRequests) {
			dsRequestIds.add(dsRequest.getDSRequestId());
		}

		DSRequest dsRequest1 = _dsRequestManager.fetchDSRequest(
			companyId, fileEntryId1);
		DSRequest dsRequest2 = _dsRequestManager.fetchDSRequest(
			companyId, fileEntryId2);

		Assert.assertEquals(
			SetUtil.fromArray(
				new Long[] {
					dsRequest1.getDSRequestId(), dsRequest2.getDSRequestId()
				}),
			dsRequestIds);

		Assert.assertEquals(
			2,
			_dsRequestManager.getRecipientDSRequestsCount(
				companyId, _user.getUserId(), null));

		dsRequests = _dsRequestManager.getRecipientDSRequests(
			companyId, _user.getUserId(), null, 0, 1);

		Assert.assertEquals(dsRequests.toString(), 1, dsRequests.size());

		Assert.assertEquals(
			1,
			_dsRequestManager.getRecipientDSRequestsCount(
				companyId, _user.getUserId(), dsRequest1.getEmailSubject()));
		Assert.assertEquals(
			0,
			_dsRequestManager.getRecipientDSRequestsCount(
				companyId, _user.getUserId(), RandomTestUtil.randomString()));
	}

	@Test
	public void testIsSignatureRequired() throws Exception {
		long companyId = TestPropsValues.getCompanyId();
		long userId = TestPropsValues.getUserId();

		String emailAddress = RandomTestUtil.randomString() + "@liferay.com";
		long fileEntryId = RandomTestUtil.randomInt();

		_addDSRequestObjectEntries(
			companyId, userId, emailAddress, fileEntryId, "sent", "sent");

		DSRequest dsRequest = _dsRequestManager.fetchDSRequest(
			companyId, fileEntryId);

		Assert.assertTrue(dsRequest.isSignatureRequired(emailAddress));
		Assert.assertFalse(
			dsRequest.isSignatureRequired(RandomTestUtil.randomString()));

		fileEntryId = RandomTestUtil.randomInt();

		_addDSRequestObjectEntries(
			companyId, userId, emailAddress, fileEntryId, "completed",
			"completed");

		dsRequest = _dsRequestManager.fetchDSRequest(companyId, fileEntryId);

		Assert.assertFalse(dsRequest.isSignatureRequired(emailAddress));
	}

	@Test
	public void testSendDSRequestNotificationsWhenRequestIsTerminal()
		throws Exception {

		long companyId = TestPropsValues.getCompanyId();
		long userId = TestPropsValues.getUserId();

		long fileEntryId1 = RandomTestUtil.randomInt();

		_addDSRequestObjectEntries(
			companyId, userId, RandomTestUtil.randomString() + "@liferay.com",
			fileEntryId1, "sent", "voided");

		int count = ReflectionTestUtil.invoke(
			_dsRequestManager, "_sendDSRequestNotifications",
			new Class<?>[] {long.class, long.class, DSRequest.class}, companyId,
			_group.getGroupId(),
			_dsRequestManager.fetchDSRequest(companyId, fileEntryId1));

		Assert.assertEquals(0, count);

		long fileEntryId2 = RandomTestUtil.randomInt();

		_addDSRequestObjectEntries(
			companyId, userId, RandomTestUtil.randomString() + "@liferay.com",
			fileEntryId2, "sent", "sent");

		count = ReflectionTestUtil.invoke(
			_dsRequestManager, "_sendDSRequestNotifications",
			new Class<?>[] {long.class, long.class, DSRequest.class}, companyId,
			_group.getGroupId(),
			_dsRequestManager.fetchDSRequest(companyId, fileEntryId2));

		Assert.assertEquals(1, count);
	}

	@Test
	public void testSendDSRequestWhenExpirationWarningIsNotBeforeExpiration()
		throws Exception {

		long fileEntryId = RandomTestUtil.randomInt();

		DSEnvelope dsEnvelope = _createDSEnvelope(fileEntryId);

		dsEnvelope.setExpireAfter(5);
		dsEnvelope.setExpireWarn(5);

		try {
			_dsRequestManager.addDSRequest(
				TestPropsValues.getCompanyId(), _group.getGroupId(),
				TestPropsValues.getUserId(), dsEnvelope,
				new long[] {fileEntryId});

			Assert.fail();
		}
		catch (PortalException portalException) {
			String message = portalException.getMessage();

			Assert.assertTrue(message, message.contains("fewer than"));
		}
	}

	@Test
	public void testSendDSRequestWhenFileEntryIdsIsEmpty() throws Exception {
		try {
			_dsRequestManager.addDSRequest(
				TestPropsValues.getCompanyId(), _group.getGroupId(),
				TestPropsValues.getUserId(), new DSEnvelope(), new long[0]);

			Assert.fail();
		}
		catch (PortalException portalException) {
			String message = portalException.getMessage();

			Assert.assertTrue(message, message.contains("at least one"));
		}
	}

	private void _addDSRequestObjectEntries(
			long companyId, long userId, String emailAddress, long fileEntryId,
			String recipientStatus, String requestStatus)
		throws Exception {

		ObjectDefinition documentObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST_DOCUMENT", companyId);
		ObjectDefinition recipientObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST_RECIPIENT", companyId);
		ObjectDefinition requestObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST", companyId);

		String languageId = LocaleUtil.toLanguageId(
			LocaleUtil.getSiteDefault());

		ServiceContext serviceContext =
			ServiceContextTestUtil.getServiceContext(
				_group.getGroupId(), userId);

		ObjectEntry requestObjectEntry =
			_objectEntryLocalService.addObjectEntry(
				0, userId, requestObjectDefinition.getObjectDefinitionId(), 0,
				languageId,
				HashMapBuilder.<String, Serializable>put(
					"emailSubject", RandomTestUtil.randomString()
				).put(
					"providerKey", "docusign"
				).put(
					"providerRequestId", RandomTestUtil.randomString()
				).put(
					"requestStatus", requestStatus
				).build(),
				serviceContext);

		_objectEntryLocalService.addObjectEntry(
			0, userId, documentObjectDefinition.getObjectDefinitionId(), 0,
			languageId,
			HashMapBuilder.<String, Serializable>put(
				_getRelationshipFieldName(
					requestObjectDefinition, "dsRequestToDSRequestDocuments"),
				requestObjectEntry.getObjectEntryId()
			).put(
				"fileEntryId", fileEntryId
			).build(),
			serviceContext);

		_objectEntryLocalService.addObjectEntry(
			0, userId, recipientObjectDefinition.getObjectDefinitionId(), 0,
			languageId,
			HashMapBuilder.<String, Serializable>put(
				_getRelationshipFieldName(
					requestObjectDefinition, "dsRequestToDSRequestRecipients"),
				requestObjectEntry.getObjectEntryId()
			).put(
				"emailAddress", emailAddress
			).put(
				"name", RandomTestUtil.randomString()
			).put(
				"providerRecipientId", RandomTestUtil.randomString()
			).put(
				"r_userToDSRequestRecipients_userId", userId
			).put(
				"requestRecipientStatus", recipientStatus
			).build(),
			serviceContext);
	}

	private DSEnvelope _createDSEnvelope(long fileEntryId) {
		DSEnvelope dsEnvelope = new DSEnvelope();

		DSDocument dsDocument = new DSDocument();

		dsDocument.setDSDocumentId(String.valueOf(fileEntryId));

		dsEnvelope.setDSDocuments(ListUtil.fromArray(dsDocument));

		dsEnvelope.setDSEnvelopeId(RandomTestUtil.randomString());
		dsEnvelope.setDSRecipients(
			ListUtil.fromArray(
				_createDSRecipient("1"), _createDSRecipient("2")));
		dsEnvelope.setEmailSubject(RandomTestUtil.randomString());
		dsEnvelope.setStatus("sent");

		return dsEnvelope;
	}

	private DSRecipient _createDSRecipient(String dsRecipientId) {
		DSRecipient dsRecipient = new DSRecipient();

		dsRecipient.setDSRecipientId(dsRecipientId);
		dsRecipient.setEmailAddress(
			RandomTestUtil.randomString() + "@liferay.com");
		dsRecipient.setName(RandomTestUtil.randomString());
		dsRecipient.setStatus("sent");

		return dsRecipient;
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

	@Inject
	private ConfigurationProvider _configurationProvider;

	@Inject
	private DSRequestManager _dsRequestManager;

	@DeleteAfterTestRun
	private Group _group;

	@Inject
	private ObjectDefinitionLocalService _objectDefinitionLocalService;

	@Inject
	private ObjectEntryLocalService _objectEntryLocalService;

	@Inject
	private ObjectFieldLocalService _objectFieldLocalService;

	@Inject
	private ObjectRelationshipLocalService _objectRelationshipLocalService;

	@DeleteAfterTestRun
	private Organization _organization;

	@DeleteAfterTestRun
	private User _otherUser;

	@DeleteAfterTestRun
	private User _user;

}