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
import com.liferay.object.constants.ObjectDefinitionConstants;
import com.liferay.object.exception.ObjectEntryValuesException;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.model.ObjectField;
import com.liferay.object.model.ObjectRelationship;
import com.liferay.object.rest.filter.factory.FilterFactory;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.petra.function.transform.TransformUtil;
import com.liferay.petra.sql.dsl.expression.Predicate;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.configuration.test.util.CompanyConfigurationTemporarySwapper;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactoryUtil;
import com.liferay.portal.kernel.test.AssertUtils;
import com.liferay.portal.kernel.test.ReflectionTestUtil;
import com.liferay.portal.kernel.test.context.ContextUserReplace;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.test.util.UserTestUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.HashMapDictionaryBuilder;
import com.liferay.portal.kernel.util.HttpComponentsUtil;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.MapUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.ProxyUtil;
import com.liferay.portal.kernel.util.SetUtil;
import com.liferay.portal.kernel.util.URLCodec;
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

		DSRequest dsRequest = _addDSRequest(dsEnvelope1, fileEntryId1);

		Assert.assertEquals(
			Collections.singletonList(fileEntryId1),
			dsRequest.getFileEntryIds());
		Assert.assertEquals(
			dsEnvelope1.getDSEnvelopeId(), dsRequest.getProviderRequestId());
		Assert.assertEquals(
			TestPropsValues.getGroupId(), dsRequest.getSiteGroupId());

		Map<String, Serializable> requestValues = _getRequestValues(
			dsEnvelope1);

		Assert.assertEquals(
			DSRequestConstants.STATUS_SENT, requestValues.get("requestStatus"));

		List<Map<String, Serializable>> documentValuesList = _getValuesList(
			"(fileEntryId eq " + fileEntryId1 + ")",
			_getObjectDefinition("L_DS_REQUEST_DOCUMENT"));

		Assert.assertEquals(
			documentValuesList.toString(), 1, documentValuesList.size());

		Set<String> actualEmailAddresses = new HashSet<>(
			TransformUtil.transform(
				_getRecipientValuesList(requestValues),
				recipientValues -> MapUtil.getString(
					recipientValues, "emailAddress")));
		Set<String> expectedEmailAddresses = new HashSet<>(
			TransformUtil.transform(
				dsEnvelope1.getDSRecipients(), DSRecipient::getEmailAddress));

		Assert.assertEquals(expectedEmailAddresses, actualEmailAddresses);

		DSRecipient dsRecipient = _getDSRecipient();

		dsRecipient.setName(RandomTestUtil.randomString(281));

		DSEnvelope dsEnvelope2 = _getDSEnvelope();

		dsEnvelope2.setDSRecipients(ListUtil.fromArray(dsRecipient));

		long fileEntryId2 = RandomTestUtil.randomInt();

		AssertUtils.assertFailure(
			ObjectEntryValuesException.ExceedsTextMaxLength.class,
			"Object entry value exceeds the maximum length of 280 characters " +
				"for object field \"name\"",
			() -> _addDSRequest(dsEnvelope2, fileEntryId2));

		Assert.assertEquals(
			Collections.emptyList(),
			_getValuesList(
				"(fileEntryId eq " + fileEntryId2 + ")",
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
	public void testContainsPermission() throws Exception {
		_user = UserTestUtil.addUser();

		DSRequest dsRequest = _addDSRequest(
			_user.getEmailAddress(), DSRequestConstants.STATUS_SENT);

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
		DSRequest dsRequest1 = _addDSRequest(
			_getDSEnvelope(), RandomTestUtil.randomInt());

		DSRequest dsRequest2 = _dsRequestManager.fetchDSRequest(
			dsRequest1.getDSRequestId());

		Assert.assertEquals(
			dsRequest1.getDSRequestId(), dsRequest2.getDSRequestId());
		Assert.assertEquals(
			DSRequestConstants.STATUS_SENT, dsRequest2.getStatus());
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
		_user = UserTestUtil.addUser();

		DSRequest dsRequest1 = _addDSRequest(
			_user.getEmailAddress(), DSRequestConstants.STATUS_VOIDED);
		DSRequest dsRequest2 = _addDSRequest(
			_user.getEmailAddress(), DSRequestConstants.STATUS_SENT);

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
	public void testGetRecipientStatusesByFileEntryId() throws Exception {
		long companyId = TestPropsValues.getCompanyId();
		long userId = TestPropsValues.getUserId();

		ObjectDefinition requestObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST", companyId);
		ObjectDefinition recipientObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST_RECIPIENT", companyId);

		long fileEntryId = RandomTestUtil.randomInt();

		String languageId = LocaleUtil.toLanguageId(
			LocaleUtil.getSiteDefault());

		ObjectEntry requestObjectEntry =
			_objectEntryLocalService.addObjectEntry(
				0, userId, requestObjectDefinition.getObjectDefinitionId(), 0,
				languageId,
				HashMapBuilder.<String, Serializable>put(
					"fileEntryId", fileEntryId
				).put(
					"providerKey", "docusign"
				).put(
					"providerRequestId", "test-" + fileEntryId
				).put(
					"requestStatus", "sent"
				).build(),
				ServiceContextTestUtil.getServiceContext(
					_group.getGroupId(), userId));

		ObjectRelationship objectRelationship =
			_objectRelationshipLocalService.fetchObjectRelationship(
				requestObjectDefinition.getObjectDefinitionId(),
				"dsRequestToDSRequestRecipients");

		ObjectField objectField = _objectFieldLocalService.getObjectField(
			objectRelationship.getObjectFieldId2());

		_objectEntryLocalService.addObjectEntry(
			0, userId, recipientObjectDefinition.getObjectDefinitionId(), 0,
			languageId,
			HashMapBuilder.<String, Serializable>put(
				objectField.getName(), requestObjectEntry.getObjectEntryId()
			).put(
				"r_userToDSRequestRecipients_userId", userId
			).put(
				"requestRecipientStatus", "sent"
			).build(),
			ServiceContextTestUtil.getServiceContext(
				_group.getGroupId(), userId));

		Map<Long, Map<Long, String>> recipientStatusesByFileEntryId =
			_dsRequestManager.getRecipientStatusesByFileEntryId(
				companyId, Collections.singletonList(fileEntryId));

		Assert.assertEquals(
			"sent",
			recipientStatusesByFileEntryId.get(
				fileEntryId
			).get(
				userId
			));
	}

	@Test
	public void testGetRequestStatuses() throws Exception {
		long companyId = TestPropsValues.getCompanyId();

		ObjectDefinition objectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST", companyId);

		long fileEntryId = RandomTestUtil.randomInt();

		_objectEntryLocalService.addObjectEntry(
			0, TestPropsValues.getUserId(),
			objectDefinition.getObjectDefinitionId(), 0,
			LocaleUtil.toLanguageId(LocaleUtil.getSiteDefault()),
			HashMapBuilder.<String, Serializable>put(
				"fileEntryId", fileEntryId
			).put(
				"providerKey", "docusign"
			).put(
				"providerRequestId", "test-" + fileEntryId
			).put(
				"requestStatus", DSRequestConstants.STATUS_COMPLETED
			).build(),
			ServiceContextTestUtil.getServiceContext(
				TestPropsValues.getGroupId(), TestPropsValues.getUserId()));

		Map<Long, String> requestStatuses =
			_dsRequestManager.getRequestStatusesByFileEntryId(
				companyId, Collections.singletonList(fileEntryId));

		Assert.assertEquals(
			DSRequestConstants.STATUS_COMPLETED,
			requestStatuses.get(fileEntryId));
	}

	@Test
	public void testGetRequestStatusesReturnsEmptyForMissingRequest()
		throws Exception {

		Map<Long, String> requestStatuses =
			_dsRequestManager.getRequestStatusesByFileEntryId(
				TestPropsValues.getCompanyId(),
				Collections.singletonList(RandomTestUtil.randomLong()));

		Assert.assertTrue(requestStatuses.isEmpty());
	}

	@Test
	public void testGetSignatureRequiredFileEntryIds() throws Exception {
		long companyId = TestPropsValues.getCompanyId();
		long userId = TestPropsValues.getUserId();

		ObjectDefinition requestObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST", companyId);
		ObjectDefinition recipientObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST_RECIPIENT", companyId);

		long fileEntryId = RandomTestUtil.randomInt();

		String languageId = LocaleUtil.toLanguageId(
			LocaleUtil.getSiteDefault());

		ObjectEntry requestObjectEntry =
			_objectEntryLocalService.addObjectEntry(
				0, userId, requestObjectDefinition.getObjectDefinitionId(), 0,
				languageId,
				HashMapBuilder.<String, Serializable>put(
					"fileEntryId", fileEntryId
				).put(
					"providerKey", "docusign"
				).put(
					"providerRequestId", "test-" + fileEntryId
				).put(
					"requestStatus", "sent"
				).build(),
				ServiceContextTestUtil.getServiceContext(
					TestPropsValues.getGroupId(), userId));

		ObjectRelationship objectRelationship =
			_objectRelationshipLocalService.fetchObjectRelationship(
				requestObjectDefinition.getObjectDefinitionId(),
				"dsRequestToDSRequestRecipients");

		ObjectField objectField = _objectFieldLocalService.getObjectField(
			objectRelationship.getObjectFieldId2());

		_objectEntryLocalService.addObjectEntry(
			0, userId, recipientObjectDefinition.getObjectDefinitionId(), 0,
			languageId,
			HashMapBuilder.<String, Serializable>put(
				objectField.getName(), requestObjectEntry.getObjectEntryId()
			).put(
				"r_userToDSRequestRecipients_userId", userId
			).put(
				"requestRecipientStatus", "sent"
			).build(),
			ServiceContextTestUtil.getServiceContext(
				TestPropsValues.getGroupId(), userId));

		Set<Long> signatureRequiredFileEntryIds =
			_dsRequestManager.getSignatureRequiredFileEntryIds(
				companyId, userId, Collections.singletonList(fileEntryId));

		Assert.assertTrue(
			signatureRequiredFileEntryIds.toString(),
			signatureRequiredFileEntryIds.contains(fileEntryId));

		Assert.assertTrue(
			_dsRequestManager.getSignatureRequiredCount(companyId, userId) > 0);
	}

	@Test
	public void testSendDSRequestNotifications() throws Exception {
		_user = UserTestUtil.addUser();

		int count = ReflectionTestUtil.invoke(
			_dsRequestManager, "_sendDSRequestNotifications",
			new Class<?>[] {long.class, long.class, DSRequest.class},
			TestPropsValues.getCompanyId(), TestPropsValues.getGroupId(),
			_addDSRequest(
				_user.getEmailAddress(), DSRequestConstants.STATUS_VOIDED));

		Assert.assertEquals(0, count);

		count = ReflectionTestUtil.invoke(
			_dsRequestManager, "_sendDSRequestNotifications",
			new Class<?>[] {long.class, long.class, DSRequest.class},
			TestPropsValues.getCompanyId(), TestPropsValues.getGroupId(),
			_addDSRequest(
				_user.getEmailAddress(), DSRequestConstants.STATUS_SENT));

		Assert.assertEquals(1, count);
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

	private DSRequest _addDSRequest(DSEnvelope dsEnvelope, long fileEntryId)
		throws Exception {

		return ReflectionTestUtil.invoke(
			_dsRequestManager, "_addDSRequest",
			new Class<?>[] {
				long.class, long.class, long.class, DSEnvelope.class,
				long[].class
			},
			TestPropsValues.getCompanyId(), TestPropsValues.getGroupId(),
			TestPropsValues.getUserId(), dsEnvelope, new long[] {fileEntryId});
	}

	private DSRequest _addDSRequest(String emailAddress, String status)
		throws Exception {

		DSEnvelope dsEnvelope = _getDSEnvelope();

		List<DSRecipient> dsRecipients = dsEnvelope.getDSRecipients();

		DSRecipient dsRecipient = dsRecipients.get(0);

		dsRecipient.setEmailAddress(emailAddress);

		dsEnvelope.setStatus(status);

		return _addDSRequest(dsEnvelope, RandomTestUtil.randomInt());
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

		long dsRequestId = MapUtil.getLong(
			requestValues, dsRequestObjectDefinition.getPKObjectFieldName());

		return _getValuesList(
			"(r_dsRequestToDSRequestRecipients_l_dsRequestId eq '" +
				dsRequestId + "')",
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
	private User _otherUser;

	@DeleteAfterTestRun
	private User _user;

}