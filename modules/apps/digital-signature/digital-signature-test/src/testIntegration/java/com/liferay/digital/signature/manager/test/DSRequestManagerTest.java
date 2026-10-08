/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.digital.signature.manager.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.digital.signature.configuration.DigitalSignatureConfiguration;
import com.liferay.digital.signature.constants.DSRequestConstants;
import com.liferay.digital.signature.constants.DSRequestRecipientConstants;
import com.liferay.digital.signature.manager.DSEnvelopeManager;
import com.liferay.digital.signature.manager.DSRequestManager;
import com.liferay.digital.signature.model.DSEnvelope;
import com.liferay.digital.signature.model.DSRecipient;
import com.liferay.digital.signature.model.DSRequest;
import com.liferay.digital.signature.model.DSRequestRecipient;
import com.liferay.object.constants.ObjectDefinitionConstants;
import com.liferay.object.exception.ObjectEntryValuesException;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.rest.filter.factory.FilterFactory;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.petra.function.transform.TransformUtil;
import com.liferay.petra.sql.dsl.expression.Predicate;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.configuration.test.util.CompanyConfigurationTemporarySwapper;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.model.Organization;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactoryUtil;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.test.AssertUtils;
import com.liferay.portal.kernel.test.ReflectionTestUtil;
import com.liferay.portal.kernel.test.context.ContextUserReplace;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.OrganizationTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.test.util.UserTestUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.HashMapDictionaryBuilder;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.MapUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.ProxyUtil;
import com.liferay.portal.kernel.util.SetUtil;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.test.rule.PermissionCheckerMethodTestRule;

import java.io.Serializable;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

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
		_companyConfigurationTemporarySwapper =
			new CompanyConfigurationTemporarySwapper(
				TestPropsValues.getCompanyId(),
				DigitalSignatureConfiguration.class.getName(),
				HashMapDictionaryBuilder.<String, Object>put(
					"accountBaseURI", RandomTestUtil.randomString()
				).put(
					"apiAccountId", RandomTestUtil.randomString()
				).put(
					"apiUsername", RandomTestUtil.randomString()
				).put(
					"enabled", true
				).put(
					"enableEmbeddedView", true
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
	public void tearDown() throws Exception {
		_companyConfigurationTemporarySwapper.close();
	}

	@Test
	public void testAddDSRequest() throws Exception {
		DSEnvelope dsEnvelope1 = _getDSEnvelope();
		long fileEntryId1 = RandomTestUtil.randomInt();

		_addDSRequest(dsEnvelope1, fileEntryId1);

		DSRequest dsRequest = _dsRequestManager.fetchDSRequest(
			TestPropsValues.getCompanyId(), fileEntryId1);

		Assert.assertEquals(
			dsEnvelope1.getEmailSubject(), dsRequest.getEmailSubject());
		Assert.assertEquals(
			Collections.singletonList(fileEntryId1),
			dsRequest.getFileEntryIds());
		Assert.assertEquals(
			dsEnvelope1.getDSEnvelopeId(), dsRequest.getProviderRequestId());
		Assert.assertEquals(
			TestPropsValues.getGroupId(), dsRequest.getSiteGroupId());
		Assert.assertEquals(
			DSRequestConstants.STATUS_SENT, dsRequest.getStatus());

		Assert.assertEquals(
			new HashSet<>(
				TransformUtil.transform(
					dsEnvelope1.getDSRecipients(),
					DSRecipient::getEmailAddress)),
			new HashSet<>(
				TransformUtil.transform(
					dsRequest.getDSRequestRecipients(),
					DSRequestRecipient::getEmailAddress)));

		_organization = OrganizationTestUtil.addOrganization();

		long fileEntryId2 = RandomTestUtil.randomInt();

		ReflectionTestUtil.invoke(
			_dsRequestManager, "_addDSRequest",
			new Class<?>[] {
				long.class, long.class, long.class, DSEnvelope.class,
				long[].class
			},
			TestPropsValues.getCompanyId(), _organization.getGroupId(),
			TestPropsValues.getUserId(), _getDSEnvelope(),
			new long[] {fileEntryId2});

		dsRequest = _dsRequestManager.fetchDSRequest(
			TestPropsValues.getCompanyId(), fileEntryId2);

		Assert.assertEquals(0, dsRequest.getSiteGroupId());

		DSRecipient dsRecipient = _getDSRecipient();

		dsRecipient.setName(RandomTestUtil.randomString(281));

		DSEnvelope dsEnvelope2 = _getDSEnvelope();

		dsEnvelope2.setDSRecipients(ListUtil.fromArray(dsRecipient));

		long fileEntryId3 = RandomTestUtil.randomInt();

		AssertUtils.assertFailure(
			ObjectEntryValuesException.ExceedsTextMaxLength.class,
			"Object entry value exceeds the maximum length of 280 characters " +
				"for object field \"name\"",
			() -> _addDSRequest(dsEnvelope2, fileEntryId3));

		Assert.assertEquals(
			Collections.emptyList(),
			_getValuesList(
				"(fileEntryId eq " + fileEntryId3 + ")",
				_getObjectDefinition("L_DS_REQUEST_DOCUMENT")));

		Assert.assertEquals(
			Collections.emptyList(),
			_getValuesList(
				"(providerRequestId eq '" + dsEnvelope2.getDSEnvelopeId() +
					"')",
				_getObjectDefinition("L_DS_REQUEST")));
	}

	@Test
	public void testAddDSRequestWhenEmbeddedViewIsDisabled() throws Exception {
		try (CompanyConfigurationTemporarySwapper
				companyConfigurationTemporarySwapper =
					new CompanyConfigurationTemporarySwapper(
						TestPropsValues.getCompanyId(),
						DigitalSignatureConfiguration.class.getName(),
						HashMapDictionaryBuilder.<String, Object>put(
							"enabled", true
						).put(
							"enableEmbeddedView", false
						).put(
							"siteSettingsStrategy", "always-inherit"
						).build())) {

			AssertUtils.assertFailure(
				PortalException.class,
				"Digital signatures are not enabled for group " +
					TestPropsValues.getGroupId(),
				() -> _dsRequestManager.addDSRequest(
					TestPropsValues.getCompanyId(),
					TestPropsValues.getGroupId(), TestPropsValues.getUserId(),
					_getDSEnvelope(), new long[] {RandomTestUtil.randomInt()}));
		}
	}

	@Test
	public void testAddDSRequestWhenExpirationWarningIsNotBeforeExpiration()
		throws Exception {

		DSEnvelope dsEnvelope = _getDSEnvelope();

		dsEnvelope.setExpireAfter(5);
		dsEnvelope.setExpireWarn(5);

		AssertUtils.assertFailure(
			PortalException.class,
			"Days to warn signers must be fewer than days until expiration",
			() -> _dsRequestManager.addDSRequest(
				TestPropsValues.getCompanyId(), TestPropsValues.getGroupId(),
				TestPropsValues.getUserId(), dsEnvelope,
				new long[] {RandomTestUtil.randomInt()}));
	}

	@Test
	public void testAddDSRequestWhenFileEntryHasActiveRequest()
		throws Exception {

		long fileEntryId = RandomTestUtil.randomInt();

		_addDSRequest(_getDSEnvelope(), fileEntryId);

		AssertUtils.assertFailure(
			PortalException.class,
			StringBundler.concat(
				"File entry ", fileEntryId,
				" already has a signature request with status \"",
				DSRequestConstants.STATUS_SENT, "\""),
			() -> _dsRequestManager.addDSRequest(
				TestPropsValues.getCompanyId(), TestPropsValues.getGroupId(),
				TestPropsValues.getUserId(), _getDSEnvelope(),
				new long[] {fileEntryId}));
	}

	@Test
	public void testAddDSRequestWhenFileEntryIdsIsEmpty() throws Exception {
		AssertUtils.assertFailure(
			PortalException.class,
			"A signature request must have at least one document",
			() -> _dsRequestManager.addDSRequest(
				TestPropsValues.getCompanyId(), TestPropsValues.getGroupId(),
				TestPropsValues.getUserId(), new DSEnvelope(), new long[0]));
	}

	@Test
	public void testFetchDSRequest() throws Exception {
		long fileEntryId1 = RandomTestUtil.randomInt();

		_addDSRequestObjectEntries(
			RandomTestUtil.randomString() + "@liferay.com", fileEntryId1,
			DSRequestRecipientConstants.STATUS_SENT,
			DSRequestConstants.STATUS_SENT, TestPropsValues.getUserId());

		DSRequest dsRequest = _dsRequestManager.fetchDSRequest(
			TestPropsValues.getCompanyId(), fileEntryId1);

		Assert.assertEquals(
			DSRequestConstants.STATUS_SENT, dsRequest.getStatus());
		Assert.assertFalse(dsRequest.isTerminal());

		List<DSRequestRecipient> dsRequestRecipients =
			dsRequest.getDSRequestRecipients();

		Assert.assertEquals(
			dsRequestRecipients.toString(), 1, dsRequestRecipients.size());

		DSRequestRecipient dsRequestRecipient1 = dsRequestRecipients.get(0);

		Assert.assertEquals(
			DSRequestRecipientConstants.STATUS_SENT,
			dsRequestRecipient1.getStatus());
		Assert.assertEquals(
			TestPropsValues.getUserId(), dsRequestRecipient1.getUserId());

		long fileEntryId2 = RandomTestUtil.randomInt();

		_addDSRequestObjectEntries(
			RandomTestUtil.randomString() + "@liferay.com", fileEntryId2,
			DSRequestRecipientConstants.STATUS_COMPLETED,
			DSRequestConstants.STATUS_COMPLETED, TestPropsValues.getUserId());

		dsRequest = _dsRequestManager.fetchDSRequest(
			TestPropsValues.getCompanyId(), fileEntryId2);

		Assert.assertEquals(
			DSRequestConstants.STATUS_COMPLETED, dsRequest.getStatus());
		Assert.assertTrue(dsRequest.isTerminal());

		DSEnvelope dsEnvelope = _getDSEnvelope();

		List<DSRecipient> dsRecipients = dsEnvelope.getDSRecipients();

		DSRecipient dsRecipient1 = dsRecipients.get(0);

		dsRecipient1.setRoutingOrder(2);

		DSRecipient dsRecipient2 = dsRecipients.get(1);

		dsRecipient2.setRoutingOrder(1);

		long fileEntryId3 = RandomTestUtil.randomInt();

		_addDSRequest(dsEnvelope, fileEntryId3);

		dsRequest = _dsRequestManager.fetchDSRequest(
			TestPropsValues.getCompanyId(), fileEntryId3);

		dsRequestRecipients = dsRequest.getDSRequestRecipients();

		DSRequestRecipient dsRequestRecipient2 = dsRequestRecipients.get(0);

		Assert.assertEquals(
			dsRecipient2.getEmailAddress(),
			dsRequestRecipient2.getEmailAddress());
		Assert.assertEquals(1, dsRequestRecipient2.getSigningOrder());

		DSRequestRecipient dsRequestRecipient3 = dsRequestRecipients.get(1);

		Assert.assertEquals(
			dsRecipient1.getEmailAddress(),
			dsRequestRecipient3.getEmailAddress());
		Assert.assertEquals(2, dsRequestRecipient3.getSigningOrder());

		DSRequest fetchedDSRequest = _dsRequestManager.fetchDSRequest(
			dsRequest.getDSRequestId());

		Assert.assertEquals(
			dsRequest.getDSRequestId(), fetchedDSRequest.getDSRequestId());
		Assert.assertEquals(
			dsRequest.getStatus(), fetchedDSRequest.getStatus());
	}

	@Test
	public void testFetchDSRequestWhenExpirationDateIsPast() throws Exception {
		DSEnvelope dsEnvelope = _getDSEnvelope();

		dsEnvelope.setExpireLocalDateTime(
			LocalDateTime.now(
			).minusDays(
				1
			));

		DSRequest dsRequest = _addDSRequest(
			dsEnvelope, RandomTestUtil.randomInt());

		dsRequest = _dsRequestManager.fetchDSRequest(
			dsRequest.getDSRequestId());

		Assert.assertEquals(
			DSRequestConstants.STATUS_EXPIRED, dsRequest.getStatus());
		Assert.assertTrue(dsRequest.isRequestable());
		Assert.assertTrue(dsRequest.isTerminal());
	}

	@Test
	public void testGetDSRequests() throws Exception {
		long fileEntryId1 = RandomTestUtil.randomInt();
		long fileEntryId2 = RandomTestUtil.randomInt();

		_addDSRequest(_getDSEnvelope(), fileEntryId1, fileEntryId2);

		Map<Long, DSRequest> dsRequests = _dsRequestManager.getDSRequests(
			TestPropsValues.getCompanyId(),
			ListUtil.fromArray(fileEntryId1, fileEntryId2));

		Assert.assertEquals(dsRequests.toString(), 2, dsRequests.size());

		DSRequest dsRequest1 = dsRequests.get(fileEntryId1);
		DSRequest dsRequest2 = dsRequests.get(fileEntryId2);

		Assert.assertEquals(
			dsRequest1.getProviderRequestId(),
			dsRequest2.getProviderRequestId());

		dsRequests = _dsRequestManager.getDSRequests(
			TestPropsValues.getCompanyId(),
			Collections.singletonList(RandomTestUtil.randomLong()));

		Assert.assertTrue(dsRequests.isEmpty());
	}

	@Test
	public void testGetFileEntryDSRequests() throws Exception {
		String emailAddress = RandomTestUtil.randomString() + "@liferay.com";
		long fileEntryId = RandomTestUtil.randomInt();

		_addDSRequestObjectEntries(
			emailAddress, fileEntryId, DSRequestRecipientConstants.STATUS_SENT,
			DSRequestConstants.STATUS_VOIDED, TestPropsValues.getUserId());
		_addDSRequestObjectEntries(
			emailAddress, fileEntryId, DSRequestRecipientConstants.STATUS_SENT,
			DSRequestConstants.STATUS_SENT, TestPropsValues.getUserId());

		List<DSRequest> dsRequests = _dsRequestManager.getFileEntryDSRequests(
			TestPropsValues.getCompanyId(), fileEntryId);

		Assert.assertEquals(dsRequests.toString(), 2, dsRequests.size());

		DSRequest dsRequest1 = dsRequests.get(0);

		Assert.assertEquals(
			DSRequestConstants.STATUS_SENT, dsRequest1.getStatus());

		DSRequest dsRequest2 = dsRequests.get(1);

		Assert.assertEquals(
			DSRequestConstants.STATUS_VOIDED, dsRequest2.getStatus());
	}

	@Test
	public void testGetRecipientDSRequests() throws Exception {
		_user = UserTestUtil.addUser();

		long fileEntryId1 = RandomTestUtil.randomInt();

		_addDSRequestObjectEntries(
			_user.getEmailAddress(), fileEntryId1,
			DSRequestRecipientConstants.STATUS_SENT,
			DSRequestConstants.STATUS_VOIDED, _user.getUserId());

		long fileEntryId2 = RandomTestUtil.randomInt();

		_addDSRequestObjectEntries(
			_user.getEmailAddress(), fileEntryId2,
			DSRequestRecipientConstants.STATUS_SENT,
			DSRequestConstants.STATUS_SENT, _user.getUserId());

		DSRequest dsRequest1 = _dsRequestManager.fetchDSRequest(
			TestPropsValues.getCompanyId(), fileEntryId1);
		DSRequest dsRequest2 = _dsRequestManager.fetchDSRequest(
			TestPropsValues.getCompanyId(), fileEntryId2);

		Assert.assertEquals(
			SetUtil.fromArray(
				new Long[] {
					dsRequest1.getDSRequestId(), dsRequest2.getDSRequestId()
				}),
			new HashSet<>(
				TransformUtil.transform(
					_dsRequestManager.getRecipientDSRequests(
						TestPropsValues.getCompanyId(), _user.getUserId(), null,
						QueryUtil.ALL_POS, QueryUtil.ALL_POS),
					DSRequest::getDSRequestId)));

		List<DSRequest> dsRequests = _dsRequestManager.getRecipientDSRequests(
			TestPropsValues.getCompanyId(), _user.getUserId(), null, 0, 1);

		Assert.assertEquals(dsRequests.toString(), 1, dsRequests.size());

		Assert.assertEquals(
			2,
			_dsRequestManager.getRecipientDSRequestsCount(
				TestPropsValues.getCompanyId(), _user.getUserId(), null));
		Assert.assertEquals(
			1,
			_dsRequestManager.getRecipientDSRequestsCount(
				TestPropsValues.getCompanyId(), _user.getUserId(),
				dsRequest1.getEmailSubject()));
		Assert.assertEquals(
			0,
			_dsRequestManager.getRecipientDSRequestsCount(
				TestPropsValues.getCompanyId(), _user.getUserId(),
				RandomTestUtil.randomString()));
	}

	@Test
	public void testHasPermission() throws Exception {
		_user = UserTestUtil.addUser();

		_addDSRequestObjectEntries(
			_user.getEmailAddress(), RandomTestUtil.randomInt(),
			DSRequestRecipientConstants.STATUS_SENT,
			DSRequestConstants.STATUS_SENT, _user.getUserId());

		List<DSRequest> dsRequests = _dsRequestManager.getRecipientDSRequests(
			TestPropsValues.getCompanyId(), _user.getUserId(), null,
			QueryUtil.ALL_POS, QueryUtil.ALL_POS);

		DSRequest dsRequest = dsRequests.get(0);

		PermissionChecker permissionChecker =
			PermissionCheckerFactoryUtil.create(_user);

		Assert.assertTrue(
			_dsRequestManager.hasPermission(
				permissionChecker, dsRequest, ActionKeys.UPDATE));
		Assert.assertTrue(
			_dsRequestManager.hasPermission(
				permissionChecker, dsRequest, ActionKeys.VIEW));

		_otherUser = UserTestUtil.addUser();

		permissionChecker = PermissionCheckerFactoryUtil.create(_otherUser);

		Assert.assertFalse(
			_dsRequestManager.hasPermission(
				permissionChecker, dsRequest, ActionKeys.UPDATE));
		Assert.assertFalse(
			_dsRequestManager.hasPermission(
				permissionChecker, dsRequest, ActionKeys.VIEW));
	}

	@Test
	public void testSendDSRequestNotifications() throws Exception {
		long fileEntryId1 = RandomTestUtil.randomInt();

		_addDSRequestObjectEntries(
			RandomTestUtil.randomString() + "@liferay.com", fileEntryId1,
			DSRequestRecipientConstants.STATUS_SENT,
			DSRequestConstants.STATUS_VOIDED, TestPropsValues.getUserId());

		int count = ReflectionTestUtil.invoke(
			_dsRequestManager, "_sendDSRequestNotifications",
			new Class<?>[] {long.class, long.class, DSRequest.class},
			TestPropsValues.getCompanyId(), TestPropsValues.getGroupId(),
			_dsRequestManager.fetchDSRequest(
				TestPropsValues.getCompanyId(), fileEntryId1));

		Assert.assertEquals(0, count);

		long fileEntryId2 = RandomTestUtil.randomInt();

		_addDSRequestObjectEntries(
			RandomTestUtil.randomString() + "@liferay.com", fileEntryId2,
			DSRequestRecipientConstants.STATUS_SENT,
			DSRequestConstants.STATUS_SENT, TestPropsValues.getUserId());

		count = ReflectionTestUtil.invoke(
			_dsRequestManager, "_sendDSRequestNotifications",
			new Class<?>[] {long.class, long.class, DSRequest.class},
			TestPropsValues.getCompanyId(), TestPropsValues.getGroupId(),
			_dsRequestManager.fetchDSRequest(
				TestPropsValues.getCompanyId(), fileEntryId2));

		Assert.assertEquals(1, count);
	}

	@Test
	public void testSendSignatureReminders() throws Exception {
		try (CompanyConfigurationTemporarySwapper
				companyConfigurationTemporarySwapper =
					new CompanyConfigurationTemporarySwapper(
						TestPropsValues.getCompanyId(),
						DigitalSignatureConfiguration.class.getName(),
						HashMapDictionaryBuilder.<String, Object>put(
							"enabled", true
						).put(
							"enableEmbeddedView", true
						).put(
							"signatureReminderEnabled", true
						).put(
							"siteSettingsStrategy", "always-inherit"
						).build())) {

			int count = _dsRequestManager.sendSignatureReminders(
				TestPropsValues.getCompanyId());

			_addDSRequestObjectEntries(
				RandomTestUtil.randomString() + "@liferay.com",
				RandomTestUtil.randomInt(),
				DSRequestRecipientConstants.STATUS_SENT,
				DSRequestConstants.STATUS_SENT, TestPropsValues.getUserId());
			_addDSRequestObjectEntries(
				RandomTestUtil.randomString() + "@liferay.com",
				RandomTestUtil.randomInt(),
				DSRequestRecipientConstants.STATUS_SENT,
				DSRequestConstants.STATUS_VOIDED, TestPropsValues.getUserId());

			Assert.assertEquals(
				count + 1,
				_dsRequestManager.sendSignatureReminders(
					TestPropsValues.getCompanyId()));
		}
	}

	@Test
	public void testUpdateDSRequest() throws Exception {
		LocalDateTime localDateTime = LocalDateTime.ofEpochSecond(
			System.currentTimeMillis() / 1000, 0, ZoneOffset.UTC);

		_testUpdateDSRequest(
			DSRequestConstants.STATUS_COMPLETED, localDateTime.plusDays(1),
			"completed", localDateTime);
		_testUpdateDSRequest(
			DSRequestConstants.STATUS_EXPIRED, localDateTime, "voided",
			localDateTime);
	}

	private DSRequest _addDSRequest(DSEnvelope dsEnvelope, long... fileEntryIds)
		throws Exception {

		return ReflectionTestUtil.invoke(
			_dsRequestManager, "_addDSRequest",
			new Class<?>[] {
				long.class, long.class, long.class, DSEnvelope.class,
				long[].class
			},
			TestPropsValues.getCompanyId(), TestPropsValues.getGroupId(),
			TestPropsValues.getUserId(), dsEnvelope, fileEntryIds);
	}

	private void _addDSRequestObjectEntries(
			String emailAddress, long fileEntryId, String recipientStatus,
			String requestStatus, long userId)
		throws Exception {

		ObjectDefinition dsRequestObjectDefinition = _getObjectDefinition(
			"L_DS_REQUEST");
		String languageId = LocaleUtil.toLanguageId(
			LocaleUtil.getSiteDefault());
		ServiceContext serviceContext =
			ServiceContextTestUtil.getServiceContext(
				TestPropsValues.getGroupId(), userId);

		ObjectEntry dsRequestObjectEntry =
			_objectEntryLocalService.addObjectEntry(
				0, userId, dsRequestObjectDefinition.getObjectDefinitionId(), 0,
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

		ObjectDefinition dsRequestDocumentObjectDefinition =
			_getObjectDefinition("L_DS_REQUEST_DOCUMENT");

		_objectEntryLocalService.addObjectEntry(
			0, userId,
			dsRequestDocumentObjectDefinition.getObjectDefinitionId(), 0,
			languageId,
			HashMapBuilder.<String, Serializable>put(
				"fileEntryId", fileEntryId
			).put(
				"r_dsRequestToDSRequestDocuments_l_dsRequestId",
				dsRequestObjectEntry.getObjectEntryId()
			).build(),
			serviceContext);

		ObjectDefinition dsRequestRecipientObjectDefinition =
			_getObjectDefinition("L_DS_REQUEST_RECIPIENT");

		_objectEntryLocalService.addObjectEntry(
			0, userId,
			dsRequestRecipientObjectDefinition.getObjectDefinitionId(), 0,
			languageId,
			HashMapBuilder.<String, Serializable>put(
				"emailAddress", emailAddress
			).put(
				"name", RandomTestUtil.randomString()
			).put(
				"providerRecipientId", RandomTestUtil.randomString()
			).put(
				"r_dsRequestToDSRequestRecipients_l_dsRequestId",
				dsRequestObjectEntry.getObjectEntryId()
			).put(
				"r_userToDSRequestRecipients_userId", userId
			).put(
				"requestRecipientStatus", recipientStatus
			).build(),
			serviceContext);
	}

	private DSEnvelope _getDSEnvelope() {
		DSEnvelope dsEnvelope = new DSEnvelope();

		dsEnvelope.setDSEnvelopeId(RandomTestUtil.randomString());
		dsEnvelope.setDSRecipients(
			ListUtil.fromArray(_getDSRecipient(), _getDSRecipient()));
		dsEnvelope.setEmailSubject(RandomTestUtil.randomString());
		dsEnvelope.setStatus("sent");

		return dsEnvelope;
	}

	private DSRecipient _getDSRecipient() {
		DSRecipient dsRecipient = new DSRecipient();

		dsRecipient.setDSRecipientId(RandomTestUtil.randomString());
		dsRecipient.setEmailAddress(
			RandomTestUtil.randomString() + "@liferay.com");
		dsRecipient.setName(RandomTestUtil.randomString());

		return dsRecipient;
	}

	private ObjectDefinition _getObjectDefinition(String externalReferenceCode)
		throws Exception {

		return _objectDefinitionLocalService.
			getObjectDefinitionByExternalReferenceCode(
				externalReferenceCode, TestPropsValues.getCompanyId());
	}

	private List<Map<String, Serializable>> _getRecipientValuesList(
			Map<String, Serializable> requestValues)
		throws Exception {

		ObjectDefinition dsRequestObjectDefinition = _getObjectDefinition(
			"L_DS_REQUEST");

		return _getValuesList(
			StringBundler.concat(
				"(r_dsRequestToDSRequestRecipients_l_dsRequestId eq '",
				MapUtil.getLong(
					requestValues,
					dsRequestObjectDefinition.getPKObjectFieldName()),
				"')"),
			_getObjectDefinition("L_DS_REQUEST_RECIPIENT"));
	}

	private Map<String, Serializable> _getRequestValues(DSEnvelope dsEnvelope)
		throws Exception {

		List<Map<String, Serializable>> requestValuesList = _getValuesList(
			"(providerRequestId eq '" + dsEnvelope.getDSEnvelopeId() + "')",
			_getObjectDefinition("L_DS_REQUEST"));

		Assert.assertEquals(
			requestValuesList.toString(), 1, requestValuesList.size());

		return requestValuesList.get(0);
	}

	private List<Map<String, Serializable>> _getValuesList(
			String filterString, ObjectDefinition objectDefinition)
		throws Exception {

		return _objectEntryLocalService.getValuesList(
			0, TestPropsValues.getCompanyId(), TestPropsValues.getUserId(),
			objectDefinition.getObjectDefinitionId(),
			_filterFactory.create(filterString, objectDefinition), null,
			QueryUtil.ALL_POS, QueryUtil.ALL_POS, null);
	}

	private void _testUpdateDSRequest(
			String expectedRequestStatus, LocalDateTime expireLocalDateTime,
			String status, LocalDateTime statusChangedLocalDateTime)
		throws Exception {

		DSEnvelope dsEnvelope = _getDSEnvelope();

		_addDSRequest(dsEnvelope, RandomTestUtil.randomInt());

		for (DSRecipient dsRecipient : dsEnvelope.getDSRecipients()) {
			dsRecipient.setStatus("completed");
			dsRecipient.setStatusLocalDateTime(statusChangedLocalDateTime);
		}

		dsEnvelope.setExpireLocalDateTime(expireLocalDateTime);
		dsEnvelope.setStatus(status);
		dsEnvelope.setStatusChangedLocalDateTime(statusChangedLocalDateTime);

		DSEnvelopeManager dsEnvelopeManager =
			(DSEnvelopeManager)ReflectionTestUtil.getAndSetFieldValue(
				_dsRequestManager, "_dsEnvelopeManager",
				ProxyUtil.newProxyInstance(
					DSEnvelopeManager.class.getClassLoader(),
					new Class<?>[] {DSEnvelopeManager.class},
					(proxy, method, arguments) -> dsEnvelope));

		User user = UserTestUtil.addUser();

		try (ContextUserReplace contextUserReplace = new ContextUserReplace(
				user, PermissionCheckerFactoryUtil.create(user))) {

			_dsRequestManager.updateDSRequest(
				TestPropsValues.getCompanyId(), TestPropsValues.getGroupId(),
				dsEnvelope.getDSEnvelopeId());
		}
		finally {
			ReflectionTestUtil.setFieldValue(
				_dsRequestManager, "_dsEnvelopeManager", dsEnvelopeManager);
		}

		Map<String, Serializable> requestValues = _getRequestValues(dsEnvelope);

		Assert.assertEquals(
			Date.from(expireLocalDateTime.toInstant(ZoneOffset.UTC)),
			requestValues.get("requestExpirationDate"));
		Assert.assertEquals(
			expectedRequestStatus, requestValues.get("requestStatus"));
		Assert.assertEquals(
			Date.from(statusChangedLocalDateTime.toInstant(ZoneOffset.UTC)),
			requestValues.get("requestStatusDate"));

		List<Map<String, Serializable>> recipientValuesList =
			_getRecipientValuesList(requestValues);

		Assert.assertEquals(
			recipientValuesList.toString(), 2, recipientValuesList.size());

		for (Map<String, Serializable> recipientValues : recipientValuesList) {
			Assert.assertEquals(
				DSRequestRecipientConstants.STATUS_COMPLETED,
				recipientValues.get("requestRecipientStatus"));
			Assert.assertEquals(
				Date.from(statusChangedLocalDateTime.toInstant(ZoneOffset.UTC)),
				recipientValues.get("requestRecipientStatusDate"));
		}
	}

	private CompanyConfigurationTemporarySwapper
		_companyConfigurationTemporarySwapper;

	@Inject
	private DSRequestManager _dsRequestManager;

	@Inject(
		filter = "filter.factory.key=" + ObjectDefinitionConstants.STORAGE_TYPE_DEFAULT
	)
	private FilterFactory<Predicate> _filterFactory;

	@Inject
	private ObjectDefinitionLocalService _objectDefinitionLocalService;

	@Inject
	private ObjectEntryLocalService _objectEntryLocalService;

	@DeleteAfterTestRun
	private Organization _organization;

	@DeleteAfterTestRun
	private User _otherUser;

	@DeleteAfterTestRun
	private User _user;

}